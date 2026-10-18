package eu.amadeos.system.screen.modules;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/** A black screen, as with {@code xset s blank}. */
final class BlankModule implements SaverModule {
    @Override
    public String id() {
        return "blank";
    }

    @Override
    public String name() {
        return "Blank";
    }

    @Override
    public void reset(int width, int height, Random random) {
    }

    @Override
    public void step() {
    }

    @Override
    public void paint(Graphics2D g, int width, int height) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
    }
}
