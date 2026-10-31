package eu.amadeos.system.screen;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.notify.NotificationClient;
import eu.amadeos.system.base.power.LogindPowerControl;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.power.PowerSettingsStore;
import eu.amadeos.system.base.power.PowerSupplies;
import eu.amadeos.system.base.process.LockLauncher;
import eu.amadeos.system.power.PowerManager;
import eu.amadeos.system.power.ScreenPower;
import eu.amadeos.system.screen.modules.SaverModules;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.attribute.FileTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The screen saver of the session ({@code amadeos.jar screen}), the counterpart of CDE's dtscreen
 * started by dtsession. Polls the idle time of the X server, shows the saver and starts the screen lock
 * according to the Settings' "Screen" settings, which it re-reads when they change. The same loop drives the
 * {@link PowerManager} (Settings' "Power"): screen off, suspend when idle, the lid and the battery.
 *
 * <p>Exit codes: 0 on request, 5 if there is no X display or no MIT-SCREEN-SAVER extension (the session
 * script then stops restarting it).
 */
public final class ScreenSaverDaemon {
    private static final Logger LOGGER = Logger.getLogger(ScreenSaverDaemon.class.getName());
    public static final int NO_DISPLAY = 5;
    private static final long IDLE_POLL_MS = 1000;
    private static final long ACTIVE_POLL_MS = 200;

    private final IdleMonitor monitor;
    private final ScreenSettingsStore store;
    private final Runnable lock;
    private final PowerManager power;
    private final IdlePolicy policy = new IdlePolicy();
    private final Random random = new Random();
    private ScreenSettings settings;
    private FileTime settingsTime;
    private SaverWindow window;

    ScreenSaverDaemon(IdleMonitor monitor, ScreenSettingsStore store, Runnable lock) {
        this(monitor, store, lock, null);
    }

    ScreenSaverDaemon(IdleMonitor monitor, ScreenSettingsStore store, Runnable lock, PowerManager power) {
        this.monitor = monitor;
        this.store = store;
        this.lock = lock;
        this.power = power;
    }

    public static void main(String[] args) {
        IdleMonitor monitor;
        try {
            monitor = IdleMonitor.x11();
        } catch (IdleMonitor.UnavailableException e) {
            LOGGER.severe("Screen saver not started: " + e.getMessage());
            System.exit(NO_DISPLAY);
            return;
        }
        // The saver window is plain Swing; give it the desktop look like every other component.
        AmadeUI.install(eu.amadeos.system.base.theme.ThemeManager.currentTheme());
        Locale locale = LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                .language().locale();
        Messages texts = Messages.load(PowerManager.class, PowerManager.TEXTS, locale);
        PowerSupplies supplies = new PowerSupplies();
        NotificationClient notifications = new NotificationClient();
        Runnable powerSettings = () -> {
            try {
                ProcessLauncher.start(java.util.List.of("amadeos-settings", "power"));
            } catch (java.io.IOException e) {
                LOGGER.warning(() -> "Cannot open Settings > Power: " + e.getMessage());
            }
        };
        PowerManager power = new PowerManager(PowerSettingsStore.user(), new LogindPowerControl(), ScreenPower.xset(),
                supplies::battery, LockLauncher::lockScreen, monitor::locked,
                PowerManager.notificationWarning(texts, notifications::send, powerSettings, PowerManager.dialogWarning(texts)));
        power.start();
        ScreenSaverDaemon daemon = new ScreenSaverDaemon(monitor, ScreenSettingsStore.user(), LockLauncher::lockScreen, power);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            power.close();
            monitor.close();
        }));
        daemon.run();
    }

    void run() {
        reloadIfChanged();
        LOGGER.info(() -> "Screen saver running: " + settings);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                poll();
                Thread.sleep(policy.saverActive() ? ACTIVE_POLL_MS : IDLE_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Screen saver poll failed", e);
            }
        }
    }

    /** One round: read settings and idle time, carry out what the policy decides. Package visible for tests. */
    void poll() {
        reloadIfChanged();
        long idle = monitor.idleMillis();
        boolean locked = monitor.locked();
        if (power != null) {
            try {
                power.poll(idle, locked);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Power management poll failed", e);
            }
        }
        IdlePolicy.Action action = policy.next(settings, idle, locked);
        switch (action) {
            case START_SAVER -> onEdt(this::showSaver);
            case STOP_SAVER -> onEdt(this::hideSaver);
            case LOCK -> {
                onEdt(this::hideSaver);
                LOGGER.info("Idle for " + settings.lockMinutes() + " min, locking the screen");
                lock.run();
            }
            case NONE -> {
            }
        }
    }

    private void reloadIfChanged() {
        if (settings != null && !store.changedSince(settingsTime)) return;
        settingsTime = store.lastModified();
        settings = store.load();
    }

    private void showSaver() {
        if (window != null) return;
        window = new SaverWindow(SaverModules.create(settings.module(), random));
        window.showSaver();
    }

    private void hideSaver() {
        if (window == null) return;
        window.close();
        window = null;
    }

    boolean saverVisible() {
        return window != null;
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            LOGGER.log(Level.WARNING, "Screen saver window failed", e.getCause());
        }
    }
}
