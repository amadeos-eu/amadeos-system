package eu.amadeos.system.notify;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.system.base.notify.NotificationClient;
import eu.amadeos.system.base.notify.dbus.NotificationCenter;
import eu.amadeos.system.base.notify.dbus.Notifications;
import eu.amadeos.system.base.x11.LockSelection;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgDirectories;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The notification daemon of the session ({@code amadeos-session notifications}, started by
 * {@code /etc/amadeos/autostart.d/45-amadeos-notifications}): owns {@code org.freedesktop.Notifications} on the
 * session bus, so that every program that uses libnotify ({@code notify-send}, Firefox, Thunderbird, LibreOffice,
 * the amade applications through amade-commons) shows its notifications as pop-ups, keeps them in a history and
 * tells the Front Panel's bell how many are new. {@code amadeos-notifications} shows the history of the running
 * daemon, or starts one.
 *
 * <p>Threads: dbus-java's workers call the {@link NotificationCore}; one worker reloads the settings, saves the
 * history and asks X about the lock and full screen windows; the event dispatch thread shows the windows.
 *
 * <p>Exit codes: 0 when the session bus went away, 2 usage, 3 another notification service owns the name,
 * 4 no session bus, 5 no X display (the session script then stops restarting it).
 */
public final class NotificationDaemon {
    private static final Logger LOGGER = Logger.getLogger(NotificationDaemon.class.getName());
    public static final int ALREADY_RUNNING = 3;
    public static final int NO_BUS = 4;
    public static final int NO_DISPLAY = 5;
    static final String TEXTS = "eu.amadeos.system.notify.notifications";
    private static final long TICK_SECONDS = 2;

