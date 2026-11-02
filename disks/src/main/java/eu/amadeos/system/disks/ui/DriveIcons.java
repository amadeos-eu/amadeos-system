package eu.amadeos.system.disks.ui;

import eu.amadeos.system.disks.model.Drive;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

/** The drive pictures of the drive list, drawn so they look the same in every icon theme. */
final class DriveIcons {
    private DriveIcons() {
    }

    static Icon of(Drive.Kind kind, int size) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(x, y);
                g.scale(size / 32.0, size / 32.0);
                paint(g, kind);
                g.dispose();
            }
        };
    }

    private static void paint(Graphics2D g, Drive.Kind kind) {
        switch (kind) {
            case SYSTEM, INTERNAL -> {
                g.setColor(new Color(0x30, 0x34, 0x3A));
                g.fillRoundRect(2, 10, 28, 12, 3, 3);
                g.setColor(new Color(0xD8, 0xB0, 0x40));
                for (int i = 0; i < 6; i++) g.fillRect(4 + i * 2, 22, 1, 3);
                g.setColor(Color.WHITE);
                g.fillRect(18, 13, 9, 6);
                if (kind == Drive.Kind.SYSTEM) {
                    g.setColor(new Color(0x2E, 0x7D, 0x32));
                    g.fillOval(4, 12, 6, 6);
                }
            }
            case EXTERNAL -> {
                g.setColor(new Color(0x45, 0x4A, 0x52));
                g.fillRoundRect(5, 4, 22, 24, 5, 5);
                g.setColor(new Color(0x80, 0x88, 0x90));
                g.fillOval(11, 9, 10, 10);
                g.setColor(new Color(0x30, 0x80, 0xD0));
                g.fillOval(14, 23, 4, 2);
            }
            case STICK -> {
                g.setColor(new Color(0xB0, 0x30, 0x30));
                g.fillRoundRect(3, 11, 19, 11, 4, 4);
                g.setColor(new Color(0xC0, 0xC0, 0xC0));
                g.fillRect(22, 12, 8, 9);
                g.setColor(Color.DARK_GRAY);
                g.fillRect(24, 14, 2, 2);
                g.fillRect(27, 14, 2, 2);
            }
            case CARD -> {
                Polygon p = new Polygon(new int[]{8, 20, 24, 24, 8}, new int[]{4, 4, 8, 28, 28}, 5);
                g.setColor(new Color(0x2F, 0x5D, 0x9A));
                g.fill(p);
                g.setColor(new Color(0xD8, 0xB0, 0x40));
                for (int i = 0; i < 4; i++) g.fillRect(10 + i * 3, 6, 2, 4);
            }
            case DISC -> {
                g.setColor(new Color(0xB8, 0xB8, 0xC0));
                g.fillOval(3, 3, 26, 26);
                g.setColor(new Color(0xE8, 0xE8, 0xF0));
                g.fillOval(10, 10, 12, 12);
                g.setColor(new Color(0x60, 0x60, 0x68));
                g.fillOval(13, 13, 6, 6);
                g.setStroke(new BasicStroke(1));
                g.drawOval(3, 3, 26, 26);
            }
            case IMAGE -> {
                g.setColor(new Color(0xF4, 0xF2, 0xEC));
                g.fillRect(7, 3, 18, 26);
                g.setColor(new Color(0x70, 0x70, 0x70));
                g.drawRect(7, 3, 18, 26);
                g.setColor(new Color(0x30, 0x34, 0x3A));
                g.fillRoundRect(10, 16, 12, 7, 2, 2);
            }
        }
    }
}
