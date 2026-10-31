package eu.amadeos.system.panel.cde.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** The bell of the notification cell, in the line style of the other status cells. */
final class NotificationIcons {
    static final Color BELL = new Color(0xD8, 0xB0, 0x40);
    static final Color MUTED = new Color(0xB0, 0xA8, 0xA0);
    static final Color SLASH = new Color(0xC0, 0x28, 0x28);

    private NotificationIcons() {
    }

    /** A bell filled in brass, or in grey with a red slash while Do not disturb is on. */
    static void paintBell(Graphics2D g, double x, double y, double size, Color fg, boolean doNotDisturb) {
        double stroke = Math.max(1.2, size / 14);
        Path2D bell = new Path2D.Double();
        bell.moveTo(x + size * 0.16, y + size * 0.76);
        bell.curveTo(x + size * 0.26, y + size * 0.62, x + size * 0.22, y + size * 0.16, x + size * 0.5, y + size * 0.14);
        bell.curveTo(x + size * 0.78, y + size * 0.16, x + size * 0.74, y + size * 0.62, x + size * 0.84, y + size * 0.76);
        bell.closePath();
        g.setColor(doNotDisturb ? MUTED : BELL);
        g.fill(bell);
        g.setColor(fg);
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(bell);
        double clapper = size * 0.16;
        g.fill(new Ellipse2D.Double(x + size * 0.5 - clapper / 2, y + size * 0.78, clapper, clapper));
        double knob = size * 0.1;
        g.fill(new Ellipse2D.Double(x + size * 0.5 - knob / 2, y + size * 0.05, knob, knob));
        if (doNotDisturb) {
            g.setColor(SLASH);
            g.setStroke(new BasicStroke((float) (stroke * 1.4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x + size * 0.12, y + size * 0.9, x + size * 0.88, y + size * 0.08));
        }
    }
}
