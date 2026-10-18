package eu.amadeos.system.print.ui;

import eu.amadeos.system.print.cups.Printer;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/** A printer drawn in Java2D with a lamp for its state: green ready, amber printing, red paused. */
final class PrinterIcon {
    private static final int SIZE = 32;
    private final Map<Printer.State, Icon> icons = new EnumMap<>(Printer.State.class);

    Icon forState(Printer.State state) {
        return icons.computeIfAbsent(state, PrinterIcon::draw);
    }

    private static Icon draw(Printer.State state) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Paper coming out on top
        g.setColor(Color.WHITE);
        g.fillRect(9, 3, 14, 12);
        g.setColor(new Color(0x606060));
        g.drawRect(9, 3, 14, 12);
        g.setColor(new Color(0x9090A0));
        g.drawLine(11, 7, 21, 7);
        g.drawLine(11, 10, 19, 10);
        // Body with a bevel
        g.setColor(new Color(0xB8B8B8));
        g.fillRoundRect(2, 13, 28, 13, 4, 4);
        g.setColor(new Color(0xE8E8E8));
        g.drawLine(4, 14, 27, 14);
        g.setColor(new Color(0x505050));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(2, 13, 27, 13, 4, 4);
        // Paper tray
        g.setColor(new Color(0x808080));
        g.fillRect(7, 26, 18, 3);
        // Lamp
        g.setColor(switch (state) {
            case IDLE -> new Color(0x30C030);
            case PROCESSING -> new Color(0xE0A020);
            case STOPPED -> new Color(0xD03030);
            case UNKNOWN -> new Color(0x909090);
        });
        g.fillOval(23, 17, 5, 5);
        g.setColor(new Color(0x303030));
        g.drawOval(23, 17, 5, 5);
        g.dispose();
        return new ImageIcon(image);
    }
}
