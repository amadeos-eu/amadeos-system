package org.datazuul.dazeos.wm.decoration;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

public final class MotifDecorationTheme implements DecorationTheme {
    public static final String NAME = "Motif";

    private static final Color BG_GRAY = new Color(0xC0, 0xC0, 0xC0);
    private static final Color HIGHLIGHT = Color.WHITE;
    private static final Color SHADOW = new Color(0x80, 0x80, 0x80);
    private static final Color DARK_SHADOW = new Color(0x30, 0x30, 0x30);

    private static final Color ACTIVE_TITLE_BG = new Color(0x4A, 0x6B, 0x82); // Classic Motif slate blue
    private static final Color INACTIVE_TITLE_BG = new Color(0x9A, 0x9A, 0x9A); // Muted gray
    private static final Color ACTIVE_TITLE_TEXT = Color.WHITE;
    private static final Color INACTIVE_TITLE_TEXT = new Color(0xDD, 0xDD, 0xDD);

    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    private final FrameMetrics metrics;

    public MotifDecorationTheme() {
        this(FrameMetrics.defaultMotif());
    }

    public MotifDecorationTheme(FrameMetrics metrics) {
        this.metrics = metrics;
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
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int bw = metrics.borderWidth();
        int tbh = metrics.titleBarHeight();

        // 1. Fill entire frame background with workstation gray
        g.setColor(BG_GRAY);
        g.fillRect(0, 0, width, height);

        // 2. Draw 3D outer raised frame borders
        draw3DBevel(g, 0, 0, width, height, true, 2);

        // 3. Draw corner grab brackets (visual Motif corner markers)
        int cornerLen = bw * 3;
        g.setColor(SHADOW);
        g.drawLine(cornerLen, 0, cornerLen, bw);
        g.drawLine(0, cornerLen, bw, cornerLen);
        g.drawLine(width - cornerLen, 0, width - cornerLen, bw);
        g.drawLine(width - bw, cornerLen, width, cornerLen);
        g.drawLine(0, height - cornerLen, bw, height - cornerLen);
        g.drawLine(cornerLen, height - bw, cornerLen, height);
        g.drawLine(width - cornerLen, height - bw, width - cornerLen, height);
        g.drawLine(width - bw, height - cornerLen, width, height - cornerLen);

        // 4. Draw Titlebar
        int tbX = bw + 1;
        int tbY = bw + 1;
        int tbW = width - (2 * bw) - 2;
        int tbH = tbh - bw - 1;

        g.setColor(active ? ACTIVE_TITLE_BG : INACTIVE_TITLE_BG);
        g.fillRect(tbX, tbY, tbW, tbH);

        // 3D recessed bevel around title bar
        draw3DBevel(g, tbX, tbY, tbW, tbH, false, 1);

        // 5. Title Text
        g.setFont(TITLE_FONT);
        g.setColor(active ? ACTIVE_TITLE_TEXT : INACTIVE_TITLE_TEXT);
        FontMetrics fm = g.getFontMetrics();

        Rectangle menuBounds = metrics.getButtonBounds(width, WindowButton.MENU);
        Rectangle minBounds = metrics.getButtonBounds(width, WindowButton.MINIMIZE);

        int textLeft = menuBounds.x + menuBounds.width + 8;
        int textRight = minBounds.x - 8;
        int maxTextWidth = Math.max(0, textRight - textLeft);

        String displayTitle = title != null ? title : "";
        if (fm.stringWidth(displayTitle) > maxTextWidth) {
            while (!displayTitle.isEmpty() && fm.stringWidth(displayTitle + "...") > maxTextWidth) {
                displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
            }
            displayTitle = displayTitle + "...";
        }

        int textY = tbY + ((tbH - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(displayTitle, textLeft, textY);

        // 6. Draw Buttons
        for (WindowButton button : WindowButton.values()) {
            Rectangle b = metrics.getButtonBounds(width, button);
            boolean isHovered = (hoveredButton == button);
            boolean isPressed = (pressedButton == button);

            // Button background
            Color btnBg = BG_GRAY;
            if (isHovered && button == WindowButton.CLOSE) {
                btnBg = new Color(0xE0, 0x70, 0x70); // subtle red tint on close hover
            } else if (isHovered) {
                btnBg = new Color(0xD8, 0xD8, 0xD8);
            }
            g.setColor(btnBg);
            g.fillRect(b.x, b.y, b.width, b.height);

            // 3D bevel (inverted if pressed)
            draw3DBevel(g, b.x, b.y, b.width, b.height, !isPressed, 2);

            // Button Glyph
            g.setColor(DARK_SHADOW);
            int cx = b.x + (b.width / 2);
            int cy = b.y + (b.height / 2);
            if (isPressed) {
                cx += 1;
                cy += 1;
            }

            switch (button) {
                case MENU -> {
                    // Modernized Motif menu button: double horizontal bar
                    g.drawLine(cx - 5, cy - 2, cx + 5, cy - 2);
                    g.drawLine(cx - 5, cy + 2, cx + 5, cy + 2);
                }
                case MINIMIZE -> {
                    // Minimize: horizontal bar at bottom
                    g.fillRect(cx - 4, cy + 3, 9, 3);
                }
                case MAXIMIZE -> {
                    // Maximize: square outline
                    g.drawRect(cx - 5, cy - 5, 10, 10);
                    g.drawLine(cx - 5, cy - 4, cx + 5, cy - 4); // thicker top bar
                }
                case CLOSE -> {
                    // Close: crisp cross
                    if (isHovered) {
                        g.setColor(Color.WHITE);
                    }
                    g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                    g.drawLine(cx - 3, cy - 4, cx + 4, cy + 3);
                    g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
                    g.drawLine(cx - 3, cy + 4, cx + 4, cy - 3);
                }
            }
        }

        // 7. Inner sunken bevel around client area
        int clientX = bw;
        int clientY = tbh;
        int clientW = width - (2 * bw);
        int clientH = height - tbh - bw;
        draw3DBevel(g, clientX - 1, clientY - 1, clientW + 2, clientH + 2, false, 1);
    }

    private void draw3DBevel(Graphics2D g, int x, int y, int w, int h, boolean raised, int thickness) {
        Color topLeft1 = raised ? HIGHLIGHT : DARK_SHADOW;
        Color topLeft2 = raised ? BG_GRAY : SHADOW;
        Color bottomRight1 = raised ? DARK_SHADOW : HIGHLIGHT;
        Color bottomRight2 = raised ? SHADOW : BG_GRAY;

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
