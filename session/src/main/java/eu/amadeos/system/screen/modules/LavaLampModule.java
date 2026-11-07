package eu.amadeos.system.screen.modules;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/**
 * A lava lamp filling the screen: blobs of wax are warmed by the lamp at the bottom, rise slowly, cool at
 * the top and sink back into the pool, merging and parting on the way (metaballs).
 *
 * <p>The wax is computed on a coarse grid of at most {@value #MAX_GRID} columns and scaled up smoothly in
 * one step. Wax has soft edges anyway, and the cost stays the same on a 4K screen and in QEMU.
 */
final class LavaLampModule implements SaverModule {
    static final int MAX_GRID = 320;
    private static final int BLOBS = 9;
    /** Field strength at the surface of the wax. */
    private static final double SURFACE = 0.5;
    /** Width of the soft edge, in field strength. */
    private static final double EDGE = 0.07;
    /** About two minutes for one turn round the colour wheel (changing colours). */
    private static final double HUE_STEP = 1.0 / (30 * 120);

    private final LavaColors chosen;
    private LavaColors colors;
    private double hue;

    // Blobs in units of the screen height: x from 0 to aspect, y from 0 (top) to 1 (bottom).
    private final double[] x = new double[BLOBS];
    private final double[] y = new double[BLOBS];
    private final double[] vx = new double[BLOBS];
    private final double[] vy = new double[BLOBS];
    private final double[] radius = new double[BLOBS];
    private final double[] heat = new double[BLOBS];
    private final double[] heatRate = new double[BLOBS];
    private final double[] sway = new double[BLOBS];
    private double aspect = 1;
    private double time;
    private Random random = new Random();

    private BufferedImage image;
    private int[] pixels;
    private float[] field;
    private double[] pool = new double[0];
    private int gw;
    private int gh;
    private double[] rowLight = new double[0];

    LavaLampModule() {
        this(LavaColors.byId(LavaColors.DEFAULT));
    }

    LavaLampModule(LavaColors colors) {
        this.chosen = colors;
        this.colors = colors;
    }

    @Override
    public String id() {
        return "lava";
    }

    @Override
    public String name() {
        return "Lava Lamp";
    }

    @Override
    public void reset(int width, int height, Random random) {
        this.random = random;
        width = Math.max(1, width);
        height = Math.max(1, height);
        aspect = (double) width / height;
        gw = Math.max(1, Math.min(width, MAX_GRID));
        gh = Math.max(1, (int) Math.round((double) gw * height / width));
        image = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        field = new float[gw * gh];
        pool = new double[gw];
        rowLight = new double[gh];
        for (int row = 0; row < gh; row++) {
            double t = (row + 0.5) / gh;
            rowLight[row] = t * t * t;
        }
        hue = random.nextDouble();
        colors = chosen.changes() ? LavaColors.changing(hue) : chosen;
        // Blobs smaller on narrow (portrait) screens, so they still fit side by side.
        double scale = Math.min(1, aspect / 1.2);
        for (int i = 0; i < BLOBS; i++) {
            radius[i] = (0.045 + random.nextDouble() * 0.06) * scale;
            x[i] = radius[i] + random.nextDouble() * Math.max(0.01, aspect - 2 * radius[i]);
            // Half start in the pool, the rest somewhere on their way.
            y[i] = i % 2 == 0 ? 0.92 + random.nextDouble() * 0.06 : 0.15 + random.nextDouble() * 0.7;
            heat[i] = random.nextDouble();
            heatRate[i] = 0.6 + random.nextDouble() * 0.8;
            sway[i] = random.nextDouble() * Math.PI * 2;
            vx[i] = 0;
            vy[i] = 0;
        }
        time = 0;
    }

