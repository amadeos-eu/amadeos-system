package eu.amadeos.system.screen.modules;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/** Flying through a field of stars. */
final class StarfieldModule implements SaverModule {
    private static final int STARS = 400;
    /** Every grey a star can have, made once instead of in every frame. */
    private static final Color[] GREYS = new Color[256];

    static {
        for (int i = 0; i < GREYS.length; i++) GREYS[i] = new Color(i, i, i);
    }

    private final double[] sx = new double[STARS];
    private final double[] sy = new double[STARS];
    private final double[] sz = new double[STARS];
    private Random random = new Random();

    @Override
    public String id() {
        return "starfield";
    }

    @Override
    public String name() {
        return "Starfield";
    }

    @Override
    public void reset(int width, int height, Random random) {
        this.random = random;
        for (int i = 0; i < STARS; i++) place(i, random.nextDouble());
    }

    private void place(int i, double depth) {
        sx[i] = random.nextDouble() * 2 - 1;
        sy[i] = random.nextDouble() * 2 - 1;
        sz[i] = 0.05 + depth * 0.95;
    }

    @Override
    public void step() {
        for (int i = 0; i < STARS; i++) {
            sz[i] -= 0.008;
            if (sz[i] <= 0.05) place(i, 1);
        }
    }

    @Override
    public boolean halfSize() {
        return true;
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        double cx = w / 2.0;
        double cy = h / 2.0;
        double spread = Math.max(w, h) / 2.0;
        for (int i = 0; i < STARS; i++) {
            int x = (int) (cx + sx[i] / sz[i] * spread * 0.5);
            int y = (int) (cy + sy[i] / sz[i] * spread * 0.5);
            if (x < 0 || y < 0 || x >= w || y >= h) continue;
            int brightness = (int) Math.min(255, 60 + (1 - sz[i]) * 220);
            g.setColor(GREYS[brightness]);
            int size = sz[i] < 0.3 ? 2 : 1;
            g.fillRect(x, y, size, size);
        }
    }
}
