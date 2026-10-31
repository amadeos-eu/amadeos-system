package eu.amadeos.system.panel.cde.status;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI.MotifBevelBorder;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.notify.NotificationClient;
import eu.amadeos.system.base.notify.dbus.NotificationCenter;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * The bell in the status slot: how many notifications are new (a red number), crossed out while Do not disturb
 * holds back the pop-ups. A click opens the history of the notification daemon; the right button opens a menu
 * with Do not disturb for an hour,
 * until tomorrow morning or until turned off, Clear all and the settings.
 * <p>
 * The cell follows the daemon through {@link NotificationClient}: its StateChanged signal, and the daemon going
 * away or starting again.
 */
public final class NotificationCell implements StatusCell {
    /** What the cell needs of the daemon; {@link NotificationClient} on the session bus, a recorder in tests. */
    interface Daemon {
        NotificationClient.State state();

        void onState(Consumer<NotificationClient.State> listener);

        boolean showHistory();

        boolean clearAll();

        boolean setDoNotDisturb(long until);
    }

    static final Color BADGE = new Color(0xC0, 0x28, 0x28);

    private final Daemon daemon;
    private final Runnable startDaemon;
    private final Runnable openSettings;
    private final Messages texts;
    private final CellButton button = new CellButton();
    private final ExecutorService calls = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notification-cell");
        t.setDaemon(true);
        return t;
    });
    private DesktopTheme theme = ThemeManager.currentTheme();
    private NotificationClient.State state = NotificationClient.State.NOT_RUNNING;
    JPopupMenu menu;

    /**
     * @param startDaemon  starts {@code amadeos-notifications}, which shows the history (when no daemon answers)
     * @param openSettings opens Settings > Notifications
     */
    public NotificationCell(NotificationClient client, Runnable startDaemon, Runnable openSettings) {
        this(new Daemon() {
            @Override
            public NotificationClient.State state() {
                return client.state();
            }

            @Override
            public void onState(Consumer<NotificationClient.State> listener) {
                client.onState(listener);
            }

            @Override
            public boolean showHistory() {
                return client.showHistory();
            }

            @Override
            public boolean clearAll() {
                return client.clearAll();
            }

            @Override
            public boolean setDoNotDisturb(long until) {
                return client.setDoNotDisturb(until);
            }
        }, startDaemon, openSettings, Messages.load(NotificationCell.class, NetworkCell.DESKTOP_TEXTS,
                LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale()));
    }

    NotificationCell(Daemon daemon, Runnable startDaemon, Runnable openSettings, Messages texts) {
        this.daemon = daemon;
        this.startDaemon = startDaemon;
        this.openSettings = openSettings;
        this.texts = texts;
        button.addActionListener(e -> showHistory());
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) openMenu();
            }
        });
        show(state);
    }

    @Override
    public JComponent component() {
        return button;
    }

    @Override
    public void onThemeChanged(DesktopTheme theme) {
        this.theme = theme;
        button.repaint();
    }

    @Override
    public void start() {
        daemon.onState(s -> SwingUtilities.invokeLater(() -> show(s)));
        calls.execute(() -> {
            NotificationClient.State s = daemon.state();
            SwingUtilities.invokeLater(() -> show(s));
        });
    }

    @Override
    public void stop() {
        calls.shutdownNow();
    }

    NotificationClient.State state() {
        return state;
    }

    /** Shows the daemon's state; on the event dispatch thread. */
    void show(NotificationClient.State s) {
        state = s;
        button.setToolTipText(describe(s));
        button.repaint();
    }

    /** "3 new notifications · Do not disturb". */
    String describe(NotificationClient.State s) {
        String count = s.unseen() == 0 ? texts.get("notifications.none")
                : s.unseen() == 1 ? texts.get("notifications.one") : texts.format("notifications.many", s.unseen());
        return s.doNotDisturb() ? texts.format("notifications.dnd.on", count) : count;
    }

    void showHistory() {
        calls.execute(() -> {
            if (!daemon.showHistory()) startDaemon.run();
        });
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
        JMenuItem head = new JMenuItem(describe(state));
        head.setEnabled(false);
        m.add(head);
        m.addSeparator();
        m.add(item("notifications.history", this::showHistory, true));
        m.addSeparator();
        if (state.doNotDisturb()) {
            m.add(item("notifications.dnd.off", () -> dnd(NotificationCenter.DND_OFF), state.running()));
        } else {
            m.add(item("notifications.dnd.hour",
                    () -> dnd(Instant.now().getEpochSecond() + 3600), state.running()));
            m.add(item("notifications.dnd.tomorrow", () -> dnd(NotificationCenter.DND_TOMORROW), state.running()));
            m.add(item("notifications.dnd.always", () -> dnd(NotificationCenter.DND_ON), state.running()));
        }
        m.addSeparator();
        m.add(item("notifications.clear", () -> calls.execute(daemon::clearAll), state.running()));
        if (openSettings != null) m.add(item("notifications.settings", openSettings, true));
        style(m);
        return m;
    }

    private void dnd(long until) {
        calls.execute(() -> daemon.setDoNotDisturb(until));
    }

    private JMenuItem item(String key, Runnable action, boolean enabled) {
        JMenuItem item = new JMenuItem(texts.get(key));
        item.setName(key);
        item.setEnabled(enabled);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void style(MenuElement element) {
        if (element.getComponent() instanceof JComponent c) {
            c.setBackground(theme.base());
            if (!(c instanceof JPopupMenu)) c.setForeground(theme.textActive());
        }
        for (MenuElement child : element.getSubElements()) style(child);
    }

    /** Flat cell on the button plate, drawn like the updates cell. */
    private final class CellButton extends JButton {
        CellButton() {
            setName("status.notifications");
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
            NotificationIcons.paintBell(g2, x, y, size, theme.textActive(), state.doNotDisturb());
            if (state.unseen() > 0) UpdateIcons.paintBadge(g2, w, size, state.unseen(), BADGE, Color.WHITE);
            g2.dispose();
        }
    }
}
