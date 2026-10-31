package eu.amadeos.system.panel.cde.status;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.PackageService.PackageException;
import eu.amadeos.system.packages.Progress;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;
import eu.amadeos.system.panel.cde.widget.AbstractDockWidget.MotifBevelBorder;

/**
 * Software updates in the status slot: shown only while updates are waiting, as a downward arrow into a tray
 * with their number, the number on red when security updates are among them. A click opens a menu with the
 * first updates and Install all / Install security updates only / Check now. An installation can be followed in
 * {@link UpdateProgressWindow}.
 * <p>
 * The cell reads what Debian's daily refresh (apt-daily.timer) fetched: every 30 minutes and whenever the
 * installed packages or the package lists change. Reading needs no privileges and no network; installing and
 * Check now go through the helper, which asks for the password where it has to.
 */
public final class UpdatesCell implements StatusCell {
    private static final Logger LOGGER = Logger.getLogger(UpdatesCell.class.getName());
    /** How often the pending updates are read again, besides the reads on changes (minutes). */
    private static final long POLL_MINUTES = 30;
    /** The first read waits until the session has settled (seconds). */
    private static final long FIRST_READ_SECONDS = 20;
    static final int MENU_ENTRIES = 8;
    static final Color SECURITY = new Color(0xC0, 0x28, 0x28);

    /** What the cell is doing besides showing the updates. */
    enum Busy { NONE, CHECKING, INSTALLING }

    /** Tells the user how an operation ended; a dialog, or a recorder in tests. */
    interface Notifier {
        void notify(String message, boolean warning);
    }

    /** Where an installation can be followed: {@link UpdateProgressWindow}; called on the event dispatch thread. */
    interface ProgressView {
        ProgressView NONE = new ProgressView() {
            @Override
            public void begin() {
            }

            @Override
            public void progress(Progress progress) {
            }

            @Override
            public void message(String line) {
            }

            @Override
            public boolean finish(String message, boolean warning) {
                return false;
            }

            @Override
            public boolean running() {
                return false;
            }

            @Override
            public void show() {
            }
        };

        /** Opens the view for a new installation. */
        void begin();

        void progress(Progress progress);

        /** One line of apt's and dpkg's messages. */
        void message(String line);

        /**
         * Ends the installation with its outcome (null when there is nothing to say).
         *
         * @return whether the view shows the outcome; otherwise the cell tells it in a dialog
         */
        boolean finish(String message, boolean warning);

        boolean running();

        /** Shows the view again after the user hid it. */
        void show();
    }

    private final PackageService service;
    private final Runnable showUpdates;
    private final Runnable openSettings;
    private final Messages texts;
    private final Notifier notifier;
    private final CellButton button = new CellButton();
    private final ScheduledExecutorService reader = Executors.newSingleThreadScheduledExecutor(daemon("updates-read"));
    private final ExecutorService actions = Executors.newSingleThreadExecutor(daemon("updates-action"));
    private DesktopTheme theme = ThemeManager.currentTheme();
    private List<Change> updates = List.of();
    private Busy busy = Busy.NONE;
    private int percent;
    private final Supplier<ProgressView> progressViews;
    private ProgressView progressView;
    private Runnable relayout = () -> {};
    private java.util.function.Consumer<List<Change>> readings = pending -> { };
    private AutoCloseable watch;
    JPopupMenu menu;

    /**
     * @param showUpdates  opens the update window, or null while there is none (the entry is left out)
     * @param openSettings opens the update settings, or null while there are none
     */
    public UpdatesCell(PackageService service, Runnable showUpdates, Runnable openSettings) {
        this(service, showUpdates, openSettings, Messages.load(UpdatesCell.class, NetworkCell.DESKTOP_TEXTS,
                LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale()),
                null, null);
    }

    /** @param progressViews makes the view of an installation; null for {@link UpdateProgressWindow} */
    UpdatesCell(PackageService service, Runnable showUpdates, Runnable openSettings, Messages texts,
                Notifier notifier, Supplier<ProgressView> progressViews) {
        this.service = service;
        this.showUpdates = showUpdates;
        this.openSettings = openSettings;
        this.texts = texts;
        this.progressViews = progressViews != null ? progressViews
                : () -> UpdateProgressWindow.create(this.texts, theme);
        this.notifier = notifier != null ? notifier : this::showDialog;
        button.addActionListener(e -> openMenu());
        show(List.of());
    }

    @Override
    public JComponent component() {
        return button;
    }

    @Override
    public boolean available() {
        return !updates.isEmpty() || busy != Busy.NONE;
    }

    @Override
    public void onAvailabilityChanged(Runnable relayout) {
        this.relayout = relayout;
    }

    @Override
    public void onThemeChanged(DesktopTheme theme) {
        this.theme = theme;
        button.repaint();
    }

