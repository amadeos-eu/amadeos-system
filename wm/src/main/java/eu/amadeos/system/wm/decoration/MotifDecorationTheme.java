package eu.amadeos.system.wm.decoration;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;

public final class MotifDecorationTheme implements DecorationTheme {
    public static final String NAME = "Motif";

    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    private final FrameMetrics metrics;
    private DesktopTheme theme;

    public MotifDecorationTheme() {
        this(FrameMetrics.defaultMotif(), ThemeManager.currentTheme());
    }

    public MotifDecorationTheme(FrameMetrics metrics) {
        this(metrics, ThemeManager.currentTheme());
    }

    public MotifDecorationTheme(FrameMetrics metrics, DesktopTheme theme) {
        this.metrics = metrics;
        this.theme = (theme != null) ? theme : ThemeManager.currentTheme();
    }

    public DesktopTheme theme() {
        return theme;
    }

    public void setTheme(DesktopTheme theme) {
        this.theme = (theme != null) ? theme : ThemeManager.currentTheme();
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
    public void paintFrame(Graphics2D g, int width, int height, String title, boolean active,
                           WindowButton hoveredButton, WindowButton pressedButton) {
        DesktopTheme t = (theme != null) ? theme : ThemeManager.currentTheme();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int bw = metrics.borderWidth();
        int tbh = metrics.titleBarHeight();

        // 1. Fill entire frame background with theme base color
        g.setColor(t.base());
        g.fillRect(0, 0, width, height);

        // 2. Draw outer 3D raised frame borders (2px bevel)
        draw3DBevel(g, 0, 0, width, height, true, 2);

        // 3. Draw inner sunken groove around the innerside of the outer border
        // Top and left: dark shadow; Bottom and right: light highlight
        g.setColor(t.darkShadow());
        g.drawLine(bw - 1, bw - 1, width - bw, bw - 1); // Top inner shadow
        g.drawLine(bw - 1, bw - 1, bw - 1, height - bw); // Left inner shadow
        g.setColor(t.highlight());
        g.drawLine(bw - 1, height - bw, width - bw, height - bw); // Bottom inner highlight
        g.drawLine(width - bw, bw - 1, width - bw, height - bw); // Right inner highlight

        // 4. Draw authentic Motif corner tabs (segmented 3D joint lines aligned with button sides)
        drawCornerJoints(g, width, height, bw, tbh);

        // 4. Draw Titlebar (raised 3D surface on the same level as the buttons)
        Rectangle tb = metrics.getTitleBarBounds(width);
        g.setColor(active ? t.activeTitleBg() : t.inactiveTitleBg());
        g.fillRect(tb.x, tb.y, tb.width, tb.height);
        draw3DBevel(g, tb.x, tb.y, tb.width, tb.height, true, 2);

        // 5. Title Text (centered inside sunken title channel)
        g.setFont(TITLE_FONT);
        g.setColor(active ? t.textActive() : t.textInactive());
        FontMetrics fm = g.getFontMetrics();

        int maxTextWidth = Math.max(0, tb.width - 16);
        String displayTitle = title != null ? title : "";
        if (fm.stringWidth(displayTitle) > maxTextWidth) {
            while (!displayTitle.isEmpty() && fm.stringWidth(displayTitle + "...") > maxTextWidth) {
                displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
            }
            displayTitle = displayTitle + "...";
        }

        int titleStrWidth = fm.stringWidth(displayTitle);
        int textX = tb.x + Math.max(0, (tb.width - titleStrWidth) / 2);
        int textY = tb.y + ((tb.height - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(displayTitle, textX, textY);

        // 6. Draw Buttons with 3D Beveled Glyphs
        for (WindowButton button : WindowButton.values()) {
            Rectangle b = metrics.getButtonBounds(width, button);
            boolean isHovered = (hoveredButton == button);
            boolean isPressed = (pressedButton == button);

            // Button background
            Color btnBg = t.base();
            if (isHovered && button == WindowButton.CLOSE) {
                btnBg = new Color(0xDB, 0x82, 0x82); // soft red tint on close hover
            } else if (isHovered) {
                btnBg = t.softHighlight();
            }
            g.setColor(btnBg);
            g.fillRect(b.x, b.y, b.width, b.height);

            // 3D bevel on button (inverted if pressed)
            draw3DBevel(g, b.x, b.y, b.width, b.height, !isPressed, 2);

            // Center coordinates for glyphs
            int cx = b.x + (b.width / 2);
            int cy = b.y + (b.height / 2);
            if (isPressed) {
                cx += 1;
                cy += 1;
            }

            drawButtonGlyph(g, button, cx, cy, isHovered, isPressed);
        }

        // 8. Horizontal separator groove below title row
        g.setColor(t.highlight());
        g.drawLine(bw, tbh, width - bw - 1, tbh);

        // 9. Inner sunken bevel around client area
        int clientX = bw;
        int clientY = tbh;
        int clientW = width - (2 * bw);
        int clientH = height - tbh - bw;
        draw3DBevel(g, clientX, clientY, clientW, clientH, false, 1);
    }

    private void drawButtonGlyph(Graphics2D g, WindowButton button, int cx, int cy, boolean isHovered, boolean isPressed) {
        DesktopTheme t = (theme != null) ? theme : ThemeManager.currentTheme();
        switch (button) {
            case MENU -> {
                // Authentic Motif Menu Glyph: 3D recessed horizontal bar / slot
                int gw = 12;
                int gh = 4;
                int gx = cx - (gw / 2);
                int gy = cy - (gh / 2);

                // Recessed 3D bevel around slot
                g.setColor(t.darkShadow());
                g.drawLine(gx, gy, gx + gw - 1, gy); // top
                g.drawLine(gx, gy, gx, gy + gh - 1); // left

                g.setColor(t.highlight());
                g.drawLine(gx, gy + gh - 1, gx + gw - 1, gy + gh - 1); // bottom
                g.drawLine(gx + gw - 1, gy, gx + gw - 1, gy + gh - 1); // right

                // Interior fill
                g.setColor(isPressed ? t.darkShadow() : t.shadow());
                g.fillRect(gx + 1, gy + 1, gw - 2, gh - 2);
            }
            case MINIMIZE -> {
                // Authentic Motif Minimize Glyph: Small 3D beveled square tile
                int sw = 6;
                int sh = 6;
                int sx = cx - (sw / 2);
                int sy = cy - (sh / 2);

                // 3D raised mini-square
                g.setColor(t.highlight());
                g.drawLine(sx, sy, sx + sw - 1, sy); // top
                g.drawLine(sx, sy, sx, sy + sh - 1); // left

                g.setColor(t.darkShadow());
                g.drawLine(sx, sy + sh - 1, sx + sw - 1, sy + sh - 1); // bottom
                g.drawLine(sx + sw - 1, sy, sx + sw - 1, sy + sh - 1); // right

                g.setColor(t.base());
                g.fillRect(sx + 1, sy + 1, sw - 2, sh - 2);
            }
            case MAXIMIZE -> {
                // Authentic Motif Maximize Glyph: Large 3D beveled square frame
                int fw = 12;
                int fh = 12;
                int fx = cx - (fw / 2);
                int fy = cy - (fh / 2);

                // Outer 3D bevel (sunken frame)
                g.setColor(t.darkShadow());
                g.drawLine(fx, fy, fx + fw - 1, fy); // top outer
                g.drawLine(fx, fy, fx, fy + fh - 1); // left outer

                g.setColor(t.highlight());
                g.drawLine(fx, fy + fh - 1, fx + fw - 1, fy + fh - 1); // bottom outer
                g.drawLine(fx + fw - 1, fy, fx + fw - 1, fy + fh - 1); // right outer

                // Inner 3D bevel (raised interior)
                int ix = fx + 2;
                int iy = fy + 2;
                int iw = fw - 4;
                int ih = fh - 4;

                g.setColor(t.highlight());
                g.drawLine(ix, iy, ix + iw - 1, iy); // top inner
                g.drawLine(ix, iy, ix, iy + ih - 1); // left inner

                g.setColor(t.darkShadow());
                g.drawLine(ix, iy + ih - 1, ix + iw - 1, iy + ih - 1); // bottom inner
                g.drawLine(ix + iw - 1, iy, ix + iw - 1, iy + ih - 1); // right inner

                // Hollow center
                g.setColor(t.base());
                g.fillRect(ix + 1, iy + 1, iw - 2, ih - 2);
            }
            case CLOSE -> {
                // Close Glyph: 3D beveled cross
                Color hl = (isHovered) ? Color.WHITE : t.highlight();
                Color sh = (isHovered) ? Color.WHITE : t.darkShadow();

                // Main stroke with 3D highlight/shadow pairs
                g.setColor(sh);
                g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);

                g.setColor(hl);
                g.drawLine(cx - 3, cy - 4, cx + 4, cy + 3);
                g.drawLine(cx - 3, cy + 4, cx + 4, cy - 3);
            }
        }
    }

    private void drawCornerJoints(Graphics2D g, int width, int height, int bw, int tbh) {
        int cornerLeft = metrics.borderWidth() + metrics.buttonSize();
        // Right notch aligns with the most right button (one button width)
        int cornerRight = width - metrics.borderWidth() - metrics.buttonSize();

        // Vertical 3D notch joints on top border:
        // Top-left notch aligns with right edge of MENU button:
        drawNotchLine(g, cornerLeft - 1, 0, cornerLeft - 1, bw - 1, true);
        // Top-right notch aligns with left edge of the most right button:
        drawNotchLine(g, cornerRight - 1, 0, cornerRight - 1, bw - 1, true);

        // Vertical 3D notch joints on bottom border:
        drawNotchLine(g, cornerLeft - 1, height - bw, cornerLeft - 1, height - 1, true);
        drawNotchLine(g, cornerRight - 1, height - bw, cornerRight - 1, height - 1, true);

        // Horizontal 3D notch joints on left border:
        // Top-left notch aligns with bottom edge of title bar / buttons:
        drawNotchLine(g, 0, tbh - 1, bw - 1, tbh - 1, false);
        // Bottom-left notch aligns symmetrically from bottom:
        drawNotchLine(g, 0, height - tbh - 1, bw - 1, height - tbh - 1, false);

        // Horizontal 3D notch joints on right border:
        // Top-right notch aligns with bottom edge of title bar / buttons:
        drawNotchLine(g, width - bw, tbh - 1, width - 1, tbh - 1, false);
        // Bottom-right notch aligns symmetrically from bottom:
        drawNotchLine(g, width - bw, height - tbh - 1, width - 1, height - tbh - 1, false);
    }

    private void drawNotchLine(Graphics2D g, int x1, int y1, int x2, int y2, boolean vertical) {
        DesktopTheme t = (theme != null) ? theme : ThemeManager.currentTheme();
        g.setColor(t.darkShadow());
        g.drawLine(x1, y1, x2, y2);
        g.setColor(t.highlight());
        if (vertical) {
            g.drawLine(x1 + 1, y1, x2 + 1, y2);
        } else {
            g.drawLine(x1, y1 + 1, x2, y2 + 1);
        }
    }

    private void draw3DBevel(Graphics2D g, int x, int y, int w, int h, boolean raised, int thickness) {
        DesktopTheme t = (theme != null) ? theme : ThemeManager.currentTheme();
        Color topLeft1 = raised ? t.highlight() : t.darkShadow();
        Color topLeft2 = raised ? t.softHighlight() : t.shadow();
        Color bottomRight1 = raised ? t.darkShadow() : t.highlight();
        Color bottomRight2 = raised ? t.shadow() : t.softHighlight();

        for (int i = 0; i < thickness; i++) {
            Color tl = (i == 0) ? topLeft1 : topLeft2;
            Color br = (i == 0) ? bottomRight1 : bottomRight2;

            g.setColor(tl);
            g.drawLine(x + i, y + i, x + w - 1 - i, y + i); // Top
            g.drawLine(x + i, y + i, x + i, y + h - 1 - i); // Left

            g.setColor(br);
            g.drawLine(x + i, y + h - 1 - i, x + w - 1 - i, y + h - 1 - i); // Bottom
            g.drawLine(x + w - 1 - i, y + i, x + w - 1 - i, y + h - 1 - i); // Right
        }
    }
}
