package eu.amadeos.system.icons;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws one glyph on the 128-unit grid of the Horizon icons: lines with round ends in the current colour and
 * width, and filled shapes. The painter has already scaled the graphics to the size of the picture.
 */
public final class Pen {
    private final Graphics2D g;
    private final float minWidth;
    private Color color = Color.WHITE;
    private float width = 6;

    Pen(Graphics2D g, float minWidth) {
        this.g = g;
        this.minWidth = minWidth;
    }

    /** The colour of what follows. */
    public Pen color(Color c) {
        color = c;
        return this;
    }

    /** The colour with an opacity from 0 to 1. */
    public Pen color(Color c, double alpha) {
        color = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * alpha));
        return this;
    }

    /** The width of lines in grid units; never thinner than a pixel and a quarter. */
    public Pen width(float w) {
        width = w;
        return this;
    }

    public Color color() {
        return color;
    }

    public void stroke(Shape s) {
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(width, minWidth), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    public void fill(Shape s) {
        g.setColor(color);
        g.fill(s);
    }

    public void line(double x1, double y1, double x2, double y2) {
        stroke(new Line2D.Double(x1, y1, x2, y2));
    }

    /** A rounded rectangle; {@code r} is the radius of the corners. */
    public void rect(double x, double y, double w, double h, double r) {
        stroke(new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r));
    }

    public void fillRect(double x, double y, double w, double h, double r) {
        fill(new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r));
    }

    public void circle(double cx, double cy, double r) {
        stroke(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    public void dot(double cx, double cy, double r) {
        fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    /** An arc of a circle, angles in degrees, counter-clockwise from three o'clock as in {@link Arc2D}. */
    public void arc(double cx, double cy, double r, double start, double extent) {
        stroke(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, start, extent, Arc2D.OPEN));
    }

    /** Straight lines through the points x1, y1, x2, y2, ...; {@code closed} joins the last to the first. */
    public void poly(boolean closed, double... xy) {
        stroke(polygon(closed, xy));
    }

    public void fillPoly(double... xy) {
        fill(polygon(true, xy));
    }

    static Path2D polygon(boolean closed, double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        if (closed) p.closePath();
        return p;
    }

    public void path(String d) {
        stroke(parse(d));
    }

    public void fillPath(String d) {
        fill(parse(d));
    }

    /**
     * A path in a small part of the SVG path language, absolute coordinates only: {@code M x y}, {@code L x y},
     * {@code Q x1 y1 x y}, {@code C x1 y1 x2 y2 x y} and {@code Z}.
     */
    static Path2D parse(String d) {
        Path2D p = new Path2D.Double();
        String[] t = d.trim().replace(",", " ").replaceAll("([MLQCZ])", " $1 ").trim().split("\\s+");
        int i = 0;
        char cmd = 'M';
        while (i < t.length) {
            if (t[i].matches("[MLQCZ]")) cmd = t[i++].charAt(0);
            switch (cmd) {
                case 'M' -> {
                    p.moveTo(num(t[i]), num(t[i + 1]));
                    i += 2;
                    cmd = 'L';
                }
                case 'L' -> {
                    p.lineTo(num(t[i]), num(t[i + 1]));
                    i += 2;
                }
                case 'Q' -> {
                    p.quadTo(num(t[i]), num(t[i + 1]), num(t[i + 2]), num(t[i + 3]));
                    i += 4;
                }
                case 'C' -> {
                    p.curveTo(num(t[i]), num(t[i + 1]), num(t[i + 2]), num(t[i + 3]), num(t[i + 4]), num(t[i + 5]));
                    i += 6;
                }
                case 'Z' -> {
                    p.closePath();
                    if (i < t.length && !t[i].matches("[MLQCZ]")) throw new IllegalArgumentException("after Z: " + t[i]);
                }
                default -> throw new IllegalArgumentException("unknown command " + cmd);
            }
        }
        return p;
    }

    private static double num(String s) {
        return Double.parseDouble(s);
    }
}
