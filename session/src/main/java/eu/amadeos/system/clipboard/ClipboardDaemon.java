package eu.amadeos.system.clipboard;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.system.base.x11.LibX11;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The clipboard manager of the session ({@code amadeos-session clipboard}, started by
 * {@code /etc/amadeos/autostart.d/40-amadeos-clipboard}): keeps a history of what was copied, in memory only,
 * keeps a copy on the clipboard after the program that made it has closed, and shows the history on
 * Ctrl+Alt+V. {@code amadeos-clipboard} (the Front Panel's Tools drawer) shows the history of the running
 * manager, or starts one.
 *
 * <p>Threads: the X thread of {@link SelectionWatcher} hears the X server, one worker runs the
 * {@link ClipboardManager} (reading the clipboard can take a moment), the event dispatch thread shows the
 * window.
 *
 * <p>Exit codes: 0 on request or after a command was sent, 2 usage, 3 another clipboard manager of amadeOS is
 * running, 5 no X display or no XFIXES extension (the session script then stops restarting it).
 */
public final class ClipboardDaemon {
    private static final Logger LOGGER = Logger.getLogger(ClipboardDaemon.class.getName());
    public static final int ALREADY_RUNNING = 3;
    public static final int NO_DISPLAY = 5;
    static final String TEXTS = "eu.amadeos.system.clipboard.clipboard";
    private static final long SETTINGS_POLL_SECONDS = 2;
    private static final long PRIMARY_RETRY_MS = 100;
    /** About half a minute of a held mouse button, then the selection is left alone. */
    private static final int PRIMARY_RETRIES = 300;

    private final ClipboardManager manager;
    private final ClipboardSettingsStore store;
    private final X11SelectionProbe probe;
    private final Messages texts;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "clipboard-worker");
        t.setDaemon(true);
        return t;
    });
    private FileTime settingsTime;
    private HistoryWindow window;

    private ClipboardDaemon(ClipboardManager manager, ClipboardSettingsStore store, X11SelectionProbe probe,
                            Messages texts) {
        this.manager = manager;
        this.store = store;
        this.probe = probe;
        this.texts = texts;
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "--show";
        switch (mode) {
            case "--daemon" -> System.exit(run(false));
            case "--show" -> {
                if (!ClipboardCommand.SHOW.send()) System.exit(run(true));
            }
            case "--clear" -> ClipboardCommand.CLEAR.send();
            default -> {
                System.err.println("Usage: amadeos-clipboard [--show|--clear|--daemon]");
                System.exit(2);
            }
        }
    }

    /** Runs the manager until the X connection ends; returns the exit code. */
    static int run(boolean showHistory) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.severe("Clipboard manager not started: no X display");
            return NO_DISPLAY;
        }
        Locale locale = LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                .language().locale();
        Messages texts = Messages.load(ClipboardDaemon.class, TEXTS, locale);
        ThemeStore theme = ThemeStore.system();
        try {
            // AWT first: its X error handler then covers this program's own X connections too, and only logs.
            SwingUtilities.invokeAndWait(() -> {
                AmadeUI.install(theme.current());
                AmadeUI.follow(theme);
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot set up the user interface", e);
            return NO_DISPLAY;
        }

        LibX11 x11;
        X11SelectionProbe probe;
        try {
            x11 = LibX11.INSTANCE;
            probe = X11SelectionProbe.open(x11);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            LOGGER.severe("Clipboard manager not started: " + e.getMessage());
            return NO_DISPLAY;
        }
        ClipboardSettingsStore store = ClipboardSettingsStore.user();
        long imageBudget = Math.min(256L << 20, Runtime.getRuntime().maxMemory() / 3);
        ClipboardSettings settings = store.load();
        ClipboardManager manager = new ClipboardManager(new ClipboardHistory(settings.size(), imageBudget),
                new AwtClipboards(Instant::now), probe, settings, Instant::now);
        ClipboardDaemon daemon = new ClipboardDaemon(manager, store, probe, texts);
        daemon.settingsTime = store.lastModified();

        SelectionWatcher watcher;
        try {
            watcher = SelectionWatcher.open(x11, daemon.listener());
        } catch (SelectionWatcher.AlreadyRunningException e) {
            LOGGER.info("Clipboard manager not started: " + e.getMessage());
            if (showHistory) ClipboardCommand.SHOW.send();
            return ALREADY_RUNNING;
        } catch (IllegalStateException e) {
            LOGGER.severe("Clipboard manager not started: " + e.getMessage());
            return NO_DISPLAY;
        }
        daemon.worker.scheduleWithFixedDelay(daemon::reloadSettings, SETTINGS_POLL_SECONDS, SETTINGS_POLL_SECONDS,
                TimeUnit.SECONDS);
        manager.history().addListener(() -> SwingUtilities.invokeLater(daemon::refreshWindow));
        LOGGER.info(() -> "Clipboard manager running: " + settings);
        if (showHistory) SwingUtilities.invokeLater(daemon::showHistory);
        watcher.run();
        return 0;
    }

    private SelectionWatcher.Listener listener() {
        return new SelectionWatcher.Listener() {
            @Override
            public void ownerChanged(Selection selection, long owner, boolean gone) {
                handle(manager.arrived(selection, owner, gone), 0);
            }

            @Override
            public void hotkey() {
                SwingUtilities.invokeLater(ClipboardDaemon.this::showHistory);
            }

            @Override
            public void command(ClipboardCommand command) {
                switch (command) {
                    case SHOW -> SwingUtilities.invokeLater(ClipboardDaemon.this::showHistory);
                    case CLEAR -> work(manager::clear);
                }
            }

            @Override
            public void saveRequested(SelectionWatcher.Request request) {
                work(() -> probe.answerSave(request, manager.save()));
            }
        };
    }

    private void handle(ClipboardManager.OwnerChange change, int attempt) {
        worker.schedule(() -> {
            try {
                if (!manager.handle(change) && attempt < PRIMARY_RETRIES) handle(change, attempt + 1);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Clipboard change not handled", e);
            }
        }, attempt == 0 ? 0 : PRIMARY_RETRY_MS, TimeUnit.MILLISECONDS);
    }

    private void work(Runnable task) {
        worker.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Clipboard task failed", e);
            }
        });
    }

    private void reloadSettings() {
        if (!store.changedSince(settingsTime)) return;
        settingsTime = store.lastModified();
        ClipboardSettings settings = store.load();
        manager.apply(settings);
        LOGGER.info(() -> "Clipboard settings changed: " + settings);
        SwingUtilities.invokeLater(this::refreshWindow);
    }

    private void showHistory() {
        if (window == null) window = new HistoryWindow(texts, actions(), ZoneId.systemDefault());
        window.showAtPointer();
    }

    private void refreshWindow() {
        if (window != null && window.isVisible()) window.refresh();
    }

    private HistoryWindow.Actions actions() {
        return new HistoryWindow.Actions() {
            @Override
            public List<ClipEntry> entries() {
                return manager.history().entries();
            }

            @Override
            public boolean historyOn() {
                return manager.settings().history();
            }

            @Override
            public boolean paused() {
                return manager.paused();
            }

            @Override
            public void choose(ClipEntry entry) {
                work(() -> manager.choose(entry));
            }

            @Override
            public void remove(ClipEntry entry) {
                work(() -> manager.remove(entry));
            }

            @Override
            public void clear() {
                work(manager::clear);
            }

            @Override
            public void setPaused(boolean paused) {
                manager.setPaused(paused);
            }
        };
    }
}