    // --8<-- [start:start]
    @Override
    public void start() {
        watch = service.watch(this::readLater);
        reader.scheduleWithFixedDelay(this::readNow, FIRST_READ_SECONDS, POLL_MINUTES * 60, TimeUnit.SECONDS);
    }
    // --8<-- [end:start]

    @Override
    public void stop() {
        if (watch != null) {
            try {
                watch.close();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Closing the package watch failed", e);
            }
        }
        reader.shutdownNow();
        actions.shutdownNow();
    }

    List<Change> updates() {
        return updates;
    }

    Busy busy() {
        return busy;
    }

    /** Whether the cell is installing updates just now, for the busy light. */
    public boolean installing() {
        return busy == Busy.INSTALLING;
    }

    /** Shows the pending updates; called on the event dispatch thread. */
    void show(List<Change> pending) {
        boolean wasAvailable = available();
        updates = List.copyOf(pending);
        refreshLook();
        if (available() != wasAvailable) relayout.run();
        readings.accept(updates);
    }

    /** Hears the pending updates after each read, e.g. {@link DesktopNotices#updates} to announce new ones. */
    public void onUpdates(java.util.function.Consumer<List<Change>> listener) {
        this.readings = listener;
    }

    private void setBusy(Busy state) {
        boolean wasAvailable = available();
        busy = state;
        percent = 0;
        refreshLook();
        if (available() != wasAvailable) relayout.run();
    }

    private void refreshLook() {
        button.setToolTipText(describe());
        button.repaint();
    }

    long securityCount() {
        return updates.stream().filter(Change::security).count();
    }

    /** "12 updates, 3 security", or what the cell is busy with. */
    String describe() {
        return switch (busy) {
            case CHECKING -> texts.get("updates.busy.check");
            case INSTALLING -> texts.format("updates.busy.install", percent);
            case NONE -> summary();
        };
    }

    String summary() {
        if (updates.isEmpty()) return texts.get("updates.none");
        String count = updates.size() == 1 ? texts.get("updates.one") : texts.format("updates.many", updates.size());
        long security = securityCount();
        return security == 0 ? count : texts.format("updates.with.security", count, security);
    }

    private void readLater() {
        try {
            reader.execute(this::readNow);
        } catch (RejectedExecutionException stopped) {
            // stop() was called
        }
    }

    private void readNow() {
        try {
            List<Change> pending = service.upgrades();
            SwingUtilities.invokeLater(() -> show(pending));
        } catch (PackageException e) {
            LOGGER.log(Level.FINE, "Cannot read the pending updates", e);
        }
    }

    void openMenu() {
        if (menu != null && menu.isVisible()) {
            menu.setVisible(false);
            return;
        }
        menu = buildMenu();
        if (button.isShowing()) menu.show(button, 0, -menu.getPreferredSize().height - 2);
    }

    JPopupMenu buildMenu() {
        JPopupMenu m = new JPopupMenu();
        m.setBorder(new MotifBevelBorder(true, 2));

        JMenuItem head = new JMenuItem(describe());
        head.setEnabled(false);
        m.add(head);

        if (!updates.isEmpty()) {
            m.addSeparator();
            // Security updates first, as the update window lists them
            List<Change> sorted = updates.stream()
                    .sorted((a, b) -> Boolean.compare(b.security(), a.security())).toList();
            for (Change c : sorted.subList(0, Math.min(MENU_ENTRIES, sorted.size()))) {
                JMenuItem entry = new JMenuItem(texts.format("updates.entry", c.name(), c.installedVersion(),
                        c.newVersion()));
                if (c.security()) entry.setForeground(SECURITY);
                entry.setToolTipText(c.originLabel());
                entry.setEnabled(showUpdates != null);
                if (showUpdates != null) entry.addActionListener(e -> showUpdates.run());
                m.add(entry);
            }
            if (sorted.size() > MENU_ENTRIES) {
                JMenuItem more = new JMenuItem(texts.format("updates.more", sorted.size() - MENU_ENTRIES));
                more.setEnabled(false);
                m.add(more);
            }
        }

        m.addSeparator();
        if (showUpdates != null) {
            JMenuItem show = new JMenuItem(texts.get("updates.show"));
            show.addActionListener(e -> showUpdates.run());
            m.add(show);
        }
        boolean idle = busy == Busy.NONE;
        if (busy == Busy.INSTALLING && progressView != null && progressView.running()) {
            JMenuItem progress = new JMenuItem(texts.get("updates.progress.show"));
            progress.addActionListener(e -> progressView.show());
            m.add(progress);
        }
        JMenuItem all = new JMenuItem(texts.get("updates.install.all"));
        all.setEnabled(idle && !updates.isEmpty());
        all.addActionListener(e -> install(false));
        m.add(all);
        if (securityCount() > 0) {
            JMenuItem security = new JMenuItem(texts.get("updates.install.security"));
            security.setEnabled(idle);
            security.addActionListener(e -> install(true));
            m.add(security);
        }
        JMenuItem check = new JMenuItem(texts.get("updates.check"));
        check.setEnabled(idle);
        check.addActionListener(e -> check());
        m.add(check);
        if (openSettings != null) {
            m.addSeparator();
            JMenuItem settings = new JMenuItem(texts.get("updates.settings"));
            settings.addActionListener(e -> openSettings.run());
            m.add(settings);
        }

        style(m);
        return m;
    }

