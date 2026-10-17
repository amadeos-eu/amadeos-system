package org.datazuul.amadeos.screen;

import org.datazuul.amadeos.screen.modules.SaverModule;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * Runs a {@link SaverModule} at about 30 frames per second. Used full screen by the daemon and as the
 * small preview in the Style Manager.
 */
public final class SaverCanvas extends JComponent {
    private final Random random = new Random();
    private final Timer timer = new Timer(33, e -> tick());
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
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w != lastWidth || h != lastHeight) {
            module.reset(w, h, random);
            lastWidth = w;
            lastHeight = h;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            module.paint(g2, w, h);
        } finally {
            g2.dispose();
        }
    }
}
