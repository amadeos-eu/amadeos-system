package eu.amadeos.system.panel.cde.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** The Bluetooth rune of the status cell, drawn in any size. */
final class BluetoothIcons {
    /** Bluetooth's blue, for the rune while it is switched on. */
    static final Color BLUE = new Color(0x2E, 0x5C, 0xA8);

    private BluetoothIcons() {
    }

    /**
     * The rune in a square of {@code size} at x, y; with {@code connected} devices a dot for each (up to three)
     * along the bottom right.
     */
    static void paintRune(Graphics2D g, double x, double y, double size, Color ink, int connected) {
        double s = size / 20.0;
        g.setColor(ink);
        g.setStroke(new BasicStroke((float) Math.max(1.2, 1.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D rune = new Path2D.Double();
        rune.moveTo(x + 5 * s, y + 6 * s);
        rune.lineTo(x + 14 * s, y + 14 * s);
        rune.lineTo(x + 9.5 * s, y + 18 * s);
        rune.lineTo(x + 9.5 * s, y + 2 * s);
        rune.lineTo(x + 14 * s, y + 6 * s);
        rune.lineTo(x + 5 * s, y + 14 * s);
        g.draw(rune);
        double d = 3 * s;
        for (int i = 0; i < Math.min(3, connected); i++) {
            g.fill(new Ellipse2D.Double(x + 16 * s, y + (16 - i * 4.5) * s, d, d));
        }
    }
}
