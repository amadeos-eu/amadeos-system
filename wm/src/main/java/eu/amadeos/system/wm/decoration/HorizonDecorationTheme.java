package eu.amadeos.system.wm.decoration;

import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The frame of the Horizon look: a flat title bar in the window's own colour, a thin line in the accent
 * colour along the top of the active window, the program icon on the left (it opens the window menu) and thin
 * line symbols for minimise, maximise and close on the right; close turns coral under the pointer.
 */
public final class HorizonDecorationTheme implements DecorationTheme {
    public static final String NAME = "Horizon";
    public static final int CORNER_RADIUS = 10;
    private static final Color CLOSE_HOT = new Color(0xE8, 0x6A, 0x54);
    private static final Color CLOSE_PRESSED = new Color(0xC8, 0x50, 0x3E);

    private final FrameMetrics metrics = new HorizonFrameMetrics();
    private volatile DesktopTheme theme;

    public HorizonDecorationTheme(DesktopTheme theme) {
        this.theme = theme;
    }

    public void setTheme(DesktopTheme theme) {
        this.theme = theme;
    }

    private DesktopTheme t() {
        return theme != null ? theme : ThemeManager.currentTheme();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FrameMetrics metrics() {
        return metrics;
    }

    @Override
    public int cornerRadius() {
        return CORNER_RADIUS;
    }

    @Override
    public void paintFrame(Graphics2D g, int width, int height, String title, boolean active,
                           WindowButton hoveredButton, WindowButton pressedButton) {
        paint(g, width, height, title, active, hoveredButton, pressedButton, null, CORNER_RADIUS);
    }

    @Override
    public void paintFrame(Graphics2D g, FrameWindow frame) {
        paint(g, frame.frameWidth(), frame.frameHeight(), frame.displayedTitle(), frame.isActive(),
                frame.hoveredButton(), frame.pressedButton(), frame.icon(), frame.cornerRadius());
    }

    // --8<-- [start:paint]
    void paint(Graphics2D g, int width, int height, String title, boolean active, WindowButton hovered,
               WindowButton pressed, BufferedImage icon, int radius) {
        DesktopTheme t = t();
        Color base = t.base();
        Color ink = t.textActive();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int tbh = metrics.titleBarHeight();

        // Body and border in the window colour, the title bar a shade towards the text colour when active
        g.setColor(base);
        g.fillRect(0, 0, width, height);
        g.setColor(active ? mix(base, ink, 0.07) : mix(base, ink, 0.025));
        g.fillRect(0, 0, width, tbh);
        g.setColor(mix(base, Color.BLACK, 0.35));
        g.fillRect(metrics.borderWidth(), tbh - 1, width - 2 * metrics.borderWidth(), 1);

        // Outline along the (rounded) edge
        g.setColor(mix(base, ink, active ? 0.20 : 0.11));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 2f * radius, 2f * radius));

        // The accent line along the top of the active window, fading out towards the corners
        if (active && width > 2 * radius + 8) {
            Color accent = t.activeTitleBg();
            Color clear = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0);
            int x0 = Math.max(radius, 6);
            int mid = width / 2;
            g.setPaint(new GradientPaint(x0, 0, clear, x0 + 40, 0, accent));
            g.fillRect(x0, 1, mid - x0, 2);
            g.setPaint(new GradientPaint(width - x0 - 40, 0, accent, width - x0, 0, clear));
            g.fillRect(mid, 1, width - x0 - mid, 2);
        }

        paintIcon(g, metrics.getButtonBounds(width, WindowButton.MENU), title, active, icon, hovered == WindowButton.MENU);

        // Title, left aligned after the icon
        Rectangle tb = metrics.getTitleBarBounds(width);
        Font font = new Font(Font.SANS_SERIF, active ? Font.BOLD : Font.PLAIN, 13);
        g.setFont(font);
        g.setColor(active ? ink : t.textInactive());
        FontMetrics fm = g.getFontMetrics();
        String text = ellipsize(title == null ? "" : title, fm, tb.width);
        g.drawString(text, tb.x, (tbh - fm.getHeight()) / 2 + fm.getAscent());

        for (WindowButton b : new WindowButton[]{WindowButton.MINIMIZE, WindowButton.MAXIMIZE, WindowButton.CLOSE}) {
            paintButton(g, metrics.getButtonBounds(width, b), b, active, hovered == b, pressed == b, base, ink, t);
        }
    }
    // --8<-- [end:paint]

    private void paintIcon(Graphics2D g, Rectangle r, String title, boolean active, BufferedImage icon, boolean hovered) {
        DesktopTheme t = t();
        if (hovered) {
            g.setColor(mix(t.base(), t.textActive(), 0.14));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        }
        int size = 18;
        int x = r.x + (r.width - size) / 2;
        int y = r.y + (r.height - size) / 2;
        if (icon != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            java.awt.Composite old = g.getComposite();
            if (!active) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.55f));
            g.drawImage(icon, x, y, size, size, null);
            g.setComposite(old);
            return;
        }
        // No icon: a small tile in the accent colour with the title's first letter
        g.setColor(active ? t.activeTitleBg() : mix(t.base(), t.textActive(), 0.3));
        g.fillRoundRect(x, y, size, size, 6, 6);
        String letter = title == null || title.isBlank() ? "" : title.strip().substring(0, 1).toUpperCase();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(DesktopTheme.isDark(g.getColor()) ? Color.WHITE : new Color(0x15, 0x16, 0x1E));
        g.drawString(letter, x + (size - fm.stringWidth(letter)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
    }

    private static void paintButton(Graphics2D g, Rectangle r, WindowButton button, boolean active, boolean hovered,
                                    boolean pressed, Color base, Color ink, DesktopTheme t) {
        Color glyph = active ? mix(base, ink, 0.80) : t.textInactive();
        if (pressed || hovered) {
            if (button == WindowButton.CLOSE) {
                g.setColor(pressed ? CLOSE_PRESSED : CLOSE_HOT);
            } else {
                g.setColor(mix(base, ink, pressed ? 0.24 : 0.14));
            }
            g.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
            glyph = button == WindowButton.CLOSE ? Color.WHITE : ink;
        }
        g.setColor(glyph);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float cx = r.x + r.width / 2f;
        float cy = r.y + r.height / 2f;
        switch (button) {
            case MINIMIZE -> g.draw(new Line2D.Float(cx - 5, cy, cx + 5, cy));
            case MAXIMIZE -> g.draw(new RoundRectangle2D.Float(cx - 5, cy - 5, 10, 10, 4, 4));
            case CLOSE -> {
                g.draw(new Line2D.Float(cx - 4.5f, cy - 4.5f, cx + 4.5f, cy + 4.5f));
                g.draw(new Line2D.Float(cx - 4.5f, cy + 4.5f, cx + 4.5f, cy - 4.5f));
            }
            default -> {
            }
        }
    }

    private static String ellipsize(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) return text;
        String s = text;
        while (!s.isEmpty() && fm.stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "" : s + "…";
    }

    static Color mix(Color a, Color b, double weightOfB) {
        return new Color(
                (int) Math.round(a.getRed() * (1 - weightOfB) + b.getRed() * weightOfB),
                (int) Math.round(a.getGreen() * (1 - weightOfB) + b.getGreen() * weightOfB),
                (int) Math.round(a.getBlue() * (1 - weightOfB) + b.getBlue() * weightOfB));
    }
}
