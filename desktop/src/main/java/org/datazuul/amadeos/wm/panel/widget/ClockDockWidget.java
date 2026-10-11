package org.datazuul.amadeos.wm.panel.widget;

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
import org.datazuul.amadeos.wm.theme.DesktopTheme;

/**
 * Authentic CDE Workstation Front Panel Clock module:
 * - Analog Clock (Default): Octagonal dial plate with etched 3D bevel directly on panel base,
 *   diamond hour ticks at 12, 3, 6, 9, dot ticks at other hours, slate-cyan hands.
 * - Digital Clock: Clean centered monospace time and uppercase date directly on panel base.
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

    private static final Color HAND_COLOR = new Color(0xB8, 0xDE, 0xEC);
    private static final Color TICK_COLOR = Color.WHITE;

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

                // 1. Right slot divider groove (CDE modular bay separator)
                g2.setColor(t.darkShadow());
                g2.drawLine(w - 2, 0, w - 2, h - 1);
                g2.setColor(t.highlight());
                g2.drawLine(w - 1, 0, w - 1, h - 1);

                // 2. Render clock face based on chosen style
                if (style == ClockStyle.ANALOG) {
                    paintAnalogClock(g2, w, h, t);
                } else {
                    paintDigitalClock(g2, w, h, t);
                }

                g2.dispose();
            }
        };

        panel.setPreferredSize(new Dimension(54, 54));
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

    private void paintAnalogClock(Graphics2D g2, int w, int h, DesktopTheme t) {
        int usableW = w - 2;
        int cx = usableW / 2; // 26
        int cy = h / 2;        // 27

        // 1. Outer chiseled border (41x41 octagon with 4px chamfer)
        // Only a chiseled border line; the inner band remains on the same level & color as panel base
        int dialR = 20;
        int c = 4;
        drawSunkenOctagonBorder(g2, cx, cy, dialR, c, t.darkShadow(), t.highlight());

        // 2. Inner clock circle with darker background for maximum hand contrast
        int innerR = 15;
        Color innerBg = new Color(
                (t.shadow().getRed() + t.darkShadow().getRed()) / 2,
                (t.shadow().getGreen() + t.darkShadow().getGreen()) / 2,
                (t.shadow().getBlue() + t.darkShadow().getBlue()) / 2
        );
        g2.setColor(innerBg);
        g2.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        // Chiseled 1px rim around inner circle
        g2.setColor(t.darkShadow());
        g2.drawArc(cx - innerR, cy - innerR, innerR * 2, innerR * 2, 45, 180);
        g2.setColor(t.softHighlight());
        g2.drawArc(cx - innerR, cy - innerR, innerR * 2, innerR * 2, 225, 180);

        // 3. 12 hour ticks in the outer band on the panel surface (radius 17)
        int tickR = 17;
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30.0);
            double tx = cx + tickR * Math.sin(angle);
            double ty = cy - tickR * Math.cos(angle);
            int rx = (int) Math.round(tx);
            int ry = (int) Math.round(ty);

            if (i % 3 == 0) {
                // Diamond tick mark at 12, 3, 6, 9
                Polygon diamond = new Polygon(
                        new int[]{rx, rx + 1, rx, rx - 1},
                        new int[]{ry - 1, ry, ry + 1, ry},
                        4
                );
                g2.setColor(TICK_COLOR);
                g2.fillPolygon(diamond);
            } else {
                // 1px dot tick at other hours
                g2.setColor(TICK_COLOR);
                g2.fillRect(rx, ry, 1, 1);
            }
        }

        // 4. Hands with dark drop shadow for crisp readability
        LocalTime now = LocalTime.now();
        double second = now.getSecond();
        double minute = now.getMinute() + second / 60.0;
        double hour = (now.getHour() % 12) + minute / 60.0;

        double hourAngle = Math.toRadians(hour * 30.0);
        double minAngle = Math.toRadians(minute * 6.0);

        Stroke origStroke = g2.getStroke();

        // Hour hand (length 8.5px)
        double hx = cx + 8.5 * Math.sin(hourAngle);
        double hy = cy - 8.5 * Math.cos(hourAngle);

        // Minute hand (length 12.5px)
        double mx = cx + 12.5 * Math.sin(minAngle);
        double my = cy - 12.5 * Math.cos(minAngle);

        // 1px offset dark shadow under hands for crisp contrast
        g2.setColor(new Color(0, 0, 0, 140));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx + 1, cy + 1, (int) Math.round(hx) + 1, (int) Math.round(hy) + 1);
        g2.drawLine(cx + 1, cy + 1, (int) Math.round(mx) + 1, (int) Math.round(my) + 1);

        // Bright slate-cyan hands
        g2.setColor(HAND_COLOR);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx, cy, (int) Math.round(hx), (int) Math.round(hy));
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx, cy, (int) Math.round(mx), (int) Math.round(my));

        // Center pivot cap
        g2.fillOval(cx - 2, cy - 2, 5, 5);
        g2.setColor(t.darkShadow());
        g2.fillOval(cx - 1, cy - 1, 3, 3);

        g2.setStroke(origStroke);
    }

    private void paintDigitalClock(Graphics2D g2, int w, int h, DesktopTheme t) {
        LocalTime nowTime = LocalTime.now();
        LocalDate nowDate = LocalDate.now();
        String timeStr = nowTime.format(TIME_FMT);
        String dateStr = nowDate.format(DATE_FMT).toUpperCase();

        int usableW = w - 2;
        g2.setColor(t.textActive());

        // Time
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        FontMetrics fmTime = g2.getFontMetrics();
        int timeX = Math.max(0, (usableW - fmTime.stringWidth(timeStr)) / 2);
        int timeY = h / 2 - 2;
        g2.drawString(timeStr, timeX, timeY);

        // Date
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        FontMetrics fmDate = g2.getFontMetrics();
        int dateX = Math.max(0, (usableW - fmDate.stringWidth(dateStr)) / 2);
        int dateY = h / 2 + 12;
        g2.drawString(dateStr, dateX, dateY);
    }

    private Polygon createOctagon(int cx, int cy, int r, int c) {
        int x0 = cx - r, x1 = x0 + c, x2 = x0 + r * 2 - c, x3 = x0 + r * 2;
        int y0 = cy - r, y1 = y0 + c, y2 = y0 + r * 2 - c, y3 = y0 + r * 2;
        return new Polygon(
                new int[]{x1, x2, x3, x3, x2, x1, x0, x0},
                new int[]{y0, y0, y1, y2, y3, y3, y2, y1},
                8
        );
    }

    private void drawSunkenOctagonBorder(Graphics2D g2, int cx, int cy, int r, int c, Color dark, Color light) {
        int x0 = cx - r, x1 = x0 + c, x2 = x0 + r * 2 - c, x3 = x0 + r * 2;
        int y0 = cy - r, y1 = y0 + c, y2 = y0 + r * 2 - c, y3 = y0 + r * 2;

        // Top & Left (shadow)
        g2.setColor(dark);
        g2.drawLine(x0, y2, x0, y1);
        g2.drawLine(x0, y1, x1, y0);
        g2.drawLine(x1, y0, x2, y0);
        g2.drawLine(x0, y2, x1, y3);

        // Bottom & Right (highlight)
        g2.setColor(light);
        g2.drawLine(x2, y0, x3, y1);
        g2.drawLine(x3, y1, x3, y2);
        g2.drawLine(x3, y2, x2, y3);
        g2.drawLine(x1, y3, x2, y3);
    }

    private void loadPersistedStyle() {
        try {
            String userHome = System.getProperty("user.home", "/tmp");
            Path conf = Path.of(userHome, ".config", "amadeos", "clock.conf");
            if (Files.exists(conf)) {
                String val = Files.readString(conf, StandardCharsets.UTF_8).trim();
                style = ClockStyle.valueOf(val.toUpperCase());
            }
        } catch (Exception ignored) {}
    }

    private void persistStyle(ClockStyle newStyle) {
        try {
            String userHome = System.getProperty("user.home", "/tmp");
            Path dir = Path.of(userHome, ".config", "amadeos");
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
