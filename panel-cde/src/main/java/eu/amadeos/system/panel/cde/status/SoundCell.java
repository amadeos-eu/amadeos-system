package eu.amadeos.system.panel.cde.status;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.sound.AudioDevice;
import eu.amadeos.system.base.sound.SoundService;
import eu.amadeos.system.base.sound.SoundService.SoundException;
import eu.amadeos.system.base.sound.SoundStatus;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.panel.cde.widget.AbstractDockWidget.MotifBevelBorder;

/**
 * Sound indicator of the status slot: a loudspeaker whose waves show the volume, crossed out when muted. A
 * click opens a menu above the panel with a volume slider and the outputs and inputs to choose from; the
 * mouse wheel over the cell changes the volume and the middle button mutes. Volume and mute act on the
 * output in use, so they follow when another device is chosen.
 */
public final class SoundCell implements StatusCell {
    private static final Logger LOGGER = Logger.getLogger(SoundCell.class.getName());
    /** Volume steps of one notch of the mouse wheel, in percent. */
    static final int WHEEL_STEP = 5;
    /** The slider ends at 100 %; louder only by the wheel or another program. */
    static final int SLIDER_MAX = 100;
    /** pactl subscribe reports every change, also of devices; this catches up should one get lost. */
    private static final long POLL_SECONDS = 60;

    private final SoundService service;
    private final Messages texts;
    private final CellButton button = new CellButton();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(daemon("sound-status"));
    private final ExecutorService actions = Executors.newSingleThreadExecutor(daemon("sound-action"));
    /** Volume the slider or wheel asked for and that is not sent yet, -1 when none is waiting. */
    private final AtomicInteger pendingVolume = new AtomicInteger(-1);
    private DesktopTheme theme = ThemeManager.currentTheme();
    private SoundStatus status = SoundStatus.UNAVAILABLE;
    /** What the cell shows: the status, or the volume just asked for until the server confirms it. */
    private int shownVolume;
    private boolean shownMuted = true;
    private ScheduledFuture<?> pendingRefresh;
    private AutoCloseable watch;
    JPopupMenu menu;
    JSlider slider;
    /** Set while {@link #show} moves the slider, so that this is not taken for the user's change. */
    private boolean followingServer;

    public SoundCell(SoundService service) {
        this(service, Messages.load(SoundCell.class, NetworkCell.DESKTOP_TEXTS,
                LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale()));
    }

