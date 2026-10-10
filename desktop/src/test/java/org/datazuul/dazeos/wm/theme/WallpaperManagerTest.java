package org.datazuul.dazeos.wm.theme;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WallpaperManagerTest {

    @Test
    void availableWallpapersDiscoversItems() {
        var list = WallpaperManager.availableWallpapers();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(w -> w.name().contains("Default")));
    }

    @Test
    void renderToTargetScalesFillCorrectly() {
        BufferedImage src = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = WallpaperManager.renderToTarget(src, 800, 600, WallpaperManager.ScaleMode.FILL, Color.BLUE);
        assertNotNull(out);
        assertEquals(800, out.getWidth());
        assertEquals(600, out.getHeight());
    }

    @Test
    void renderToTargetHandlesNullSrcWithSolidColor() {
        BufferedImage out = WallpaperManager.renderToTarget(null, 400, 300, WallpaperManager.ScaleMode.FILL, Color.RED);
        assertNotNull(out);
        assertEquals(400, out.getWidth());
        assertEquals(300, out.getHeight());
        // Verify center pixel matches red
        int rgb = out.getRGB(200, 150) & 0xFFFFFF;
        assertEquals(0xFF0000, rgb);
    }

    @Test
    void applyWallpaperNotifiesListeners() {
        AtomicBoolean called = new AtomicBoolean(false);
        WallpaperManager.WallpaperChangeListener listener = (path, mode, color) -> called.set(true);
        WallpaperManager.addListener(listener);

        try {
            WallpaperManager.applyWallpaper("none", WallpaperManager.ScaleMode.FIT, Color.BLACK);
            assertTrue(called.get());
            assertEquals(WallpaperManager.ScaleMode.FIT, WallpaperManager.currentScaleMode());
            assertEquals(Color.BLACK, WallpaperManager.currentBgColor());
        } finally {
            WallpaperManager.removeListener(listener);
        }
    }
}
