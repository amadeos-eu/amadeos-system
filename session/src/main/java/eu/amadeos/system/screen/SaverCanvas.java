package eu.amadeos.system.screen;

import eu.amadeos.system.screen.modules.SaverModule;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.VolatileImage;
import java.util.Random;

/**
 * Runs a {@link SaverModule} at about 30 frames per second. Used full screen by the daemon and as the
 * small preview in the Style Manager.
 *
 * <p>On very large areas (several monitors, 4K) modules that allow it ({@link SaverModule#halfSize()}) are
 * drawn at half the size into an image in video memory and scaled up in one step: a quarter of the pixels
 * to draw, which matters most without a graphics card, e.g. in QEMU.
 */
public final class SaverCanvas extends JComponent {
    private final Random random = new Random();
    /** Above this many pixels, modules that allow it are drawn at half the size. */
    static final long LARGE = 2560L * 1600;

    private final Timer timer = new Timer(33, e -> tick());
    private VolatileImage half;
    private SaverModule module;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public SaverCanvas(SaverModule module) {
        this.module = module;
        setOpaque(true);
        setDoubleBuffered(true);
    }

    public void setModule(SaverModule module) {
        this.module = module;
        lastWidth = -1;
        repaint();
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private void tick() {
        if (lastWidth < 0) return; // not painted yet
        module.step();
        repaint();
        // Without this, X11 may hold the drawing back and the animation stutters.
        Toolkit.getDefaultToolkit().sync();
    }

    /** Whether the module is drawn at half the size on an area of this size. */
    static boolean atHalfSize(SaverModule module, int width, int height) {
        return module.halfSize() && (long) width * height > LARGE;
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        boolean reduced = atHalfSize(module, w, h);
        int mw = reduced ? Math.max(1, w / 2) : w;
        int mh = reduced ? Math.max(1, h / 2) : h;
        if (mw != lastWidth || mh != lastHeight) {
            module.reset(mw, mh, random);
            lastWidth = mw;
            lastHeight = mh;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (reduced) paintScaledUp(g2, w, h, mw, mh);
            else module.paint(g2, w, h);
        } finally {
            g2.dispose();
        }
    }

    private void paintScaledUp(Graphics2D g, int w, int h, int mw, int mh) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Images in video memory can be lost (another program, a mode switch): draw again.
        do {
            if (half == null || half.getWidth() != mw || half.getHeight() != mh
                    || half.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE) {
                if (half != null) half.flush();
                half = createVolatileImage(mw, mh);
            }
            Graphics2D hg = half.createGraphics();
            try {
                module.paint(hg, mw, mh);
            } finally {
                hg.dispose();
            }
            g.drawImage(half, 0, 0, w, h, null);
        } while (half.contentsLost());
    }
}