    private void style(MenuElement element) {
        if (element.getComponent() instanceof JComponent c) {
            c.setBackground(theme.base());
            if (!(c instanceof JPopupMenu) && !SECURITY.equals(c.getForeground())) c.setForeground(theme.textActive());
        }
        for (MenuElement child : element.getSubElements()) style(child);
    }

    /** Installs all pending updates, or the security updates only, in the background. */
    void install(boolean securityOnly) {
        if (busy != Busy.NONE) return;
        setBusy(Busy.INSTALLING);
        if (progressView == null) progressView = progressViews.get();
        ProgressView view = progressView;
        view.begin();
        run(() -> service.upgrade(List.of(), securityOnly, new ProgressListener() {
            @Override
            public void progress(Progress progress) {
                SwingUtilities.invokeLater(() -> {
                    percent = progress.overall();
                    refreshLook();
                    view.progress(progress);
                });
            }

            @Override
            public void message(String line) {
                LOGGER.fine(line);
                SwingUtilities.invokeLater(() -> view.message(line));
            }
        }), true);
    }

    /** Fetches the package lists again; the cell then shows what they offer. */
    void check() {
        if (busy != Busy.NONE) return;
        setBusy(Busy.CHECKING);
        run(() -> service.refresh(ProgressListener.NONE), false);
    }

    private void run(Supplier<Result> operation, boolean installing) {
        try {
            actions.execute(() -> {
                Result result = operation.get();
                List<Change> pending;
                try {
                    pending = service.upgrades();
                } catch (PackageException e) {
                    LOGGER.log(Level.FINE, "Cannot read the pending updates", e);
                    pending = updates;
                }
                List<Change> finalPending = pending;
                SwingUtilities.invokeLater(() -> finished(result, installing, finalPending));
            });
        } catch (RejectedExecutionException stopped) {
            setBusy(Busy.NONE);
        }
    }

    private void finished(Result result, boolean installing, List<Change> pending) {
        setBusy(Busy.NONE);
        show(pending);
        String message = resultMessage(result, installing);
        // The progress window, when the user kept it open, shows the outcome itself.
        boolean shown = installing && progressView != null && progressView.finish(message, !result.success());
        if (message != null && !shown) notifier.notify(message, !result.success());
    }

    private void showDialog(String message, boolean warning) {
        JOptionPane.showMessageDialog(button.isShowing() ? button : null, message, texts.get("updates.title"),
                warning ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    /** What to tell the user after an operation, or null when there is nothing to say. */
    String resultMessage(Result result, boolean installing) {
        return switch (result.status()) {
            case DONE -> installing
                    ? texts.get(result.rebootRequired() ? "updates.done.reboot" : "updates.done") : null;
            case LOCKED -> texts.get("updates.error.locked");
            // The user closed the password dialog: their choice, no message
            case NOT_AUTHORIZED -> null;
            case FAILED -> texts.format(installing ? "updates.error.install" : "updates.error.check", result.message());
        };
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** Flat cell on the button plate, drawn like the network cell. */
    private final class CellButton extends JButton {
        CellButton() {
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = NetworkIcons.smooth(g);
            int w = getWidth();
            int h = getHeight();
            ButtonModel model = getModel();
            boolean pressed = model.isArmed() && model.isPressed();
            if (pressed) {
                g2.setColor(theme.sunkenBg());
                g2.fillRect(0, 0, w, h);
            } else if (model.isRollover()) {
                g2.setColor(theme.softHighlight());
                g2.fillRect(0, 0, w, h);
            }
            double size = Math.min(w, h) - 4;
            double x = (w - size) / 2 + (pressed ? 1 : 0);
            double y = (h - size) / 2 + (pressed ? 1 : 0);
            UpdateIcons.paintTray(g2, x, y, size, theme.textActive());
            if (busy == Busy.INSTALLING) {
                UpdateIcons.paintProgress(g2, 1, h - 4, w - 2, 3, percent, theme.textActive(), theme.shadow());
            } else if (busy == Busy.CHECKING) {
                UpdateIcons.paintProgress(g2, 1, h - 4, w - 2, 3, -1, theme.textActive(), theme.shadow());
            }
            if (!updates.isEmpty()) {
                boolean security = securityCount() > 0;
                UpdateIcons.paintBadge(g2, w, size, updates.size(), security ? SECURITY : theme.darkShadow(),
                        Color.WHITE);
            }
            g2.dispose();
        }
    }
}
