package eu.amadeos.system.power;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.notify.DesktopNotification;
import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.base.power.Inhibitor;
import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerControl;
import eu.amadeos.system.base.power.PowerSettings;
import eu.amadeos.system.base.power.PowerSettingsStore;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The power half of the session's screen daemon ({@code amadeos-session screen}): turns the screen off and
 * suspends after the idle times of the Settings' Power page, handles the lid (logind leaves it to the desktop
 * while this runs), warns about and acts on a nearly empty battery, and locks the screen before the computer
 * sleeps, however it was sent to sleep. The screen saver's poll loop calls {@link #poll(long, boolean)}.
 */
public final class PowerManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(PowerManager.class.getName());
    /** How long the computer waits for the lock to appear before it sleeps. */
    private static final long LOCK_WAIT_MILLIS = 3000;

    /** Texts of the battery warning, {@code power.properties} and its translations. */
    public static final String TEXTS = "eu.amadeos.system.power.power";

    private final PowerSettingsStore store;
    private final PowerControl control;
    private final ScreenPower screen;
    private final Supplier<Optional<BatteryStatus>> battery;
    private final Runnable lock;
    private final BooleanSupplier locked;
    private final Consumer<Integer> warning;
    private final PowerPolicy policy = new PowerPolicy();
    private PowerSettings settings;
    private FileTime settingsTime;
    private int screenTimeout = -1;
    private Inhibitor lidLock;
    private Inhibitor sleepDelay;
    private AutoCloseable sleepSignal;

    public PowerManager(PowerSettingsStore store, PowerControl control, ScreenPower screen,
                        Supplier<Optional<BatteryStatus>> battery, Runnable lock, BooleanSupplier locked,
                        Consumer<Integer> warning) {
        this.store = store;
        this.control = control;
        this.screen = screen;
        this.battery = battery;
        this.lock = lock;
        this.locked = locked;
        this.warning = warning;
    }

    /** Takes over the lid from logind and starts listening for sleep. Without logind the idle settings still work. */
    public void start() {
        reloadIfChanged();
        try {
            lidLock = Inhibitor.take("handle-lid-switch", "block", "The amadeOS desktop handles the lid (Settings > Power)");
            sleepDelay = Inhibitor.take("sleep", "delay", "The amadeOS desktop locks the screen first");
            sleepSignal = control.onSleep(this::sleeping);
        } catch (IOException | PowerControl.PowerException e) {
            LOGGER.warning(() -> "Power management without logind: " + e.getMessage());
        }
        LOGGER.info(() -> "Power management running: " + settings);
    }

    // --8<-- [start:poll]
    /** One round, from the screen saver's loop. */
    public void poll(long idleMillis, boolean screenLocked) {
        reloadIfChanged();
        Optional<BatteryStatus> status = battery.get();
        boolean onBattery = status.map(BatteryStatus::onBattery).orElse(false);
        PowerSettings.Profile profile = settings.profile(onBattery);
        int timeout = profile.screenOffMinutes() * 60;
        if (timeout != screenTimeout) {
            screenTimeout = timeout;
            screen.setTimeout(timeout);
        }
        PowerPolicy.State state = new PowerPolicy.State(idleMillis, onBattery, status.map(BatteryStatus::percent).orElse(-1),
                control.lidClosed(), control.docked(), screenLocked);
        for (PowerPolicy.Step step : policy.next(settings, state)) {
            switch (step) {
                case PowerPolicy.Step.Warn warn -> warning.accept(warn.percent());
                case PowerPolicy.Step.Act act -> act(act.action(), act.reason());
            }
        }
    }
    // --8<-- [end:poll]

    void act(PowerAction action, PowerPolicy.Reason reason) {
        LOGGER.info(() -> "Power action " + action + " (" + reason + ")");
        try {
            switch (action) {
                case SUSPEND -> control.suspend();
                case HIBERNATE -> {
                    if (control.canHibernate()) control.hibernate();
                    else if (reason == PowerPolicy.Reason.LOW_BATTERY) control.powerOff();
                    else control.suspend();
                }
                case SHUTDOWN -> control.powerOff();
                case LOCK -> lock.run();
                case SCREEN_OFF -> screen.turnOff();
                case ASK, NOTHING -> {
                }
            }
        } catch (PowerControl.PowerException e) {
            LOGGER.log(Level.WARNING, "Power action " + action + " failed", e);
        }
    }

    /** logind's PrepareForSleep: lock first, then let the computer sleep; after waking, be ready again. */
    private void sleeping(boolean start) {
        if (start) {
            if (settings.lockOnWake() && !locked.getAsBoolean()) {
                lock.run();
                long until = System.currentTimeMillis() + LOCK_WAIT_MILLIS;
                while (!locked.getAsBoolean() && System.currentTimeMillis() < until) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (sleepDelay != null) sleepDelay.close();
            sleepDelay = null;
        } else {
            policy.wokeUp();
            screenTimeout = -1; // some X servers forget DPMS settings on resume
            try {
                sleepDelay = Inhibitor.take("sleep", "delay", "The amadeOS desktop locks the screen first");
            } catch (IOException e) {
                LOGGER.warning(() -> "Cannot take the sleep delay again: " + e.getMessage());
            }
        }
    }

    private void reloadIfChanged() {
        if (settings != null && !store.changedSince(settingsTime)) return;
        settingsTime = store.lastModified();
        settings = store.load();
        screenTimeout = -1;
    }

    // --8<-- [start:warning]
    /**
     * The battery warning as a critical notification, which stays until closed and shows through Do not disturb;
     * clicking it opens Settings > Power. Without a notification daemon (0 from {@code send}) the fallback shows.
     *
     * @param send     sends a notification and returns its id, e.g. {@code NotificationClient::send}
     * @param settings opens Settings > Power
     */
    public static Consumer<Integer> notificationWarning(Messages texts, ToLongFunction<DesktopNotification> send,
                                                        Runnable settings, Consumer<Integer> fallback) {
        return percent -> {
            DesktopNotification n = DesktopNotification.of(texts.get("battery.app"), "battery-caution",
                            texts.get("battery.low.title"), texts.format("battery.low.message", percent))
                    .withUrgency(DesktopNotification.Urgency.CRITICAL)
                    .withCategory("device.error")
                    .withDesktopEntry("amadeos-settings")
                    .withAction("default", texts.get("battery.settings"))
                    .onAction(key -> settings.run());
            if (send.applyAsLong(n) == 0) fallback.accept(percent);
        };
    }
    // --8<-- [end:warning]

    /** The battery warning as a small message window, where no notification daemon runs. */
    public static Consumer<Integer> dialogWarning(Messages texts) {
        return percent -> SwingUtilities.invokeLater(() -> {
            JOptionPane pane = new JOptionPane(texts.format("battery.low.message", percent), JOptionPane.WARNING_MESSAGE);
            var dialog = pane.createDialog(null, texts.get("battery.low.title"));
            dialog.setModal(false);
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
        });
    }

    @Override
    public void close() {
        if (lidLock != null) lidLock.close();
        if (sleepDelay != null) sleepDelay.close();
        if (sleepSignal != null) {
            try {
                sleepSignal.close();
            } catch (Exception ignored) {
                // The bus is going away with the process.
            }
        }
    }
}
