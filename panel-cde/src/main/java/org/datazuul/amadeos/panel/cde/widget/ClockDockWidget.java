package org.datazuul.amadeos.panel.cde.widget;

import org.datazuul.amadeos.base.xdg.XdgDirectories;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.Timer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

/**
 * Authentic CDE Workstation Front Panel Clock module:
 * - Top: Drawer notch tab with darker gray background (no triangle arrow).
 * - Center: Analog Clock (Default) with chiseled etched groove border,
 *   authentic #A8988F dial face, high-contrast white ticks with drop shadows,
 *   slate-cyan hands centered with exact pivot.
 * - Digital Clock mode: Clean monospace time and uppercase date.
 * - Context menu to toggle between Analog and Digital styles.
 * - Modular right bay divider groove.
 */
public final class ClockDockWidget extends AbstractDockWidget {
    public enum ClockStyle {
        ANALOG,
        DIGITAL
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");
    private static final DateTimeFormatter TOOLTIP_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy HH:mm:ss");

    private static final Color HAND_COLOR = new Color(0x9A, 0xC8, 0xD6);
    private static final Color DIAL_BG = new Color(0xA8, 0x98, 0x8F);
    private static final Color DRAWER_TAB_BG = new Color(0x99, 0x99, 0x99);

    private final JPanel panel;
    private final Timer timer;
    private ClockStyle style = ClockStyle.ANALOG;
    final JPopupMenu contextMenu;
    final JRadioButtonMenuItem analogItem;
    final JRadioButtonMenuItem digitalItem;

    public ClockDockWidget() {
        this(null);
    }

    public ClockDockWidget(ClockStyle initialStyle) {
        super("clock");
        if (initialStyle != null) {
            this.style = initialStyle;
        } else {
            loadPersistedStyle();
        }

        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                DesktopTheme t = currentTheme;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int usableW = w - 2;

                // 1. Raised drawer tab without triangle + seamless button plate
                int tabH = CdePanelChrome.TAB_HEIGHT;
                CdePanelChrome.paintSlotBay(g2, w, h, t);

                // 2. Render clock on the button plate (no vertical dividers)
                if (style == ClockStyle.ANALOG) {
                    paintAnalogClock(g2, w, h, tabH + 1, t);
                } else {
                    paintDigitalClock(g2, w, h, tabH + 1, t);
                }

                g2.dispose();
            }
        };

        panel.setPreferredSize(new Dimension(CdePanelChrome.FIRST_SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT));
        panel.setBackground(currentTheme.base());

        // Context menu to select clock style
        contextMenu = new JPopupMenu();
        contextMenu.setBorder(new MotifBevelBorder(true, 2));

        analogItem = new JRadioButtonMenuItem("Analog Clock");
        digitalItem = new JRadioButtonMenuItem("Digital Clock");

        ButtonGroup group = new ButtonGroup();
        group.add(analogItem);
        group.add(digitalItem);

        analogItem.setSelected(style == ClockStyle.ANALOG);
        digitalItem.setSelected(style == ClockStyle.DIGITAL);

        analogItem.addActionListener(e -> setClockStyle(ClockStyle.ANALOG));
        digitalItem.addActionListener(e -> setClockStyle(ClockStyle.DIGITAL));

        contextMenu.add(analogItem);
        contextMenu.add(digitalItem);

        contextMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                syncMenuItems();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });

        panel.setComponentPopupMenu(contextMenu);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    syncMenuItems();
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        updateTooltip();

        timer = new Timer(1000, e -> {
            updateTooltip();
            panel.repaint();
        });
    }

    private void syncMenuItems() {
        analogItem.setSelected(style == ClockStyle.ANALOG);
        digitalItem.setSelected(style == ClockStyle.DIGITAL);
    }

    public ClockStyle getClockStyle() {
        return style;
    }

    public void setClockStyle(ClockStyle newStyle) {
        if (newStyle != null && this.style != newStyle) {
            this.style = newStyle;
            syncMenuItems();
            persistStyle(newStyle);
            panel.repaint();
        }
    }

    private void updateTooltip() {
        LocalTime nowTime = LocalTime.now();
        LocalDate nowDate = LocalDate.now();
        String fullInfo = nowDate.atTime(nowTime).format(TOOLTIP_FMT);
        panel.setToolTipText("Clock (" + fullInfo + ") - Right-click to change style");
    }

    private void paintAnalogClock(Graphics2D g2, int usableW, int h, int topOffset, DesktopTheme t) {
        int clockX = (usableW - 40) / 2; // 8 in 56px slot
        int clockY = topOffset + (h - topOffset - 40) / 2; // 25 in 75px slot

        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        Color shadow = t.darkShadow();
        Color highlight = t.highlight();

        // 1. Outer 40x40 2-pixel chiseled groove with 3px corner chamfers
        // Outer line: top/left shadow, bottom/right highlight
        g.setColor(shadow);
        g.drawLine(clockX + 3, clockY, clockX + 36, clockY);
        g.drawLine(clockX + 3, clockY, clockX, clockY + 3);
        g.drawLine(clockX, clockY + 3, clockX, clockY + 36);
        g.drawLine(clockX + 39, clockY + 3, clockX + 36, clockY);

        g.setColor(highlight);
        g.drawLine(clockX, clockY + 36, clockX + 3, clockY + 39);
        g.drawLine(clockX + 3, clockY + 39, clockX + 36, clockY + 39);
        g.drawLine(clockX + 36, clockY + 39, clockX + 39, clockY + 36);
        g.drawLine(clockX + 39, clockY + 36, clockX + 39, clockY + 3);

        // Inner 1px line: top/left highlight, bottom/right shadow
        g.setColor(highlight);
        g.drawLine(clockX + 3, clockY + 1, clockX + 36, clockY + 1);
        g.drawLine(clockX + 3, clockY + 1, clockX + 1, clockY + 3);
        g.drawLine(clockX + 1, clockY + 3, clockX + 1, clockY + 36);
        g.drawLine(clockX + 38, clockY + 3, clockX + 36, clockY + 1);

        g.setColor(shadow);
        g.drawLine(clockX + 1, clockY + 36, clockX + 3, clockY + 38);
        g.drawLine(clockX + 3, clockY + 38, clockX + 36, clockY + 38);
        g.drawLine(clockX + 36, clockY + 38, clockX + 38, clockY + 36);
        g.drawLine(clockX + 38, clockY + 36, clockX + 38, clockY + 3);

        // 2. Symmetrical 30x30 Octagon dial face plate
        Polygon dialPlate = new Polygon(
                new int[]{
                        clockX + 11, clockX + 28, clockX + 34, clockX + 34,
                        clockX + 28, clockX + 11, clockX + 5, clockX + 5
                },
                new int[]{
                        clockY + 5, clockY + 5, clockY + 11, clockY + 28,
                        clockY + 34, clockY + 34, clockY + 28, clockY + 11
                },
                8
        );
        g.setColor(DIAL_BG);
        g.fillPolygon(dialPlate);

        // 3. Crisp white tick marks
        g.setColor(Color.WHITE);
        // 12 o'clock cross (3x3)
        g.fillRect(clockX + 19, clockY + 3, 3, 1);
        g.fillRect(clockX + 20, clockY + 2, 1, 3);
        // 6 o'clock cross (3x3)
        g.fillRect(clockX + 19, clockY + 37, 3, 1);
        g.fillRect(clockX + 20, clockY + 36, 1, 3);
        // 9 o'clock cross (3x3)
        g.fillRect(clockX + 2, clockY + 20, 3, 1);
        g.fillRect(clockX + 3, clockY + 19, 1, 3);
        // 3 o'clock cross (3x3)
        g.fillRect(clockX + 35, clockY + 20, 3, 1);
        g.fillRect(clockX + 36, clockY + 19, 1, 3);

        // 8 hour dots (1px white each)
        g.fillRect(clockX + 28, clockY + 4, 1, 1);
        g.fillRect(clockX + 35, clockY + 11, 1, 1);
        g.fillRect(clockX + 35, clockY + 29, 1, 1);
        g.fillRect(clockX + 28, clockY + 36, 1, 1);
        g.fillRect(clockX + 11, clockY + 36, 1, 1);
        g.fillRect(clockX + 4, clockY + 29, 1, 1);
        g.fillRect(clockX + 4, clockY + 11, 1, 1);
        g.fillRect(clockX + 11, clockY + 4, 1, 1);

        // 4. Clock Hands (emanating from exact center cx=clockX+20, cy=clockY+20)
        LocalTime now = LocalTime.now();
        double second = now.getSecond();
        double minute = now.getMinute() + second / 60.0;
        double hour = (now.getHour() % 12) + minute / 60.0;

        double hourAngle = Math.toRadians(hour * 30.0);
        double minAngle = Math.toRadians(minute * 6.0);

        int cx = clockX + 20;
        int cy = clockY + 20;

        // Hour hand (length 7px, width 2px)
        int hx = (int) Math.round(cx + 7.0 * Math.sin(hourAngle));
        int hy = (int) Math.round(cy - 7.0 * Math.cos(hourAngle));

        // Minute hand (length 11px, width 2px)
        int mx = (int) Math.round(cx + 11.0 * Math.sin(minAngle));
        int my = (int) Math.round(cy - 11.0 * Math.cos(minAngle));

        g.setColor(HAND_COLOR);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawLine(cx, cy, hx, hy);
        g.drawLine(cx, cy, mx, my);

        g.dispose();
    }

    private void paintDigitalClock(Graphics2D g2, int usableW, int h, int topOffset, DesktopTheme t) {
        LocalTime nowTime = LocalTime.now();
        LocalDate nowDate = LocalDate.now();
        String timeStr = nowTime.format(TIME_FMT);
        String dateStr = nowDate.format(DATE_FMT).toUpperCase();

        int cy = topOffset + (h - topOffset) / 2;
        g2.setColor(t.textActive());

        // Time
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        FontMetrics fmTime = g2.getFontMetrics();
        int timeX = Math.max(0, (usableW - fmTime.stringWidth(timeStr)) / 2);
        int timeY = cy - 3;
        g2.drawString(timeStr, timeX, timeY);

        // Date
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        FontMetrics fmDate = g2.getFontMetrics();
        int dateX = Math.max(0, (usableW - fmDate.stringWidth(dateStr)) / 2);
        int dateY = cy + 13;
        g2.drawString(dateStr, dateX, dateY);
    }

    private void loadPersistedStyle() {
        try {
            Path conf = XdgDirectories.amadeosConfigDir().resolve("clock.conf");
            if (Files.exists(conf)) {
                String val = Files.readString(conf, StandardCharsets.UTF_8).trim();
                style = ClockStyle.valueOf(val.toUpperCase());
            }
        } catch (Exception ignored) {}
    }

    private void persistStyle(ClockStyle newStyle) {
        try {
            Path dir = XdgDirectories.amadeosConfigDir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("clock.conf"), newStyle.name(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    @Override
    public JComponent component() {
        return panel;
    }

    @Override
    public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    @Override
    public void stop() {
        timer.stop();
    }

    @Override
    protected void updateThemeStyles(DesktopTheme theme) {
        panel.setBackground(theme.base());
        panel.repaint();
    }
}
