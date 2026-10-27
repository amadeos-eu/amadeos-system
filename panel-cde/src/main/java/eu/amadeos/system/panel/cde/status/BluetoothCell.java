package eu.amadeos.system.panel.cde.status;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.bluetooth.BluetoothDevice;
import eu.amadeos.system.base.bluetooth.BluetoothService;
import eu.amadeos.system.base.bluetooth.BluetoothService.BluetoothException;
import eu.amadeos.system.base.bluetooth.BluetoothStatus;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.panel.cde.widget.AbstractDockWidget.MotifBevelBorder;

/**
 * Bluetooth indicator of the status slot: the Bluetooth rune, blue while switched on with a dot per connected
 * device, grey and struck through while off. A click opens a menu above the panel to switch Bluetooth on or off
 * and to connect or disconnect the paired devices; pairing new ones is in Settings &gt; Bluetooth. Shown only on
 * computers with a Bluetooth adapter.
 */
public final class BluetoothCell implements StatusCell {
    private static final Logger LOGGER = Logger.getLogger(BluetoothCell.class.getName());
    /** BlueZ reports changes as signals; this catches up should one get lost, and finds an adapter plugged in. */
    private static final long POLL_SECONDS = 30;

    private final BluetoothService service;
    private final Runnable openSettings;
    private final Messages texts;
    private final CellButton button = new CellButton();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(daemon("bluetooth-status"));
    private final ExecutorService actions = Executors.newSingleThreadExecutor(daemon("bluetooth-action"));
    private DesktopTheme theme = ThemeManager.currentTheme();
    private BluetoothStatus status = BluetoothStatus.UNAVAILABLE;
    private Runnable relayout = () -> { };
    private ScheduledFuture<?> pendingRefresh;
    private AutoCloseable watch;
    JPopupMenu menu;

    /** @param openSettings opens Settings &gt; Bluetooth, for pairing and visibility */
    public BluetoothCell(BluetoothService service, Runnable openSettings) {
        this(service, openSettings, Messages.load(BluetoothCell.class, NetworkCell.DESKTOP_TEXTS,
                LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale()));
    }

    BluetoothCell(BluetoothService service, Runnable openSettings, Messages texts) {
        this.service = service;
        this.openSettings = openSettings;
        this.texts = texts;
        button.addActionListener(e -> openMenu());
        show(status);
    }

    @Override
    public JComponent component() {
        return button;
    }

    /** Only with an adapter; a desktop computer without Bluetooth keeps the place for other cells. */
    @Override
    public boolean available() {
        return status.hasAdapter();
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
                LOGGER.log(Level.FINE, "Closing Bluetooth watch failed", e);
            }
        }
        poller.shutdownNow();
        actions.shutdownNow();
    }

    BluetoothStatus status() {
        return status;
    }

    /** Shows a status on the cell; called on the event dispatch thread. */
    void show(BluetoothStatus s) {
        boolean wasAvailable = available();
        this.status = s;
        button.setToolTipText(describe());
        button.repaint();
        if (wasAvailable != available()) relayout.run();
    }

    private boolean on() {
        return status.hasAdapter() && status.adapter().powered();
    }

    private List<BluetoothDevice> connected() {
        return status.paired().stream().filter(BluetoothDevice::connected).toList();
    }

    /** One line about Bluetooth, for the tooltip and the head of the menu. */
    String describe() {
        if (!status.hasAdapter()) return texts.get("bluetooth.unavailable");
        if (!on()) return texts.get("bluetooth.off");
        List<BluetoothDevice> c = connected();
        return switch (c.size()) {
            case 0 -> texts.get("bluetooth.on");
            case 1 -> texts.format("bluetooth.connected.one", c.getFirst().name());
            default -> texts.format("bluetooth.connected.many", c.size());
        };
    }

    /** A device in the menu: "WH-1000XM4 (80 %)". */
    String label(BluetoothDevice d) {
        return d.connected() && d.battery() >= 0 ? texts.format("bluetooth.device.battery", d.name(), d.battery())
                : d.name();
    }

    /** BlueZ sends several signals per change; refresh once they have settled. */
    private synchronized void scheduleRefresh() {
        if (pendingRefresh != null && !pendingRefresh.isDone()) return;
        try {
            pendingRefresh = poller.schedule(this::refreshNow, 300, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // stop() was called
        }
    }

    private void refreshNow() {
        BluetoothStatus s = service.status();
        SwingUtilities.invokeLater(() -> show(s));
    }

    void openMenu() {
        if (menu != null && menu.isVisible()) {
            menu.setVisible(false);
            return;
        }
        menu = buildMenu();
        if (button.isShowing()) menu.show(button, 0, -menu.getPreferredSize().height - 2);
    }

    // --8<-- [start:menu]
    JPopupMenu buildMenu() {
        JPopupMenu m = new JPopupMenu();
        m.setBorder(new MotifBevelBorder(true, 2));
        JMenuItem head = new JMenuItem(describe());
        head.setEnabled(false);
        m.add(head);
        m.addSeparator();
        JCheckBoxMenuItem power = new JCheckBoxMenuItem(texts.get("bluetooth.title"), on());
        power.addActionListener(e -> act(() -> service.setPowered(power.isSelected())));
        m.add(power);
        if (on()) {
            List<BluetoothDevice> paired = status.paired();
            if (paired.isEmpty()) {
                JMenuItem none = new JMenuItem(texts.get("bluetooth.none"));
                none.setEnabled(false);
                m.add(none);
            }
            // A tick for each connected device; choosing one connects or disconnects it.
            for (BluetoothDevice d : paired) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(label(d), d.connected());
                item.addActionListener(e -> act(() -> {
                    if (d.connected()) service.disconnect(d);
                    else service.connect(d);
                }));
                m.add(item);
            }
        }
        m.addSeparator();
        JMenuItem settings = new JMenuItem(texts.get("bluetooth.settings"));
        settings.addActionListener(e -> openSettings.run());
        m.add(settings);
        style(m);
        return m;
    }
    // --8<-- [end:menu]

    private void style(MenuElement element) {
        if (element.getComponent() instanceof JComponent c) {
            c.setBackground(theme.base());
            if (!(c instanceof JPopupMenu)) c.setForeground(theme.textActive());
        }
        for (MenuElement child : element.getSubElements()) style(child);
    }

    /** Runs a change in the background and reports a refusal. */
    private void act(Action action) {
        actions.execute(() -> {
            try {
                action.run();
            } catch (BluetoothException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(button,
                        texts.format("bluetooth.error", e.getMessage()), texts.get("bluetooth.title"),
                        JOptionPane.ERROR_MESSAGE));
            }
            scheduleRefresh();
        });
    }

    @FunctionalInterface
    private interface Action {
        void run() throws BluetoothException;
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
            boolean on = on();
            Color ink = on ? BluetoothIcons.BLUE : theme.shadow();
            BluetoothIcons.paintRune(g2, x, y, size, ink, on ? connected().size() : 0);
            if (!on) NetworkIcons.paintSlash(g2, x, y, size, theme.darkShadow());
            g2.dispose();
        }
    }
}