    @Override
    public void step() {
        time += 1.0 / 30;
        if (chosen.changes()) {
            hue += HUE_STEP;
            colors = LavaColors.changing(hue);
        }
        for (int i = 0; i < BLOBS; i++) {
            // The lamp warms wax near the bottom, the top of the lamp cools it, the middle a little.
            double rate = heatRate[i] / (30 * 9.0);
            if (y[i] > 0.82) heat[i] += rate * 1.6;
            else if (y[i] < 0.22) heat[i] -= rate * 1.4;
            else heat[i] -= rate * 0.12;
            heat[i] = Math.max(0, Math.min(1, heat[i]));

            // Warm wax rises, cool wax sinks; about 20 seconds from the bottom to the top.
            double target = -(heat[i] - 0.45) * 0.0032;
            vy[i] += (target - vy[i]) * 0.02;
            double drift = Math.sin(time * 0.21 * heatRate[i] + sway[i]) * 0.00045;
            vx[i] += (drift - vx[i]) * 0.03 + (random.nextDouble() - 0.5) * 0.00004;
            x[i] += vx[i];
            y[i] += vy[i];

            double r = radius[i];
            if (x[i] < r) { x[i] = r; vx[i] = Math.abs(vx[i]); }
            if (x[i] > aspect - r) { x[i] = Math.max(r, aspect - r); vx[i] = -Math.abs(vx[i]); }
            // Wax rests in the pool at the bottom and stops just under the top.
            if (y[i] > 1.0) { y[i] = 1.0; vy[i] = Math.min(0, vy[i]); }
            if (y[i] < 0.08) { y[i] = 0.08; vy[i] = Math.max(0, vy[i]); }
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {
        if (image == null) reset(w, h, random);
        computeField();
        shade();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.drawImage(image, 0, 0, w, h, null);
    }

    /** Field strength of all blobs and the pool, one value per grid cell. */
    private void computeField() {
        double cell = 1.0 / gh;
        double[] reach2 = new double[BLOBS];
        double[] stretch = new double[BLOBS];
        for (int i = 0; i < BLOBS; i++) {
            double reach = radius[i] * 1.9;
            reach2[i] = reach * reach;
            // Moving wax is drawn out a little along its way.
            stretch[i] = 1 + Math.min(0.35, Math.abs(vy[i]) * 90);
        }
        // The pool of wax at the bottom, with a slowly moving surface.
        double poolDepth = 0.055;
        for (int col = 0; col < gw; col++) {
            double px = (col + 0.5) * cell;
            pool[col] = 1 - poolDepth - 0.012 * Math.sin(px * 4.1 + time * 0.23)
                    - 0.008 * Math.sin(px * 9.7 - time * 0.31);
        }
        for (int row = 0; row < gh; row++) {
            double py = (row + 0.5) * cell;
            int base = row * gw;
            for (int col = 0; col < gw; col++) {
                double px = (col + 0.5) * cell;
                double f = Math.max(0, Math.min(1.2, SURFACE + (py - pool[col]) * 9));
                for (int i = 0; i < BLOBS; i++) {
                    double dx = (px - x[i]) * stretch[i];
                    double dy = (py - y[i]) / stretch[i];
                    double d2 = dx * dx + dy * dy;
                    if (d2 < reach2[i]) {
                        double k = 1 - d2 / reach2[i];
                        f += k * k;
                    }
                }
                field[base + col] = (float) f;
            }
        }
    }

    /** Colours every cell: liquid, a faint glow round the wax, and wax lit from below. */
    private void shade() {
        LavaColors c = colors;
        int[] top = rgb(c.liquidTop());
        int[] low = rgb(c.liquidLow());
        int[] edge = rgb(c.waxEdge());
        int[] core = rgb(c.waxCore());
        int[] liquid = new int[3];
        int[] wax = new int[3];
        for (int row = 0; row < gh; row++) {
            double light = rowLight[row];
            for (int ch = 0; ch < 3; ch++) liquid[ch] = (int) (top[ch] + (low[ch] - top[ch]) * light);
            int base = row * gw;
            for (int col = 0; col < gw; col++) {
                double f = field[base + col];
                double glow = Math.min(1, f / SURFACE);
                glow = glow * glow * glow * glow * 0.22;
                double cover = smooth((f - (SURFACE - EDGE)) / (2 * EDGE));
                int r;
                int gr;
                int b;
                if (cover <= 0) {
                    r = mix(liquid[0], edge[0], glow);
                    gr = mix(liquid[1], edge[1], glow);
                    b = mix(liquid[2], edge[2], glow);
                } else {
                    // Lit from the lamp below: lower edges brighter, upper edges darker.
                    double above = row > 0 ? field[base - gw + col] : f;
                    double below = row < gh - 1 ? field[base + gw + col] : f;
                    double lit = (above - below) * gh * 0.012;
                    double depth = Math.min(1, (f - SURFACE) * 1.4);
                    double t = clamp(0.25 + depth * 0.5 + lit + light * 0.25);
                    for (int ch = 0; ch < 3; ch++) wax[ch] = mix(edge[ch], core[ch], t);
                    r = mix(mix(liquid[0], edge[0], glow), wax[0], cover);
                    gr = mix(mix(liquid[1], edge[1], glow), wax[1], cover);
                    b = mix(mix(liquid[2], edge[2], glow), wax[2], cover);
                }
                pixels[base + col] = r << 16 | gr << 8 | b;
            }
        }
    }

    private static int[] rgb(Color color) {
        return new int[]{color.getRed(), color.getGreen(), color.getBlue()};
    }

    private static int mix(int a, int b, double t) {
        return (int) (a + (b - a) * t);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double smooth(double t) {
        t = clamp(t);
        return t * t * (3 - 2 * t);
    }
}
