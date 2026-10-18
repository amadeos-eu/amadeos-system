package eu.amadeos.system.panel.cde.status;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.net.NetworkService;
import eu.amadeos.system.base.net.NetworkService.NetworkException;
import eu.amadeos.system.base.net.NetworkStatus;
import eu.amadeos.system.base.net.WifiNetwork;
import eu.amadeos.system.panel.cde.widget.AbstractDockWidget.MotifBevelBorder;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * Network indicator of the status slot: an Ethernet jack or the WLAN fan with the signal strength. A click
 * opens a menu above the panel with the WLAN networks in range; choosing one joins it, asking for the
 * password the first time. NetworkManager saves joined networks and reconnects to them at the next start.
 */
public final class NetworkCell implements StatusCell {
    private static final Logger LOGGER = Logger.getLogger(NetworkCell.class.getName());
    static final String DESKTOP_TEXTS = "eu.amadeos.system.i18n.desktop";
    /** Signal strength is not reported as an event, so the status is read again this often (seconds). */
    private static final long POLL_SECONDS = 30;
    private static final int MAX_NETWORKS = 15;

    private final NetworkService service;
    private final Runnable openSettings;
    private final Messages texts;
    private final CellButton button = new CellButton();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(daemon("network-status"));
    private final ExecutorService actions = Executors.newSingleThreadExecutor(daemon("network-action"));
    private DesktopTheme theme = ThemeManager.currentTheme();
    private NetworkStatus status = NetworkStatus.UNAVAILABLE;
    private ScheduledFuture<?> pendingRefresh;
    private AutoCloseable watch;
    JPopupMenu menu;

    /**
     * @param openSettings opens the full network settings (static addresses, VPN, ...), for now nmtui
     */
    public NetworkCell(NetworkService service, Runnable openSettings) {
        this(service, openSettings, Messages.load(NetworkCell.class, DESKTOP_TEXTS,
                LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale()));
    }

