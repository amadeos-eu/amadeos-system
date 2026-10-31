package eu.amadeos.system.power;

import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides, once a second, what the power settings ask for: suspending after the idle time of the current power
 * source, the lid action when the lid closes, the warning and the action for a nearly empty battery. Pure logic
 * without X11 or D-Bus, so it can be tested.
 *
 * <ul>
 *   <li>Idle suspend happens once per idle period: after waking up the X server's idle time still counts the
 *       time asleep, so the next idle suspend waits for input first.</li>
 *   <li>The lid action happens when the lid goes from open to closed, not while it stays closed; with
 *       {@code lidIgnoreDocked} not while an external screen is connected.</li>
 *   <li>The battery warning and the low-battery action happen once each while discharging; plugging in
 *       re-arms them.</li>
 * </ul>
 */
public final class PowerPolicy {

    /** What the daemon should do. */
    public sealed interface Step {
        /** Carry out a power action (suspend, hibernate, lock, screen off, shut down). */
        record Act(PowerAction action, Reason reason) implements Step {
        }

        /** Warn that the battery is low; {@code percent} is the charge left. */
        record Warn(int percent) implements Step {
        }
    }

    public enum Reason {IDLE, LID, LOW_BATTERY}

    /**
     * What the daemon knows at one moment.
     *
     * @param idleMillis time since the last input
     * @param onBattery  true while the computer runs on its battery
     * @param percent    battery charge, or -1 without a battery
     * @param lidClosed  whether the lid is closed
     * @param docked     whether an external screen is connected or the computer is docked
     * @param locked     whether the screen is locked
     */
    public record State(long idleMillis, boolean onBattery, int percent, boolean lidClosed, boolean docked,
                        boolean locked) {
    }

    private long lastIdle;
    private boolean idleSuspended;
    private boolean lidWasClosed;
    private boolean warned;
    private boolean lowHandled;

    public List<Step> next(PowerSettings settings, State state) {
        List<Step> steps = new ArrayList<>();
        boolean input = state.idleMillis() < lastIdle;
        lastIdle = state.idleMillis();
        if (input) idleSuspended = false;

        // Lid: on the closing edge only
        boolean lidClosing = state.lidClosed() && !lidWasClosed;
        lidWasClosed = state.lidClosed();
        if (lidClosing && !(settings.lidIgnoreDocked() && state.docked())) {
            PowerAction lid = settings.profile(state.onBattery()).lid();
            if (lid != PowerAction.NOTHING) steps.add(new Step.Act(lid, Reason.LID));
        }

        // Battery
        if (!state.onBattery() || state.percent() < 0) {
            warned = false;
            lowHandled = false;
        } else {
            if (!lowHandled && state.percent() <= settings.lowPercent()) {
                lowHandled = true;
                warned = true;
                if (settings.lowAction() != PowerAction.NOTHING) {
                    steps.add(new Step.Act(settings.lowAction(), Reason.LOW_BATTERY));
                }
            } else if (!warned && settings.warnPercent() > 0 && state.percent() <= settings.warnPercent()) {
                warned = true;
                steps.add(new Step.Warn(state.percent()));
            }
        }

        // Idle suspend
        PowerSettings.Profile profile = settings.profile(state.onBattery());
        if (steps.isEmpty() && profile.suspendMinutes() != PowerSettings.NEVER && !idleSuspended
                && state.idleMillis() >= profile.suspendMillis()) {
            idleSuspended = true;
            steps.add(new Step.Act(PowerAction.SUSPEND, Reason.IDLE));
        }
        return steps;
    }

    /** After waking up: the X idle time includes the sleep, so no idle suspend until the next input. */
    public void wokeUp() {
        idleSuspended = true;
    }
}
