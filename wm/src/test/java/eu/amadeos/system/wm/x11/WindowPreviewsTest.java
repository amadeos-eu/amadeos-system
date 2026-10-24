package eu.amadeos.system.wm.x11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class WindowPreviewsTest {
    private static long[] icon(int size, int argb) {
        long[] v = new long[2 + size * size];
        v[0] = size;
        v[1] = size;
        java.util.Arrays.fill(v, 2, v.length, argb & 0xFFFFFFFFL);
        return v;
    }

    private static long[] concat(long[]... parts) {
        return java.util.Arrays.stream(parts).flatMapToLong(java.util.Arrays::stream).toArray();
    }

    @Test
    void picksTheSmallestIconAtLeast32() {
        BufferedImage picked = WindowPreviews.pickIcon(concat(icon(16, 0xFF0000FF), icon(48, 0xFF00FF00), icon(128, 0xFFFF0000)), 32);
        assertEquals(32, picked.getWidth());
        assertEquals(0xFF00FF00, picked.getRGB(10, 10));
    }

    @Test
    void takesAnExact32AsItIs() {
        BufferedImage picked = WindowPreviews.pickIcon(concat(icon(64, 0xFFFF0000), icon(32, 0xFF00FF00)), 32);
        assertEquals(0xFF00FF00, picked.getRGB(0, 0));
    }

    @Test
    void fallsBackToTheLargestSmallIcon() {
        BufferedImage picked = WindowPreviews.pickIcon(concat(icon(16, 0xFF0000FF), icon(24, 0xFF00FF00)), 32);
        assertEquals(24, picked.getWidth());
    }

    @Test
    void ignoresBrokenData() {
        assertNull(WindowPreviews.pickIcon(new long[]{40, 40, 1, 2, 3}, 32));
    }

    @Test
    void scalesDownKeepingTheAspect() {
        BufferedImage shot = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        BufferedImage preview = WindowPreviews.scaleDown(shot, WindowPreviews.MAX_WIDTH, WindowPreviews.MAX_HEIGHT);
        assertEquals(392, preview.getWidth());
        assertEquals(196, preview.getHeight());
    }
}
