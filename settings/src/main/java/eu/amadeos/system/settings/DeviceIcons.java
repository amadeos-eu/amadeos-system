package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.bluetooth.BluetoothDevice;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.util.function.Consumer;

/**
 * Small icons (20 pixels) for the lists of the Network, Bluetooth and Printers pages, drawn in the colours of the
 * current theme like {@link SettingsIcons}.
 */
final class DeviceIcons {
    static final int SIZE = 20;

    private DeviceIcons() {
    }

    /** WLAN signal as four bars, the strong ones filled, with a small lock for a secured network. */
    static Icon wifi(int bars, boolean secured) {
        return icon(g -> {
            DesktopTheme t = AmadeUI.theme();
            for (int i = 0; i < 4; i++) {
                int h = 4 + i * 4;
                int x = 1 + i * 4;
                g.setColor(i < bars ? t.textActive() : t.shadow());
                g.fillRect(x, 18 - h, 3, h);
            }
            if (secured) {
                g.setColor(t.textActive());
                g.setStroke(new BasicStroke(1.2f));
                g.drawArc(14, 1, 5, 6, 0, 180);
                g.fillRect(13, 4, 7, 5);
            }
        });
    }

    /** An Ethernet plug. */
    static Icon ethernet() {
        return icon(g -> {
            DesktopTheme t = AmadeUI.theme();
            g.setColor(t.fieldBackground());
            g.fillRect(3, 4, 14, 11);
            g.setColor(t.textActive());
            g.drawRect(3, 4, 14, 11);
            g.drawRect(7, 15, 6, 3);
            for (int x = 5; x <= 14; x += 3) g.drawLine(x, 6, x, 9);
        });
    }

    static Icon printer() {
        return icon(g -> {
            DesktopTheme t = AmadeUI.theme();
            g.setColor(t.fieldBackground());
            g.fillRect(5, 1, 10, 7);
            g.fillRect(5, 13, 10, 6);
            g.setColor(t.shadow());
            g.fillRoundRect(1, 7, 18, 8, 3, 3);
            g.setColor(t.textActive());
            g.drawRect(5, 1, 10, 7);
            g.drawRoundRect(1, 7, 18, 8, 3, 3);
            g.setColor(t.fieldBackground());
            g.fillRect(5, 13, 10, 6);
            g.setColor(t.textActive());
            g.drawRect(5, 13, 10, 6);
            g.setColor(new Color(0x4A, 0xA0, 0x4A));
            g.fillOval(15, 9, 3, 3);
        });
    }

    static Icon bluetooth(BluetoothDevice.Kind kind) {
        return icon(g -> {
            DesktopTheme t = AmadeUI.theme();
            Color ink = t.textActive();
            Color paper = t.fieldBackground();
            g.setStroke(new BasicStroke(1.5f));
            switch (kind) {
                case HEADPHONES -> {
                    g.setColor(ink);
                    g.draw(new Arc2D.Double(3, 2, 14, 14, 0, 180, Arc2D.OPEN));
                    g.fillRoundRect(2, 9, 4, 8, 2, 2);
                    g.fillRoundRect(14, 9, 4, 8, 2, 2);
                }
                case SPEAKER -> {
                    g.setColor(paper);
                    g.fillRect(4, 1, 12, 18);
                    g.setColor(ink);
                    g.drawRect(4, 1, 12, 18);
                    g.drawOval(7, 9, 6, 6);
                    g.fillOval(9, 3, 2, 2);
                }
                case KEYBOARD -> {
                    g.setColor(paper);
                    g.fillRoundRect(1, 5, 18, 10, 3, 3);
                    g.setColor(ink);
                    g.drawRoundRect(1, 5, 18, 10, 3, 3);
                    for (int x = 4; x < 17; x += 3) g.drawLine(x, 8, x, 8);
                    g.drawLine(6, 12, 14, 12);
                }
                case MOUSE -> {
                    g.setColor(paper);
                    g.fillRoundRect(5, 2, 10, 16, 9, 9);
                    g.setColor(ink);
                    g.drawRoundRect(5, 2, 10, 16, 9, 9);
                    g.drawLine(5, 8, 15, 8);
                    g.drawLine(10, 2, 10, 8);
                }
                case PHONE -> {
                    g.setColor(paper);
                    g.fillRoundRect(5, 1, 10, 18, 3, 3);
                    g.setColor(ink);
                    g.drawRoundRect(5, 1, 10, 18, 3, 3);
                    g.drawLine(9, 16, 11, 16);
                }
                case COMPUTER -> {
                    g.setColor(paper);
                    g.fillRect(3, 3, 14, 10);
                    g.setColor(ink);
                    g.drawRect(3, 3, 14, 10);
                    g.drawLine(1, 16, 19, 16);
                }
                case GAMEPAD -> {
                    g.setColor(paper);
                    g.fillRoundRect(1, 5, 18, 10, 8, 8);
                    g.setColor(ink);
                    g.drawRoundRect(1, 5, 18, 10, 8, 8);
                    g.drawLine(4, 10, 8, 10);
                    g.drawLine(6, 8, 6, 12);
                    g.fillOval(13, 8, 2, 2);
                    g.fillOval(15, 11, 2, 2);
                }
                case PRINTER -> printer().paintIcon(null, g, 0, 0);
                case OTHER -> rune(g, ink);
            }
        });
    }

    /** The Bluetooth rune, for devices of unknown kind. */
    static void rune(Graphics2D g, Color ink) {
        g.setColor(ink);
        Path2D p = new Path2D.Double();
        p.moveTo(5, 6);
        p.lineTo(15, 14);
        p.lineTo(10, 18);
        p.lineTo(10, 2);
        p.lineTo(15, 6);
        p.lineTo(5, 14);
        g.draw(p);
    }

    private static Icon icon(Consumer<Graphics2D> painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                painter.accept(g2);
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
}
