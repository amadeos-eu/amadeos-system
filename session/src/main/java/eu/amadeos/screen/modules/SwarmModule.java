package eu.amadeos.screen.modules;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Random;

/**
 * The X11 "swarm": a wasp flies around at random, a swarm of bees chases it.
 */
final class SwarmModule implements SaverModule {
    private static final int BEES = 80;

    private final double[] bx = new double[BEES];
    private final double[] by = new double[BEES];
    private final double[] bvx = new double[BEES];
    private final double[] bvy = new double[BEES];
    private final double[] px = new double[BEES];
    private final double[] py = new double[BEES];
    private double wx;
    private double wy;
    private double wvx;
    private double wvy;
    private int width = 1;
    private int height = 1;
    private double scale = 1;
    private Random random = new Random();

    @Override
    public String id() {
        return "swarm";
    }

    @Override
    public String name() {
        return "Swarm";
    }

    @Override
    public void reset(int width, int height, Random random) {
        this.random = random;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        scale = Math.max(0.3, Math.min(width, height) / 600.0);
        wx = this.width / 2.0;
        wy = this.height / 2.0;
        wvx = wvy = 0;
        for (int i = 0; i < BEES; i++) {
            bx[i] = px[i] = random.nextDouble() * this.width;
            by[i] = py[i] = random.nextDouble() * this.height;
            bvx[i] = bvy[i] = 0;
        }
    }

    @Override
    public void step() {
        double wasMax = 12 * scale;
        wvx = clamp(wvx + (random.nextDouble() - 0.5) * 4 * scale, wasMax);
        wvy = clamp(wvy + (random.nextDouble() - 0.5) * 4 * scale, wasMax);
        wx += wvx;
        wy += wvy;
        double border = 20 * scale;
        if (wx < border) { wx = border; wvx = Math.abs(wvx); }
        if (wx > width - border) { wx = width - border; wvx = -Math.abs(wvx); }
        if (wy < border) { wy = border; wvy = Math.abs(wvy); }
        if (wy > height - border) { wy = height - border; wvy = -Math.abs(wvy); }

        double beeMax = 9 * scale;
        for (int i = 0; i < BEES; i++) {
            px[i] = bx[i];
            py[i] = by[i];
            double ax = (wx - bx[i]) * 0.012 + (random.nextDouble() - 0.5) * 2.5 * scale;
            double ay = (wy - by[i]) * 0.012 + (random.nextDouble() - 0.5) * 2.5 * scale;
            bvx[i] = clamp(bvx[i] + ax, beeMax);
            bvy[i] = clamp(bvy[i] + ay, beeMax);
            bx[i] += bvx[i];
            by[i] += bvy[i];
        }
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF2, 0xC2, 0x3A));
        for (int i = 0; i < BEES; i++) {
            g.drawLine((int) px[i], (int) py[i], (int) bx[i], (int) by[i]);
        }
        g.setColor(Color.WHITE);
        int r = (int) Math.max(2, 4 * scale);
        g.fillOval((int) wx - r, (int) wy - r, 2 * r, 2 * r);
    }
}
