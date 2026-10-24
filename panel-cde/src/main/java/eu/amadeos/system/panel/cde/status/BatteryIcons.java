package eu.amadeos.system.panel.cde.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Vector drawing for the battery cell, so it scales with the cell: a lying battery with its pole on the
 * right, filled to the charge in green, yellow or red, and a lightning bolt while it charges.
 */
final class BatteryIcons {
    static final Color GREEN = new Color(0x2E, 0x9E, 0x3E);
    static final Color YELLOW = new Color(0xE8, 0xB8, 0x1C);
    static final Color RED = new Color(0xD0, 0x28, 0x28);
    /** Above this the fill is green, at or below {@link #RED_PERCENT} red, in between yellow. */
    static final int YELLOW_PERCENT = 50;
    static final int RED_PERCENT = 20;

    private BatteryIcons() {
    }

    static Color fillColor(int percent) {
        if (percent > YELLOW_PERCENT) return GREEN;
        if (percent > RED_PERCENT) return YELLOW;
        return RED;
    }

    /**
     * The battery in a square {@code size} wide, outlined in {@code fg} with the empty part in {@code empty};
     * {@code bolt} adds the bolt of mains power, white with an {@code fg} rim so the fill shows around it.
     */
    static void paintBattery(Graphics2D g, double x, double y, double size, int percent, boolean bolt,
                             Color fg, Color empty) {
        double stroke = Math.max(1.5, size / 11);
        double bodyW = size * 0.80;
        double bodyH = size * 0.56;
        double bx = x + size * 0.04;
        double by = y + (size - bodyH) / 2;

        Rectangle2D body = new Rectangle2D.Double(bx, by, bodyW, bodyH);
        g.setColor(empty);
        g.fill(body);

        double inset = stroke * 1.25;
        double innerW = bodyW - 2 * inset;
        double fillW = innerW * Math.max(0, Math.min(100, percent)) / 100.0;
        // A sliver stays visible when the battery is nearly empty, so the red can be seen
        if (percent > 0) fillW = Math.max(fillW, Math.min(innerW, size / 10));
        g.setColor(fillColor(percent));
        g.fill(new Rectangle2D.Double(bx + inset, by + inset, fillW, bodyH - 2 * inset));

        g.setColor(fg);
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.draw(body);
        double poleH = bodyH * 0.42;
        g.fill(new Rectangle2D.Double(bx + bodyW + stroke / 2, by + (bodyH - poleH) / 2, size * 0.09, poleH));

        if (bolt) {
            double cx = bx + bodyW / 2;
            double top = y + size * 0.14;
            double bottom = y + size * 0.86;
            double half = size * 0.15;
            Path2D p = new Path2D.Double();
            p.moveTo(cx + half * 0.55, top);
            p.lineTo(cx - half, y + size * 0.54);
            p.lineTo(cx - half * 0.05, y + size * 0.54);
            p.lineTo(cx - half * 0.55, bottom);
            p.lineTo(cx + half, y + size * 0.46);
            p.lineTo(cx + half * 0.05, y + size * 0.46);
            p.closePath();
            g.setColor(Color.WHITE);
            g.fill(p);
            g.setColor(fg);
            g.setStroke(new BasicStroke((float) Math.max(1, stroke * 0.6), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.draw(p);
        }
    }
}