    private final NotificationSettingsStore store;
    private final Path historyFile;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "notifications-worker");
        t.setDaemon(true);
        return t;
    });
    private final Object xLock = new Object();
    private volatile NotificationSettings settings;
    private FileTime settingsTime;
    private X11EwmhHelper x;
    private DBusConnection bus;
    private NotificationCore core;
    private PopupStack popups;
    private HistoryWindow window;
    private Messages texts;
    private volatile boolean saveQueued;

    private NotificationDaemon(NotificationSettingsStore store, Path historyFile) {
        this.store = store;
        this.historyFile = historyFile;
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "--show";
        switch (mode) {
            case "--daemon" -> System.exit(run(false));
            case "--show" -> {
                boolean shown;
                try (NotificationClient client = new NotificationClient()) {
                    shown = client.showHistory();
                }
                if (!shown) System.exit(run(true));
                System.exit(0);
            }
            default -> {
                System.err.println("Usage: amadeos-notifications [--show|--daemon]");
                System.exit(2);
            }
        }
    }

    /** The history in {@code ~/.cache/amadeos/notifications.json}. */
    static Path historyFile() {
        return XdgDirectories.amadeosLogDir().getParent().resolve("notifications.json");
    }

    static int run(boolean showHistory) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.severe("Notification daemon not started: no X display");
            return NO_DISPLAY;
        }
        NotificationDaemon daemon = new NotificationDaemon(NotificationSettingsStore.user(), historyFile());
        return daemon.start(showHistory);
    }

    private int start(boolean showHistory) {
        Locale locale = LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                .language().locale();
        texts = Messages.load(NotificationDaemon.class, TEXTS, locale);
        ThemeStore theme = ThemeStore.system();
        try {
            SwingUtilities.invokeAndWait(() -> {
                AmadeUI.install(theme.current());
                AmadeUI.follow(theme);
            });
            x = X11EwmhHelper.open(null);
        } catch (Exception | UnsatisfiedLinkError e) {
            LOGGER.log(Level.SEVERE, "Notification daemon not started: no X display", e);
            return NO_DISPLAY;
        }
        settings = store.load();
        settingsTime = store.lastModified();
        Clock clock = Clock.systemDefaultZone();
        NotificationHistory history = new NotificationHistory();
        history.load(historyFile, clock.instant());

        try {
            bus = DBusConnectionBuilder.forSessionBus().withShared(false).build();
        } catch (DBusException e) {
            LOGGER.log(Level.SEVERE, "Notification daemon not started: no session bus", e);
            return NO_BUS;
        }
        core = new NotificationCore(() -> settings, this::saveSettings, new Screen(), new Signals(), clock, history,
                this::queueSave);
        NotificationServer server = new NotificationServer(core, NotificationDaemon::desktopEntry, clock,
                () -> SwingUtilities.invokeLater(this::showHistory), version());
        try {
            SwingUtilities.invokeAndWait(() -> popups = new PopupStack(texts, new PopupCard.Actions() {
                @Override
                public void invoke(long id, String key) {
                    work(() -> core.invoke(id, key));
                }

                @Override
                public void dismiss(long id) {
                    work(() -> core.dismiss(id));
                }
            }, id -> work(() -> core.expired(id)), () -> settings, clock::instant, this::area));
            bus.exportObject(Notifications.PATH, server.specObject());
            bus.exportObject(NotificationCenter.PATH, server.centerObject());
            bus.addSigHandler(DBus.NameOwnerChanged.class, signal -> {
                if (signal.newOwner.isEmpty() && signal.name.startsWith(":")) {
                    work(() -> core.senderGone(signal.name));
                }
            });
            bus.requestBusName(Notifications.BUS_NAME);
        } catch (DBusException e) {
            LOGGER.info("Notification daemon not started: " + e.getMessage());
            closeBus();
            return ALREADY_RUNNING;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Notification daemon not started", e);
            closeBus();
            return NO_BUS;
        }
        LOGGER.info(() -> "Notification daemon running: " + settings.corner() + ", " + settings.seconds() + " s");
        worker.scheduleWithFixedDelay(this::tick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
        if (showHistory) SwingUtilities.invokeLater(this::showHistory);
        try {
            while (bus.isConnected()) {
                Thread.sleep(2_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        saveHistory();
        return 0;
    }

    private void tick() {
        try {
            if (store.changedSince(settingsTime)) {
                settingsTime = store.lastModified();
                settings = store.load();
                SwingUtilities.invokeLater(() -> {
                    if (popups != null) popups.layout();
                    if (window != null && window.isVisible()) window.refresh();
                });
            }
            core.tick();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Notification tick failed", e);
        }
    }

    private void saveSettings(NotificationSettings s) {
        settings = s;
        store.save(s);
        settingsTime = store.lastModified();
        SwingUtilities.invokeLater(() -> {
            if (window != null && window.isVisible()) window.refresh();
        });
    }

    /** The history is written on the worker, at most once per change burst. */
    private void queueSave() {
        if (saveQueued) return;
        saveQueued = true;
        worker.schedule(() -> {
            saveQueued = false;
            saveHistory();
        }, 500, TimeUnit.MILLISECONDS);
    }

    private void saveHistory() {
        java.util.List<NotificationHistory.Entry> entries = core.entries();
        NotificationHistory copy = new NotificationHistory();
        for (int i = entries.size() - 1; i >= 0; i--) {
            NotificationHistory.Entry e = entries.get(i);
            copy.add(e.notification(), e.notification().time());
            if (e.seen()) copy.update(e.notification().id(), x -> x.seen(true));
        }
        copy.save(historyFile);
    }

    private void work(Runnable task) {
        worker.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Notification task failed", e);
            }
        });
    }

    private void showHistory() {
        if (window == null) {
            window = new HistoryWindow(texts, new HistoryWindow.Actions() {
                @Override
                public java.util.List<NotificationHistory.Entry> entries() {
                    return core.entries();
                }

                @Override
                public boolean manualDnd() {
                    return settings.manualDnd(Instant.now());
                }

                @Override
                public void setManualDnd(boolean on) {
                    work(() -> core.setDoNotDisturb(on ? NotificationSettings.DND_ON : NotificationSettings.DND_OFF));
                }

                @Override
                public void open(long id) {
                    work(() -> core.open(id));
                }

                @Override
                public void remove(long id) {
                    work(() -> core.remove(id));
                }

                @Override
                public void clearAll() {
                    work(core::clearAll);
                }

                @Override
                public void shown() {
                    work(core::historyShown);
                }
            }, Instant::now, java.time.ZoneId.systemDefault());
        }
        // The history shows the pop-ups' notifications too; they would only cover it.
        popups.hideAll();
        window.showWindow();
    }

    /** The screen without the docks: the pop-ups at the bottom stay above the Front Panel. */
    private Rectangle area() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getBounds();
        int[] edges;
        synchronized (xLock) {
            edges = x.reservedEdges();
        }
        return new Rectangle(screen.x + edges[0], screen.y + edges[2], screen.width - edges[0] - edges[1],
                screen.height - edges[2] - edges[3]);
    }

    private static Optional<XdgDesktopEntry> desktopEntry(String id) {
        XdgApplicationManager apps = XdgApplicationManager.getInstance();
        Optional<XdgDesktopEntry> entry = apps.findById(id + ".desktop");
        if (entry.isEmpty()) {
            apps.reload();
            entry = apps.findById(id + ".desktop");
        }
        return entry;
    }

    private static String version() {
        String v = NotificationDaemon.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    private void closeBus() {
        if (bus != null) {
            try {
                bus.close();
            } catch (java.io.IOException ignored) {
                // going away anyway
            }
        }
    }

    private final class Screen implements NotificationCore.Screen {
        @Override
        public void popup(Notification n, long timeoutMs) {
            SwingUtilities.invokeLater(() -> popups.show(n, timeoutMs));
        }

        @Override
        public void unpop(long id) {
            SwingUtilities.invokeLater(() -> popups.hide(id));
        }

        @Override
        public void historyChanged() {
            SwingUtilities.invokeLater(() -> {
                if (window != null && window.isVisible()) window.refresh();
            });
        }

        @Override
        public boolean fullscreen() {
            synchronized (xLock) {
                try {
                    return x.isActiveWindowFullscreen();
                } catch (RuntimeException e) {
                    return false;
                }
            }
        }

        @Override
        public boolean locked() {
            synchronized (xLock) {
                try {
                    return LockSelection.isLocked(eu.amadeos.system.base.x11.LibX11.INSTANCE, x.display());
                } catch (RuntimeException e) {
                    return false;
                }
            }
        }

        @Override
        public void beep() {
            Toolkit.getDefaultToolkit().beep();
        }

        @Override
        public void launch(String desktopEntry) {
            desktopEntry(desktopEntry).ifPresent(entry -> {
                try {
                    entry.launch();
                } catch (java.io.IOException e) {
                    LOGGER.log(Level.WARNING, "Cannot start " + desktopEntry, e);
                }
            });
        }
    }

    private final class Signals implements NotificationCore.Signals {
        @Override
        public void closed(long id, int reason) {
            send(() -> new Notifications.NotificationClosed(Notifications.PATH, new UInt32(id), new UInt32(reason)));
        }

        @Override
        public void actionInvoked(long id, String key) {
            send(() -> new Notifications.ActionInvoked(Notifications.PATH, new UInt32(id), key));
        }

        @Override
        public void stateChanged(int unseen, boolean doNotDisturb) {
            send(() -> new NotificationCenter.StateChanged(NotificationCenter.PATH, unseen, doNotDisturb));
        }

        private void send(SignalFactory factory) {
            try {
                bus.sendMessage(factory.make());
            } catch (DBusException | RuntimeException e) {
                LOGGER.log(Level.WARNING, "Cannot send a notification signal", e);
            }
        }
    }

    @FunctionalInterface
    private interface SignalFactory {
        DBusSignal make() throws DBusException;
    }
}
