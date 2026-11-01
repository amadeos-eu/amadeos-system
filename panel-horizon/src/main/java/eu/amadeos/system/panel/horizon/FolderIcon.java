package eu.amadeos.system.panel.horizon;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/**
 * The dock's folder icons, drawn in the accent colour so that they follow the Horizon colours: a folder with a
 * downward arrow (Downloads) or with lines of text (Documents).
 */
final class FolderIcon implements Icon {
    enum Kind { DOWNLOADS, DOCUMENTS }

    private final Kind kind;
    private final int size;
    private final Supplier<BarColors> colors;

    FolderIcon(Kind kind, int size, Supplier<BarColors> colors) {
        this.kind = kind;
        this.size = size;
        this.colors = colors;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        double s = size;
        Color accent = colors.get().accent();
        Color back = BarColors.mix(accent, Color.BLACK, 0.3);
        // The back with the tab
        Path2D tab = new Path2D.Double();
        tab.moveTo(s * 0.08, s * 0.22);
        tab.lineTo(s * 0.38, s * 0.22);
        tab.lineTo(s * 0.46, s * 0.30);
        tab.lineTo(s * 0.92, s * 0.30);
        tab.lineTo(s * 0.92, s * 0.80);
        tab.lineTo(s * 0.08, s * 0.80);
        tab.closePath();
        g.setColor(back);
        g.fill(tab);
        // The front
        RoundRectangle2D front = new RoundRectangle2D.Double(s * 0.06, s * 0.36, s * 0.88, s * 0.48, s * 0.1, s * 0.1);
        g.setColor(accent);
        g.fill(front);
        g.setColor(BarColors.mix(accent, Color.WHITE, 0.35));
        g.setStroke(new BasicStroke((float) Math.max(1, s / 22)));
        g.drawLine((int) (s * 0.1), (int) (s * 0.39), (int) (s * 0.9), (int) (s * 0.39));
        // The glyph
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) Math.max(1.5, s / 14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx = s * 0.5;
        if (kind == Kind.DOWNLOADS) {
            g.draw(new java.awt.geom.Line2D.Double(cx, s * 0.45, cx, s * 0.72));
            Path2D head = new Path2D.Double();
            head.moveTo(cx - s * 0.11, s * 0.62);
            head.lineTo(cx, s * 0.73);
            head.lineTo(cx + s * 0.11, s * 0.62);
            g.draw(head);
        } else {
            for (int i = 0; i < 3; i++) {
                double ly = s * (0.5 + i * 0.1);
                double w = i == 2 ? 0.2 : 0.32;
                g.draw(new java.awt.geom.Line2D.Double(cx - s * 0.16, ly, cx - s * 0.16 + s * w, ly));
            }
        }
        g.dispose();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