    SoundCell(SoundService service, Messages texts) {
        this.service = service;
        this.texts = texts;
        button.addActionListener(e -> openMenu());
        button.addMouseWheelListener(e -> changeVolume(-e.getWheelRotation() * WHEEL_STEP));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e) && status.available()) setMuted(!shownMuted);
            }
        });
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
                LOGGER.log(Level.FINE, "Closing sound watch failed", e);
            }
        }
        poller.shutdownNow();
        actions.shutdownNow();
    }

    SoundStatus status() {
        return status;
    }

    int shownVolume() {
        return shownVolume;
    }

    boolean shownMuted() {
        return shownMuted;
    }

    /** Shows a status on the cell, and on the open menu's slider; called on the event dispatch thread. */
    void show(SoundStatus status) {
        this.status = status;
        if (pendingVolume.get() < 0) {
            shownVolume = status.volume();
            shownMuted = status.muted();
            if (slider != null && !slider.getValueIsAdjusting()) {
                followingServer = true;
                slider.setValue(Math.min(SLIDER_MAX, shownVolume));
                followingServer = false;
            }
        }
        button.setToolTipText(tooltip());
        button.repaint();
    }

    /** One line about the output, for the tooltip and the head of the menu. */
    String describe() {
        if (!status.available()) return texts.get("sound.unavailable");
        Optional<AudioDevice> output = status.output();
        if (output.isEmpty()) return texts.get("sound.none");
        return shownMuted
                ? texts.format("sound.tooltip.muted", output.get().description())
                : texts.format("sound.tooltip", shownVolume, output.get().description());
    }

    private String tooltip() {
        return status.available() && status.output().isPresent()
                ? "<html>" + escape(describe()) + "<br><small>" + escape(texts.get("sound.hint")) + "</small></html>"
                : describe();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** pactl subscribe reports a burst of lines per change; refresh once they have settled. */
    private synchronized void scheduleRefresh() {
        if (pendingRefresh != null && !pendingRefresh.isDone()) return;
        try {
            pendingRefresh = poller.schedule(this::refreshNow, 150, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // stop() was called
        }
    }

    private void refreshNow() {
        SoundStatus s = service.status();
        SwingUtilities.invokeLater(() -> show(s));
    }

    /** Louder or softer by {@code delta} percent, from the wheel. */
    void changeVolume(int delta) {
        if (!status.available() || status.output().isEmpty()) return;
        setVolume(Math.max(0, Math.min(150, shownVolume + delta)));
    }

    // --8<-- [start:volume]
    /**
     * Asks for a volume; the cell shows it at once. While the slider is dragged only the newest value is
     * sent once the previous one is done, so pactl does not queue up. A change of volume also unmutes.
     */
    void setVolume(int percent) {
        shownVolume = percent;
        boolean unmute = shownMuted;
        shownMuted = false;
        button.setToolTipText(tooltip());
        button.repaint();
        if (pendingVolume.getAndSet(percent) >= 0) return;
        actions.execute(() -> {
            try {
                if (unmute) service.setMuted(false);
                service.setVolume(pendingVolume.getAndSet(-1));
            } catch (SoundException e) {
                pendingVolume.set(-1);
                SwingUtilities.invokeLater(() -> failed(e));
            }
            scheduleRefresh();
        });
    }
    // --8<-- [end:volume]

    void setMuted(boolean muted) {
        shownMuted = muted;
        button.setToolTipText(tooltip());
        button.repaint();
        act(() -> service.setMuted(muted));
    }

    /** Reads the devices in the background, then opens the menu above the cell. */
    void openMenu() {
        if (menu != null && menu.isVisible()) {
            menu.setVisible(false);
            return;
        }
        poller.execute(() -> {
            SoundStatus s = service.status();
            SwingUtilities.invokeLater(() -> {
                show(s);
                menu = buildMenu(s);
                if (button.isShowing()) {
                    menu.show(button, 0, -menu.getPreferredSize().height - 2);
                }
            });
        });
    }

    JPopupMenu buildMenu(SoundStatus s) {
        JPopupMenu m = new JPopupMenu();
        m.setBorder(new MotifBevelBorder(true, 2));
        m.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                slider = null;
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
        if (!s.available()) {
            JMenuItem head = new JMenuItem(texts.get("sound.unavailable"));
            head.setEnabled(false);
            m.add(head);
            style(m);
            return m;
        }

        if (s.output().isPresent()) m.add(volumeRow());

        m.addSeparator();
        m.add(heading(texts.get("sound.output"), false));
        if (s.outputs().isEmpty()) m.add(disabled(texts.get("sound.output.none")));
        ButtonGroup outputs = new ButtonGroup();
        AudioDevice current = s.output().orElse(null);
        for (AudioDevice d : s.outputs()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(d.description(), d.equals(current));
            item.setToolTipText(d.name());
            item.addActionListener(e -> act(() -> service.setOutput(d)));
            outputs.add(item);
            m.add(item);
        }

        m.addSeparator();
        m.add(heading(texts.get("sound.input"), true));
        if (s.inputs().isEmpty()) m.add(disabled(texts.get("sound.input.none")));
        ButtonGroup inputs = new ButtonGroup();
        AudioDevice input = s.input().orElse(null);
        for (AudioDevice d : s.inputs()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(d.description(), d.equals(input));
            item.setToolTipText(d.name());
            item.addActionListener(e -> act(() -> service.setInput(d)));
            inputs.add(item);
            m.add(item);
        }
        if (input != null) {
            JCheckBoxMenuItem mute = new JCheckBoxMenuItem(texts.get("sound.input.mute"), input.muted());
            mute.addActionListener(e -> act(() -> service.setInputMuted(mute.isSelected())));
            m.add(mute);
        }

        style(m);
        return m;
    }

    /** Mute button with the speaker, the slider and the volume in figures, in one line. */
    private JComponent volumeRow() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 8));

        JToggleButton mute = new JToggleButton(SoundIcons.speakerIcon(theme, () -> shownVolume, () -> shownMuted),
                shownMuted);
        mute.setToolTipText(texts.get("sound.mute"));
        mute.setFocusPainted(false);
        mute.setContentAreaFilled(false);
        mute.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        mute.addActionListener(e -> {
            setMuted(mute.isSelected());
            mute.repaint();
        });

        slider = new JSlider(0, SLIDER_MAX, Math.min(SLIDER_MAX, shownVolume));
        slider.setUI(new MotifSliderUI(slider));
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setPreferredSize(new Dimension(180, slider.getPreferredSize().height));
        slider.setToolTipText(texts.get("sound.volume"));
        JLabel percent = new JLabel(shownVolume + " %");
        percent.setPreferredSize(new Dimension(percent.getFontMetrics(percent.getFont()).stringWidth("150 %") + 4,
                percent.getPreferredSize().height));
        JSlider s = slider;
        s.addChangeListener(e -> {
            percent.setText(s.getValue() + " %");
            if (!followingServer && (s.getValue() != Math.min(SLIDER_MAX, shownVolume) || shownMuted)) {
                setVolume(s.getValue());
                mute.setSelected(false);
            }
            mute.repaint();
        });

        row.add(mute, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(percent, BorderLayout.EAST);
        return row;
    }

    private JMenuItem heading(String text, boolean input) {
        JMenuItem item = new JMenuItem(text, SoundIcons.headingIcon(theme, input));
        item.setEnabled(false);
        item.setDisabledIcon(item.getIcon());
        item.setFont(item.getFont().deriveFont(Font.BOLD));
        return item;
    }

    private static JMenuItem disabled(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(false);
        return item;
    }

    private void style(MenuElement element) {
        if (element.getComponent() instanceof JComponent c) {
            c.setBackground(theme.base());
            if (!(c instanceof JPopupMenu)) c.setForeground(theme.textActive());
            for (java.awt.Component child : c.getComponents()) {
                if (child instanceof JComponent j && !(child instanceof MenuElement)) {
                    j.setForeground(theme.textActive());
                    for (java.awt.Component inner : j.getComponents()) inner.setForeground(theme.textActive());
                }
            }
        }
        for (MenuElement child : element.getSubElements()) style(child);
    }

    /** Runs a change in the background and reports a refusal. */
    private void act(Action action) {
        actions.execute(() -> {
            try {
                action.run();
            } catch (SoundException e) {
                SwingUtilities.invokeLater(() -> failed(e));
            }
            scheduleRefresh();
        });
    }

    private void failed(SoundException e) {
        JOptionPane.showMessageDialog(button, texts.format("sound.error.change", e.getMessage()),
                texts.get("sound.title"), JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface Action {
        void run() throws SoundException;
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * The slider as in CDE: a sunken groove, filled up to the volume in the selection color, and a raised
     * thumb with a notch, in the colors of the theme instead of the look and feel's.
     */
    private final class MotifSliderUI extends javax.swing.plaf.basic.BasicSliderUI {
        MotifSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(12, 18);
        }

        @Override
        public void paintTrack(Graphics g) {
            Rectangle t = trackRect;
            int h = 8;
            int y = t.y + (t.height - h) / 2;
            g.setColor(theme.sunkenBg());
            g.fillRect(t.x, y, t.width, h);
            int filled = thumbRect.x + thumbRect.width / 2 - t.x;
            g.setColor(theme.selectionBackground());
            g.fillRect(t.x + 2, y + 2, Math.max(0, filled - 2), h - 4);
            eu.amadeos.commons.swing.AmadeUI.paintBevel(g, t.x, y, t.width, h, false, 2);
        }

        @Override
        public void paintThumb(Graphics g) {
            Rectangle r = thumbRect;
            g.setColor(theme.base());
            g.fillRect(r.x, r.y, r.width, r.height);
            eu.amadeos.commons.swing.AmadeUI.paintBevel(g, r.x, r.y, r.width, r.height, true, 2);
            int cx = r.x + r.width / 2;
            g.setColor(theme.shadow());
            g.drawLine(cx - 1, r.y + 4, cx - 1, r.y + r.height - 5);
            g.setColor(theme.highlight());
            g.drawLine(cx, r.y + 4, cx, r.y + r.height - 5);
        }

        @Override
        public void paintFocus(Graphics g) {
        }
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
            if (!status.available() || status.output().isEmpty()) {
                SoundIcons.paintSpeaker(g2, x, y, size, 0, theme.shadow(), theme.shadow());
                NetworkIcons.paintSlash(g2, x, y, size, theme.darkShadow());
            } else if (shownMuted) {
                SoundIcons.paintMuted(g2, x, y, size, theme.textActive(), theme.darkShadow());
            } else {
                SoundIcons.paintSpeaker(g2, x, y, size, SoundIcons.waves(shownVolume), theme.textActive(),
                        theme.shadow());
            }
            g2.dispose();
        }
    }
}
