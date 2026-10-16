package org.datazuul.amadeos.wm.panel.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

/**
 * Vector drawings for the network cell and its menu, so they scale with the cell and follow the theme
 * colors: the WLAN fan with 0-4 lit bars, an Ethernet jack, a padlock for secured networks and a slash
 * for "not connected".
 */
final class NetworkIcons {
    private NetworkIcons() {
    }

    /** WLAN fan: a dot and three arcs; {@code bars} of them (1-4) in {@code on}, the rest in {@code off}. */
    static void paintWifi(Graphics2D g, double x, double y, double size, int bars, Color on, Color off) {
        double cx = x + size / 2;
        double cy = y + size * 0.86;
        double stroke = Math.max(1.5, size / 9);
        g.setColor(bars >= 1 ? on : off);
        double dot = stroke * 1.5;
        g.fill(new Ellipse2D.Double(cx - dot / 2, cy - dot / 2, dot, dot));
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int arc = 1; arc <= 3; arc++) {
            double r = size * 0.25 * arc;
            g.setColor(bars >= arc + 1 ? on : off);
            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 45, 90, Arc2D.OPEN));
        }
    }

    /** Front view of an RJ45 jack: body, latch notch below, contacts on top. */
    static void paintEthernet(Graphics2D g, double x, double y, double size, Color fg, Color bg) {
        double w = size * 0.70;
        double h = size * 0.50;
        double bx = x + (size - w) / 2;
        double by = y + size * 0.18;
        g.setColor(fg);
        g.fill(new RoundRectangle2D.Double(bx, by, w, h, size * 0.12, size * 0.12));
        double nw = w * 0.42;
        g.fill(new Rectangle2D.Double(x + (size - nw) / 2, by + h - 1, nw, size * 0.16));
        g.setColor(bg);
        g.setStroke(new BasicStroke((float) Math.max(1, size / 22)));
        for (int i = 0; i < 4; i++) {
            double cxl = bx + w * (0.24 + i * 0.173);
            g.draw(new Line2D.Double(cxl, by + h * 0.18, cxl, by + h * 0.46));
        }
    }

    /** Small padlock, drawn into the lower right corner of a menu icon. */
    static void paintLock(Graphics2D g, double x, double y, double size, Color fg) {
        g.setColor(fg);
        g.setStroke(new BasicStroke((float) Math.max(1, size / 7)));
        double bw = size * 0.8;
        double bh = size * 0.5;
        double bx = x + (size - bw) / 2;
        double by = y + size - bh;
        double sw = bw * 0.6;
        g.draw(new Arc2D.Double(x + (size - sw) / 2, by - sw * 0.6, sw, sw * 1.2, 0, 180, Arc2D.OPEN));
        g.fill(new Rectangle2D.Double(bx, by, bw, bh));
    }

    /** Diagonal slash across the cell: no connection. */
    static void paintSlash(Graphics2D g, double x, double y, double size, Color fg) {
        g.setColor(fg);
        g.setStroke(new BasicStroke((float) Math.max(1.5, size / 10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x + size * 0.2, y + size * 0.8, x + size * 0.8, y + size * 0.2));
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Menu icon of a WLAN network: signal fan, with a padlock when it needs a password. */
    static Icon wifiMenuIcon(int bars, boolean secured, DesktopTheme theme) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = smooth(g);
                paintWifi(g2, x, y, 16, bars, theme.textActive(), theme.shadow());
                if (secured) paintLock(g2, x + 16, y + 6, 9, theme.textActive());
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 26;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }
}
