package eu.amadeos.system.screen.modules;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * The classic X11 "Qix": two bouncing points joined by a line that leaves a fading trail of slowly
 * changing colour.
 */
final class QixModule implements SaverModule {
    private static final int TRAIL = 48;

    private final double[] x = new double[2];
    private final double[] y = new double[2];
    private final double[] dx = new double[2];
    private final double[] dy = new double[2];
    private final Deque<double[]> trail = new ArrayDeque<>();
    private int width = 1;
    private int height = 1;
    private float hue;

    @Override
    public String id() {
        return "qix";
    }

    @Override
    public String name() {
        return "Qix";
    }

    @Override
    public void reset(int width, int height, Random random) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        double speed = Math.max(1.5, Math.min(width, height) / 120.0);
        for (int i = 0; i < 2; i++) {
            x[i] = random.nextDouble() * this.width;
            y[i] = random.nextDouble() * this.height;
            dx[i] = (random.nextBoolean() ? 1 : -1) * speed * (0.6 + random.nextDouble());
            dy[i] = (random.nextBoolean() ? 1 : -1) * speed * (0.6 + random.nextDouble());
        }
        hue = random.nextFloat();
        trail.clear();
    }

    @Override
    public void step() {
        for (int i = 0; i < 2; i++) {
            x[i] += dx[i];
            y[i] += dy[i];
            if (x[i] < 0 || x[i] >= width) {
                dx[i] = -dx[i];
                x[i] = Math.max(0, Math.min(width - 1, x[i]));
            }
            if (y[i] < 0 || y[i] >= height) {
                dy[i] = -dy[i];
                y[i] = Math.max(0, Math.min(height - 1, y[i]));
            }
        }
        hue = (hue + 0.002f) % 1f;
        trail.addFirst(new double[]{x[0], y[0], x[1], y[1], hue});
        while (trail.size() > TRAIL) trail.removeLast();
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(Math.max(1f, Math.min(w, h) / 400f)));
        int i = 0;
        for (double[] line : trail) {
            float brightness = 1f - (float) i / TRAIL;
            g.setColor(Color.getHSBColor((float) line[4], 0.8f, brightness));
            g.drawLine((int) line[0], (int) line[1], (int) line[2], (int) line[3]);
            i++;
        }
    }
}
