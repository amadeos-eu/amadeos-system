package eu.amadeos.system.taskmanager.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A graph of the last minute: one line per series (the first one filled when there are two), or the series
 * stacked as areas. The scale is fixed ({@link #setMaximum}) or follows the largest value.
 */
public final class Graph extends JComponent {
    /** One series: its values (oldest first) and colour. */
    public record Series(double[] values, Color color) {
    }

    private List<Series> series = List.of();
    private boolean stacked;
    private double maximum;
    private boolean autoScale;
    private DoubleFunction<String> scaleLabel = v -> "";
    private String ago = "";
    private String now = "";

    public Graph() {
        setPreferredSize(new Dimension(360, 130));
        setMinimumSize(new Dimension(120, 60));
    }

    /** The series; the graph repaints. */
    public void setSeries(List<Series> series, boolean stacked) {
        this.series = List.copyOf(series);
        this.stacked = stacked;
        repaint();
    }

    /** A fixed top of the scale (100 for shares, the memory size). */
    public void setMaximum(double maximum) {
        this.maximum = maximum;
        this.autoScale = false;
    }

    /**
     * The top of the scale follows the largest value: {@code floor} times a power of four (64 kB/s, 256 kB/s,
     * 1 MB/s, ... for rates in bytes), so the scale reads as round numbers.
     */
    public void setAutoScale(double floor) {
        this.maximum = floor;
        this.autoScale = true;
    }

    public void setLabels(DoubleFunction<String> scaleLabel, String ago, String now) {
        this.scaleLabel = scaleLabel;
        this.ago = ago;
        this.now = now;
    }

    /** The top of the scale for these values: the floor times the smallest power of four that holds them. */
    static double niceTop(double largest, double floor) {
        double top = floor <= 0 ? 1 : floor;
        while (top < largest && top < Double.MAX_VALUE / 4) top *= 4;
        return top;
    }

    double top() {
        if (!autoScale) return maximum <= 0 ? 1 : maximum;
        double largest = 0;
        for (Series s : series) for (double v : s.values()) largest = Math.max(largest, v);
        return niceTop(largest, maximum);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0xE2, 0xE2, 0xE2));
            for (int i = 1; i < 4; i++) g.drawLine(0, h * i / 4, w, h * i / 4);
            for (int i = 1; i < 6; i++) g.drawLine(w * i / 6, 0, w * i / 6, h);
            double top = top();
            if (stacked) paintStacked(g, w, h, top); else paintLines(g, w, h, top);
            g.setFont(getFont().deriveFont(getFont().getSize2D() * 0.85f));
            label(g, scaleLabel.apply(top), 4, 3);
            FontMetrics fm = g.getFontMetrics();
            label(g, ago, 4, h - fm.getHeight() - 3);
            label(g, now, w - fm.stringWidth(now) - 8, h - fm.getHeight() - 3);
        } finally {
            g.dispose();
        }
    }

    private double y(double v, int h, double top) {
        return h - Math.min(1, Math.max(0, v / top)) * (h - 2);
    }

    private void paintLines(Graphics2D g, int w, int h, double top) {
        g.setStroke(new BasicStroke(1.6f));
        for (int k = 0; k < series.size(); k++) {
            Series s = series.get(k);
            double[] v = s.values();
            int n = v.length;
            if (n < 2) continue;
            Path2D p = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                double x = i * (w - 1.0) / (n - 1);
                if (i == 0) p.moveTo(x, y(v[i], h, top)); else p.lineTo(x, y(v[i], h, top));
            }
            if (k == 0 && series.size() <= 2) {
                Path2D fill = (Path2D) p.clone();
                fill.lineTo(w, h);
                fill.lineTo(0, h);
                fill.closePath();
                Color c = s.color();
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
                g.fill(fill);
            }
            g.setColor(s.color());
            g.draw(p);
        }
    }

    private void paintStacked(Graphics2D g, int w, int h, double top) {
        if (series.isEmpty()) return;
        int n = series.get(0).values().length;
        double[] base = new double[n];
        double[][] tops = new double[series.size()][n];
        for (int k = 0; k < series.size(); k++) {
            double[] v = series.get(k).values();
            for (int i = 0; i < n; i++) {
                base[i] += i < v.length ? v[i] : 0;
                tops[k][i] = base[i];
            }
        }
        for (int k = series.size() - 1; k >= 0; k--) {
            Path2D p = new Path2D.Double();
            p.moveTo(0, h);
            for (int i = 0; i < n; i++) p.lineTo(i * (w - 1.0) / Math.max(1, n - 1), y(tops[k][i], h, top));
            p.lineTo(w, h);
            p.closePath();
            g.setColor(series.get(k).color());
            g.fill(p);
        }
    }

    private static void label(Graphics2D g, String s, int x, int y) {
        if (s == null || s.isEmpty()) return;
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRect(x, y, fm.stringWidth(s) + 4, fm.getHeight());
        g.setColor(Color.GRAY);
        g.drawString(s, x + 2, y + fm.getAscent());
    }
}