    NetworkCell(NetworkService service, Runnable openSettings, Messages texts) {
        this.service = service;
        this.openSettings = openSettings;
        this.texts = texts;
        button.addActionListener(e -> openMenu(false));
        show(status);
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
        watch = service.watch(this::scheduleRefresh);
        poller.scheduleWithFixedDelay(this::refreshNow, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (watch != null) {
            try {
                watch.close();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Closing network watch failed", e);
            }
        }
        poller.shutdownNow();
        actions.shutdownNow();
    }

    NetworkStatus status() {
        return status;
    }

    /** Shows a status on the cell; called on the event dispatch thread. */
    void show(NetworkStatus status) {
        this.status = status;
        button.setToolTipText(describe(status));
        button.repaint();
    }

    /** One line about the connection, for the tooltip and the head of the menu. */
    String describe(NetworkStatus s) {
        return switch (s.state()) {
            case UNAVAILABLE -> texts.get("network.unavailable");
            case DISCONNECTED -> texts.get("network.disconnected");
            case CONNECTING -> texts.format("network.connecting", s.name());
            case CONNECTED -> s.kind() == NetworkStatus.Kind.WIFI
                    ? texts.format("network.connected.wifi", s.name(), s.signal())
                    : texts.format("network.connected.ethernet", s.name());
        };
    }

    /** nmcli monitor reports several lines per change; refresh once they have settled. */
    private synchronized void scheduleRefresh() {
        if (pendingRefresh != null && !pendingRefresh.isDone()) return;
        try {
            pendingRefresh = poller.schedule(this::refreshNow, 400, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // stop() was called
        }
    }

    private void refreshNow() {
        NetworkStatus s = service.status();
        SwingUtilities.invokeLater(() -> show(s));
    }

    /** Reads status and networks in the background, then opens the menu above the cell. */
    void openMenu(boolean rescan) {
        if (menu != null && menu.isVisible()) {
            menu.setVisible(false);
            return;
        }
        poller.execute(() -> {
            NetworkStatus s = service.status();
            List<WifiNetwork> networks = s.hasWifi() && s.wifiEnabled() ? service.wifiNetworks(rescan) : List.of();
            SwingUtilities.invokeLater(() -> {
                show(s);
                menu = buildMenu(s, networks);
                if (button.isShowing()) {
                    menu.show(button, 0, -menu.getPreferredSize().height - 2);
                }
            });
        });
    }

    JPopupMenu buildMenu(NetworkStatus s, List<WifiNetwork> networks) {
        JPopupMenu m = new JPopupMenu();
        m.setBorder(new MotifBevelBorder(true, 2));

        JMenuItem head = new JMenuItem(describe(s));
        head.setEnabled(false);
        m.add(head);

        if (s.hasWifi()) {
            m.addSeparator();
            JCheckBoxMenuItem radio = new JCheckBoxMenuItem(texts.get("network.wifi"), s.wifiEnabled());
            radio.addActionListener(e -> act(() -> service.setWifiEnabled(radio.isSelected())));
            m.add(radio);
            if (s.wifiEnabled()) {
                if (networks.isEmpty()) {
                    JMenuItem none = new JMenuItem(texts.get("network.none"));
                    none.setEnabled(false);
                    m.add(none);
                }
                for (WifiNetwork n : networks.subList(0, Math.min(MAX_NETWORKS, networks.size()))) {
                    m.add(networkItem(n));
                }
                JMenuItem scan = new JMenuItem(texts.get("network.scan"));
                scan.addActionListener(e -> openMenu(true));
                m.add(scan);
            }
        }

        m.addSeparator();
        JMenuItem settings = new JMenuItem(texts.get("network.settings"));
        settings.addActionListener(e -> openSettings.run());
        m.add(settings);

        style(m);
        return m;
    }

    /** The active network opens a submenu (disconnect, forget); any other joins on click. */
    private JMenuItem networkItem(WifiNetwork n) {
        javax.swing.Icon icon = NetworkIcons.wifiMenuIcon(n.bars(), n.secured(), theme);
        String tip = texts.format("network.signal", n.signal());
        if (n.active()) {
            JMenu item = new JMenu(n.ssid());
            item.setIcon(icon);
            item.setToolTipText(tip);
            item.setFont(item.getFont().deriveFont(Font.BOLD));
            JMenuItem disconnect = new JMenuItem(texts.get("network.disconnect"));
            disconnect.addActionListener(e -> act(service::disconnectWifi));
            JMenuItem forget = new JMenuItem(texts.get("network.forget"));
            forget.addActionListener(e -> act(() -> service.forget(n)));
            item.add(disconnect);
            item.add(forget);
            return item;
        }
        JMenuItem item = new JMenuItem(n.ssid(), icon);
        item.setToolTipText(tip);
        item.addActionListener(e -> join(n));
        return item;
    }

    private void style(MenuElement element) {
        if (element.getComponent() instanceof JComponent c) {
            c.setBackground(theme.base());
            if (!(c instanceof JPopupMenu)) c.setForeground(theme.textActive());
        }
        for (MenuElement child : element.getSubElements()) style(child);
    }

    /** Joins a network, asking for its password if it needs one and none is saved. */
    void join(WifiNetwork n) {
        String password = null;
        if (n.secured() && !n.known()) {
            password = askPassword(n.ssid());
            if (password == null) return;
        }
        connect(n, password);
    }

    private void connect(WifiNetwork n, String password) {
        show(new NetworkStatus(NetworkStatus.State.CONNECTING, NetworkStatus.Kind.WIFI, n.ssid(), 0,
                status.wifiDevice(), true));
        actions.execute(() -> {
            try {
                service.connect(n, password);
            } catch (NetworkException e) {
                SwingUtilities.invokeLater(() -> connectFailed(n, e.getMessage()));
            }
            scheduleRefresh();
        });
    }

    /** A saved password may be outdated: offer to enter it again, which replaces the saved connection. */
    private void connectFailed(WifiNetwork n, String reason) {
        String title = texts.get("network.title");
        if (n.known() && n.secured()) {
            int answer = JOptionPane.showConfirmDialog(button, texts.format("network.error.retry", n.ssid(), reason),
                    title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            String password = askPassword(n.ssid());
            if (password == null) return;
            WifiNetwork fresh = new WifiNetwork(n.ssid(), n.signal(), true, false, false);
            actions.execute(() -> {
                try {
                    service.forget(n);
                } catch (NetworkException e) {
                    LOGGER.log(Level.FINE, "Forgetting " + n.ssid() + " failed", e);
                }
            });
            connect(fresh, password);
        } else {
            JOptionPane.showMessageDialog(button, texts.format("network.error.connect", n.ssid(), reason),
                    title, JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Runs a change in the background and reports a refusal. */
    private void act(Action action) {
        actions.execute(() -> {
            try {
                action.run();
            } catch (NetworkException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(button,
                        texts.format("network.error.change", e.getMessage()), texts.get("network.title"),
                        JOptionPane.ERROR_MESSAGE));
            }
            scheduleRefresh();
        });
    }

    @FunctionalInterface
    private interface Action {
        void run() throws NetworkException;
    }

    /** @return the password, or null if cancelled */
    private String askPassword(String ssid) {
        JPasswordField field = new JPasswordField(22);
        char echo = field.getEchoChar();
        JCheckBox reveal = new JCheckBox(texts.get("network.password.show"));
        reveal.addActionListener(e -> field.setEchoChar(reveal.isSelected() ? 0 : echo));
        JOptionPane pane = new JOptionPane(
                new Object[]{new JLabel(texts.format("network.password.prompt", ssid)), field, reveal},
                JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        field.addActionListener(e -> pane.setValue(JOptionPane.OK_OPTION));
        JDialog dialog = pane.createDialog(button, texts.get("network.password.title"));
        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                field.requestFocusInWindow();
            }
        });
        dialog.setVisible(true);
        dialog.dispose();
        char[] password = field.getPassword();
        try {
            return Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()) ? new String(password) : null;
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** Flat cell on the button plate, drawn like the panel's launcher buttons. */
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
            Color on = theme.textActive();
            Color off = theme.shadow();
            NetworkStatus s = status;
            boolean connected = s.state() == NetworkStatus.State.CONNECTED;
            boolean connecting = s.state() == NetworkStatus.State.CONNECTING;
            if ((connected || connecting) && s.kind() == NetworkStatus.Kind.ETHERNET) {
                NetworkIcons.paintEthernet(g2, x, y, size, connected ? on : off, theme.base());
            } else if (connected) {
                NetworkIcons.paintWifi(g2, x, y, size, Math.max(1, WifiNetwork.bars(s.signal())), on, off);
            } else if (connecting) {
                // Only the dot lit, no slash: on its way
                NetworkIcons.paintWifi(g2, x, y, size, 1, on, off);
            } else {
                if (s.hasWifi()) {
                    NetworkIcons.paintWifi(g2, x, y, size, 0, on, off);
                } else {
                    NetworkIcons.paintEthernet(g2, x, y, size, off, theme.base());
                }
                NetworkIcons.paintSlash(g2, x, y, size, theme.darkShadow());
            }
            g2.dispose();
        }
    }
}
