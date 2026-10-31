package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

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
 * Vector icons for the controls of the Settings, drawn in the colours of the current theme so that they
 * work with every theme and need no icon files.
 */
final class SettingsIcons {
    static final int SIZE = 36;

    private SettingsIcons() {
    }

    /** The icon drawn at {@code size} pixels instead of {@link #SIZE}, e.g. for the sidebar. */
    static Icon of(SettingsCategory category, int size) {
        Icon full = of(category);
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.scale(size / (double) SIZE, size / (double) SIZE);
                full.paintIcon(c, g2, 0, 0);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    static Icon of(SettingsCategory category) {
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

    private static void paint(SettingsCategory category, Graphics2D g, DesktopTheme t, Color ink, boolean enabled) {
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
            case SOUND -> {
                // A loudspeaker with its sound waves.
                g.setColor(paper);
                Path2D cone = new Path2D.Double();
                cone.moveTo(4, 13);
                cone.lineTo(10, 13);
                cone.lineTo(18, 6);
                cone.lineTo(18, 30);
                cone.lineTo(10, 23);
                cone.lineTo(4, 23);
                cone.closePath();
                g.fill(cone);
                g.setColor(ink);
                g.draw(cone);
                g.setColor(enabled ? new Color(0x4A, 0x69, 0x84) : t.shadow());
                g.draw(new Arc2D.Double(16, 11, 10, 14, -50, 100, Arc2D.OPEN));
                g.draw(new Arc2D.Double(16, 6, 16, 24, -50, 100, Arc2D.OPEN));
            }
            case BLUETOOTH -> {
                g.setColor(enabled ? new Color(0x2E, 0x5C, 0xA8) : t.shadow());
                g.fillRoundRect(7, 2, 22, 32, 18, 18);
                g.setColor(enabled ? Color.WHITE : paper);
                g.setStroke(new BasicStroke(2.2f));
                Path2D rune = new Path2D.Double();
                rune.moveTo(12, 11);
                rune.lineTo(24, 23);
                rune.lineTo(18, 29);
                rune.lineTo(18, 7);
                rune.lineTo(24, 13);
                rune.lineTo(12, 25);
                g.draw(rune);
            }
            case PRINTERS -> {
                g.setColor(paper);
                g.fillRect(9, 3, 18, 11);
                g.setColor(ink);
                g.drawRect(9, 3, 18, 11);
                g.setColor(t.shadow());
                g.fillRoundRect(2, 12, 32, 14, 5, 5);
                g.setColor(ink);
                g.drawRoundRect(2, 12, 32, 14, 5, 5);
                g.setColor(paper);
                g.fillRect(9, 21, 18, 13);
                g.setColor(ink);
                g.drawRect(9, 21, 18, 13);
                g.drawLine(12, 25, 24, 25);
                g.drawLine(12, 29, 21, 29);
                g.setColor(enabled ? new Color(0x4A, 0xA0, 0x4A) : t.shadow());
                g.fillOval(27, 15, 4, 4);
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
            case NOTIFICATIONS -> {
                // A pop-up card with two lines of text, a bell on it and a red dot: a new notification.
                g.setColor(t.highlight());
                g.fillRect(3, 11, 26, 18);
                g.setColor(ink);
                g.drawRect(3, 11, 26, 18);
                g.drawLine(15, 18, 26, 18);
                g.drawLine(15, 23, 23, 23);
                g.setColor(enabled ? new Color(0xD8, 0xB0, 0x40) : t.shadow());
                Path2D small = new Path2D.Double();
                small.moveTo(6, 25);
                small.curveTo(7, 22, 7, 15, 10, 15);
                small.curveTo(13, 15, 13, 22, 14, 25);
                small.closePath();
                g.fill(small);
                g.setColor(ink);
                g.draw(small);
                g.fillOval(9, 25, 3, 3);
                g.setColor(enabled ? new Color(0xC0, 0x28, 0x28) : t.textInactive());
                g.fillOval(24, 4, 9, 9);
                g.setColor(ink);
                g.drawOval(24, 4, 9, 9);
            }
            case CLIPBOARD -> {
                // A clipboard with its clip and a sheet of text, a second sheet behind it: the history.
                g.setColor(t.shadow());
                g.fillRect(12, 4, 20, 26);
                g.setColor(enabled ? new Color(0xA0, 0x70, 0x40) : t.shadow());
                g.fillRect(5, 7, 22, 26);
                g.setColor(ink);
                g.drawRect(5, 7, 22, 26);
                g.setColor(paper);
                g.fillRect(8, 11, 16, 19);
                g.setColor(ink);
                for (int y = 16; y <= 26; y += 5) g.drawLine(11, y, y == 26 ? 17 : 21, y);
                g.setColor(enabled ? new Color(0xC0, 0xC0, 0xC8) : t.textInactive());
                g.fillRect(11, 4, 10, 5);
                g.setColor(ink);
                g.drawRect(11, 4, 10, 5);
            }
            case DISPLAY -> {
                // A monitor whose picture has arrows to its corners: the resolution.
                monitor(g, t, ink);
                g.setColor(enabled ? new Color(0x4A, 0x69, 0x84) : t.shadow());
                g.fillRect(6, 6, 24, 16);
                g.setColor(enabled ? Color.WHITE : t.textInactive());
                g.drawLine(10, 9, 26, 19);
                g.fillPolygon(new int[]{8, 14, 11}, new int[]{8, 9, 13}, 3);
                g.fillPolygon(new int[]{28, 22, 25}, new int[]{20, 19, 15}, 3);
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
            case POWER -> {
                // A battery on its side with a lightning bolt: power and sleeping.
                g.setColor(enabled ? new Color(0x5C, 0xA8, 0x4A) : t.shadow());
                g.fillRect(4, 10, 26, 16);
                g.setColor(ink);
                g.setStroke(new BasicStroke(1.5f));
                g.drawRect(4, 10, 26, 16);
                g.fillRect(30, 14, 3, 8);
                g.setColor(enabled ? new Color(0xF8, 0xE0, 0x40) : paper);
                Path2D bolt = new Path2D.Double();
                bolt.moveTo(19, 11);
                bolt.lineTo(12, 19);
                bolt.lineTo(17, 19);
                bolt.lineTo(15, 25);
                bolt.lineTo(22, 17);
                bolt.lineTo(17, 17);
                bolt.closePath();
                g.fill(bolt);
                g.setColor(ink);
                g.setStroke(new BasicStroke(1f));
                g.draw(bolt);
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
            case WORKSPACES -> {
                // CDE's four workspace buttons, two by two, in the theme's workspace colours.
                for (int i = 0; i < 4; i++) {
                    int cx = 3 + (i % 2) * 16;
                    int cy = 6 + (i / 2) * 13;
                    g.setColor(enabled ? t.workspaceColor(i) : t.shadow());
                    g.fillRect(cx, cy, 14, 11);
                    AmadeUI.paintBevel(g, cx, cy, 14, 11, i != 0, 1);
                }
            }
            case PANEL -> {
                // The Front Panel: a long raised bar with slots and a clock face in the first one.
                g.setColor(t.base());
                g.fillRect(1, 14, 34, 16);
                AmadeUI.paintBevel(g, 1, 14, 34, 16, true, 1);
                g.setColor(t.sunkenBg());
                for (int x = 4; x < 32; x += 7) g.fillRect(x, 18, 5, 9);
                g.setColor(paper);
                g.fillOval(4, 18, 6, 6);
                g.setColor(ink);
                g.drawOval(4, 18, 6, 6);
                g.setColor(enabled ? t.activeTitleBg() : t.shadow());
                g.fillRect(14, 8, 8, 6);
            }
            case NETWORK -> {
                // The way out: a computer, the proxy in between and the internet as a globe, linked.
                g.setColor(ink);
                g.drawLine(10, 24, 18, 18);
                g.drawLine(18, 18, 25, 11);
                g.setColor(paper);
                g.fillRect(3, 22, 12, 9);
                g.setColor(enabled ? new Color(0xD0, 0x80, 0x40) : t.shadow());
                g.fillRect(14, 14, 8, 8);
                g.setColor(enabled ? new Color(0x4A, 0x69, 0x84) : t.shadow());
                g.fillOval(21, 3, 12, 12);
                g.setColor(ink);
                g.drawRect(3, 22, 12, 9);
                g.drawLine(6, 33, 12, 33);
                g.drawRect(14, 14, 8, 8);
                g.drawOval(21, 3, 12, 12);
                g.drawLine(21, 9, 33, 9);
            }
            case USERS -> {
                // Two people, one behind the other: head and shoulders.
                g.setColor(t.shadow());
                g.fillOval(18, 5, 11, 11);
                g.fill(new Arc2D.Double(13, 16, 21, 20, 0, 180, Arc2D.CHORD));
                g.setColor(ink);
                g.drawOval(18, 5, 11, 11);
                Color person = enabled ? new Color(0x3F, 0x6E, 0xA8) : t.shadow();
                g.setColor(person);
                g.fillOval(6, 10, 13, 13);
                g.fill(new Arc2D.Double(1, 23, 23, 22, 0, 180, Arc2D.CHORD));
                g.setColor(ink);
                g.drawOval(6, 10, 13, 13);
                g.draw(new Arc2D.Double(1, 23, 23, 22, 0, 180, Arc2D.CHORD));
            }
            case PASSWORDS -> {
                // A key: a round bow with a hole, the shaft and two bits.
                g.setColor(enabled ? new Color(0xD8, 0xB0, 0x40) : t.shadow());
                g.fillOval(3, 9, 15, 15);
                g.fillRect(16, 14, 17, 5);
                g.fillRect(26, 19, 3, 6);
                g.fillRect(30, 19, 3, 4);
                g.setColor(ink);
                g.drawOval(3, 9, 15, 15);
                g.setColor(paper);
                g.fillOval(7, 14, 5, 5);
            }
            case UPDATES -> {
                // An arrow down into a tray, like the Front Panel's updates cell.
                g.setColor(enabled ? new Color(0x50, 0x90, 0x60) : t.shadow());
                Path2D arrow = new Path2D.Double();
                arrow.moveTo(14, 3);
                arrow.lineTo(22, 3);
                arrow.lineTo(22, 14);
                arrow.lineTo(28, 14);
                arrow.lineTo(18, 24);
                arrow.lineTo(8, 14);
                arrow.lineTo(14, 14);
                arrow.closePath();
                g.fill(arrow);
                g.setColor(ink);
                g.draw(arrow);
                g.drawLine(4, 22, 4, 31);
                g.drawLine(4, 31, 32, 31);
                g.drawLine(32, 31, 32, 22);
            }
            case LANGUAGE -> {
                // A globe with its meridian and two parallels.
                g.setColor(enabled ? new Color(0x4A, 0x69, 0x84) : t.shadow());
                g.fillOval(4, 4, 28, 28);
                g.setColor(enabled ? new Color(0x8F, 0xB0, 0x60) : t.sunkenBg());
                g.fillOval(9, 9, 9, 8);
                g.fillOval(17, 18, 10, 9);
                g.setColor(ink);
                g.drawOval(4, 4, 28, 28);
                g.drawOval(12, 4, 12, 28);
                g.drawLine(5, 13, 31, 13);
                g.drawLine(5, 23, 31, 23);
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
