package org.datazuul.amadeos.style;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

/**
 * Vector icons for the Style Manager controls, drawn in the colours of the current theme so that they
 * work with every theme and need no icon files.
 */
final class StyleIcons {
    static final int SIZE = 36;

    private StyleIcons() {
    }

    static Icon of(StyleCategory category) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                DesktopTheme t = AmadeUI.theme();
                boolean enabled = c == null || c.isEnabled();
                Color ink = enabled ? t.textActive() : t.textInactive();
                g2.setStroke(new BasicStroke(1.6f));
                paint(category, g2, t, ink, enabled);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        };
    }

    private static void paint(StyleCategory category, Graphics2D g, DesktopTheme t, Color ink, boolean enabled) {
        Color paper = t.fieldBackground();
        switch (category) {
            case COLOR -> {
                Color[] chips = enabled
                        ? new Color[]{t.activeTitleBg(), new Color(0xC8, 0x50, 0x40), new Color(0xD8, 0xB0, 0x40), new Color(0x50, 0x90, 0x60)}
                        : new Color[]{t.shadow(), t.shadow(), t.shadow(), t.shadow()};
                for (int i = 0; i < 4; i++) {
                    int cx = 4 + (i % 2) * 15;
                    int cy = 4 + (i / 2) * 15;
                    g.setColor(chips[i]);
                    g.fillRect(cx, cy, 13, 13);
                    AmadeUI.paintBevel(g, cx, cy, 13, 13, true, 1);
                }
            }
            case FONT -> {
                g.setColor(ink);
                g.setFont(new Font(Font.SERIF, Font.BOLD, 26));
                g.drawString("A", 4, 28);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                g.drawString("a", 22, 28);
            }
            case BACKDROP -> {
                monitor(g, t, ink);
                g.setColor(enabled ? new Color(0x4A, 0x69, 0x84) : t.shadow());
                g.fillRect(6, 6, 24, 16);
                g.setColor(enabled ? new Color(0x8F, 0xB0, 0x60) : t.sunkenBg());
                Path2D hill = new Path2D.Double();
                hill.moveTo(6, 22);
                hill.lineTo(14, 13);
                hill.lineTo(20, 18);
                hill.lineTo(24, 15);
                hill.lineTo(30, 22);
                hill.closePath();
                g.fill(hill);
            }
            case KEYBOARD -> {
                g.setColor(paper);
                g.fillRoundRect(2, 10, 32, 17, 4, 4);
                g.setColor(ink);
                g.drawRoundRect(2, 10, 32, 17, 4, 4);
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 6; col++) g.drawRect(5 + col * 5, 13 + row * 5, 3, 3);
                }
                g.drawLine(9, 24, 27, 24);
            }
            case MOUSE -> {
                g.setColor(paper);
                g.fillRoundRect(10, 6, 16, 26, 14, 14);
                g.setColor(ink);
                g.drawRoundRect(10, 6, 16, 26, 14, 14);
                g.drawLine(10, 16, 26, 16);
                g.drawLine(18, 6, 18, 16);
                g.drawLine(18, 6, 18, 1);
            }
            case BEEP -> {
                g.setColor(enabled ? new Color(0xD8, 0xB0, 0x40) : t.shadow());
                Path2D bell = new Path2D.Double();
                bell.moveTo(9, 26);
                bell.curveTo(11, 22, 10, 8, 18, 7);
                bell.curveTo(26, 8, 25, 22, 27, 26);
                bell.closePath();
                g.fill(bell);
                g.setColor(ink);
                g.draw(bell);
                g.fillOval(15, 26, 6, 5);
            }
            case SCREEN -> {
                monitor(g, t, ink);
                g.setColor(Color.BLACK);
                g.fillRect(6, 6, 24, 16);
                g.setColor(enabled ? new Color(0xF2, 0xE2, 0x9A) : t.textInactive());
                g.fill(new Arc2D.Double(12, 8, 10, 10, 60, 240, Arc2D.CHORD));
                g.setColor(Color.BLACK);
                g.fillOval(15, 7, 9, 9);
                g.setColor(enabled ? Color.WHITE : t.textInactive());
                g.fillRect(25, 9, 1, 1);
                g.fillRect(22, 18, 1, 1);
            }
            case WINDOW -> {
                g.setColor(paper);
                g.fillRect(4, 6, 28, 24);
                g.setColor(enabled ? t.activeTitleBg() : t.shadow());
                g.fillRect(4, 6, 28, 6);
                g.setColor(ink);
                g.drawRect(4, 6, 28, 24);
                g.drawRect(26, 7, 4, 4);
            }
            case STARTUP -> {
                g.setColor(ink);
                g.setStroke(new BasicStroke(2.4f));
                g.draw(new Arc2D.Double(7, 8, 22, 22, 120, 300, Arc2D.OPEN));
                g.drawLine(18, 5, 18, 17);
            }
        }
    }

    private static void monitor(Graphics2D g, DesktopTheme t, Color ink) {
        g.setColor(t.sunkenBg());
        g.fillRect(3, 3, 30, 22);
        AmadeUI.paintBevel(g, 3, 3, 30, 22, true, 2);
        g.setColor(ink);
        g.fillRect(14, 26, 8, 3);
        g.fillRect(10, 29, 16, 3);
    }
}
