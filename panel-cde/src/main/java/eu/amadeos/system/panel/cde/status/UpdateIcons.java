package eu.amadeos.system.panel.cde.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Vector drawings for the updates cell, so they scale with the cell and follow the theme colors: a downward
 * arrow into a tray, the number of updates on a round badge and a thin progress bar.
 */
final class UpdateIcons {
    private UpdateIcons() {
    }

    /** Arrow pointing down into an open tray: "something to install". */
    static void paintTray(Graphics2D g, double x, double y, double size, Color fg) {
        double stroke = Math.max(1.5, size / 9);
        g.setColor(fg);
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Tray: a U open at the top
        Path2D tray = new Path2D.Double();
        tray.moveTo(x + size * 0.14, y + size * 0.58);
        tray.lineTo(x + size * 0.14, y + size * 0.86);
        tray.lineTo(x + size * 0.86, y + size * 0.86);
        tray.lineTo(x + size * 0.86, y + size * 0.58);
        g.draw(tray);
        // Arrow: shaft and head
        double cx = x + size / 2;
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(cx, y + size * 0.12);
        arrow.lineTo(cx, y + size * 0.66);
        arrow.moveTo(cx - size * 0.2, y + size * 0.46);
        arrow.lineTo(cx, y + size * 0.66);
        arrow.lineTo(cx + size * 0.2, y + size * 0.46);
        g.draw(arrow);
    }

    /** The number (99+ above 99) on a round badge in the upper right corner of a cell {@code cellWidth} wide. */
    static void paintBadge(Graphics2D g, int cellWidth, double size, int count, Color bg, Color fg) {
        String text = count > 99 ? "99+" : Integer.toString(count);
        Font font = g.getFont().deriveFont(Font.BOLD, (float) Math.max(7, size * 0.42));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        double h = fm.getAscent() + 2;
        double w = Math.max(h, fm.stringWidth(text) + 4);
        double bx = cellWidth - w - 1;
        double by = 1;
        g.setColor(bg);
        if (w == h) {
            g.fill(new Ellipse2D.Double(bx, by, w, h));
        } else {
            g.fill(new RoundRectangle2D.Double(bx, by, w, h, h, h));
        }
        g.setColor(fg);
        g.drawString(text, (float) (bx + (w - fm.stringWidth(text)) / 2), (float) (by + fm.getAscent()));
    }

    /**
     * A bar along the bottom of the cell; {@code percent} below 0 means "busy, no measure", drawn as a
     * half-filled bar.
     */
    static void paintProgress(Graphics2D g, double x, double y, double w, double h, int percent, Color on, Color off) {
        g.setColor(off);
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(on);
        if (percent < 0) {
            g.fill(new Rectangle2D.Double(x + w / 4, y, w / 2, h));
        } else {
            g.fill(new Rectangle2D.Double(x, y, w * Math.min(100, percent) / 100.0, h));
        }
    }
}
