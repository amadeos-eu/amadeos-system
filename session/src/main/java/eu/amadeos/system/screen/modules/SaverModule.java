package eu.amadeos.system.screen.modules;

import java.awt.Graphics2D;
import java.util.Random;

/**
 * One screen saver animation. The canvas calls {@link #reset} when it starts or changes size, then
 * {@link #step()} and {@link #paint} about 30 times per second. Modules keep their own state and must
 * cope with any size, including tiny previews.
 */
public interface SaverModule {

    /** Stable id stored in {@code screen.conf}. */
    String id();

    /** Name shown in the Style Manager. */
    String name();

    void reset(int width, int height, Random random);

    void step();

    /** Paints the whole frame; the background is not cleared by the caller. */
    void paint(Graphics2D g, int width, int height);
}
