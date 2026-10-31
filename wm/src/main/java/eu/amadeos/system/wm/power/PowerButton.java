package eu.amadeos.system.wm.power;

import eu.amadeos.system.base.power.Inhibitor;
import eu.amadeos.system.base.power.LogindPowerControl;
import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerControl;
import eu.amadeos.system.base.power.PowerSettingsStore;
import eu.amadeos.system.base.ui.SessionDialogHelper;

import java.io.IOException;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * The power button (key XF86PowerOff), which the window manager grabs: while it runs, logind leaves the
 * button to the desktop, and pressing it does what Settings > Power says: show the Exit dialog (the default),
 * suspend, hibernate or shut down. While the screen is locked only the actions without a dialog happen.
 */
public final class PowerButton {
    private static final Logger LOGGER = Logger.getLogger(PowerButton.class.getName());
    /** XF86XK_PowerOff from X11/XF86keysym.h. */
    public static final long XF86_POWER_OFF = 0x1008FF2AL;
    /** Some firmware sends the button twice; presses closer together than this count once. */
    private static final long DEBOUNCE_MILLIS = 1500;

    /** What pressing the button leads to; the window manager passes the real ones, tests stand-ins. */
    public interface Actions {
        void ask();

        void suspend();

        void hibernate();

        void shutDown();
    }

    private final PowerSettingsStore store;
    private final Actions actions;
    private final LongSupplier clock;
    private long lastPress = Long.MIN_VALUE / 2;
    private Inhibitor inhibitor;

    public PowerButton(PowerSettingsStore store, Actions actions, LongSupplier clock) {
        this.store = store;
        this.actions = actions;
        this.clock = clock;
    }

    /** The button as the desktop uses it: the Exit dialog, logind for sleeping, the session's end for shutting down. */
    public static PowerButton desktop() {
        PowerControl power = new LogindPowerControl();
        return new PowerButton(PowerSettingsStore.user(), new Actions() {
            @Override
            public void ask() {
                SessionDialogHelper.showSessionPowerDialog(null, power);
            }

            @Override
            public void suspend() {
                inBackground(power::suspend);
            }

            @Override
            public void hibernate() {
                inBackground(() -> {
                    if (power.canHibernate()) power.hibernate();
                    else power.suspend();
                });
            }

            @Override
            public void shutDown() {
                // As the Exit dialog's Shut Down: the session ends, then the display manager switches off.
                SessionDialogHelper.requestSessionAction("poweroff");
            }
        }, System::currentTimeMillis);
    }

    /** Asks logind to leave the button to the desktop; call once the key is grabbed. */
    public void takeOver() {
        try {
            inhibitor = Inhibitor.take("handle-power-key", "block", "The amadeOS desktop handles the power button (Settings > Power)");
        } catch (IOException e) {
            LOGGER.warning(() -> "logind keeps the power button: " + e.getMessage());
        }
    }

    // --8<-- [start:pressed]
    /** The button was pressed; {@code locked} while the screen lock is up. */
    public void pressed(boolean locked) {
        long now = clock.getAsLong();
        if (now - lastPress < DEBOUNCE_MILLIS) return;
        lastPress = now;
        PowerAction action = store.load().powerKey();
        LOGGER.info(() -> "Power button: " + action + (locked ? " (screen locked)" : ""));
        switch (action) {
            case ASK -> {
                if (!locked) actions.ask();
            }
            case SUSPEND -> actions.suspend();
            case HIBERNATE -> actions.hibernate();
            case SHUTDOWN -> actions.shutDown();
            default -> {
            }
        }
    }
    // --8<-- [end:pressed]

    private interface Call {
        void run() throws PowerControl.PowerException;
    }

    private static void inBackground(Call call) {
        Thread thread = new Thread(() -> {
            try {
                call.run();
            } catch (PowerControl.PowerException e) {
                LOGGER.warning(() -> "Power button action failed: " + e.getMessage());
            }
        }, "amadeOS-power-button");
        thread.setDaemon(true);
        thread.start();
    }
}
