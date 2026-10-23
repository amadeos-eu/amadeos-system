package eu.amadeos.system.base.theme;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Properties;
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

    @Test
    void aWorkspaceWithoutABackdropOfItsOwnShowsTheCommonOne() {
        Properties props = new Properties();
        props.setProperty("wallpaper", "/usr/share/backgrounds/amadeos/wallpaper.png");
        props.setProperty("mode", "FIT");
        props.setProperty("color", "#102030");
        props.setProperty("workspace.3.wallpaper", "/usr/share/backdrops/Concave.pm");
        props.setProperty("workspace.3.mode", "TILE");

        WallpaperManager.Backdrop common = WallpaperManager.backdropOf(props, 0);
        assertEquals("/usr/share/backgrounds/amadeos/wallpaper.png", common.path());
        assertEquals(WallpaperManager.ScaleMode.FIT, common.mode());
        assertEquals(new Color(0x10, 0x20, 0x30), common.color());
        assertFalse(WallpaperManager.hasOwnBackdrop(props, 0));

        WallpaperManager.Backdrop third = WallpaperManager.backdropOf(props, 2);
        assertTrue(WallpaperManager.hasOwnBackdrop(props, 2));
        assertEquals("/usr/share/backdrops/Concave.pm", third.path());
        assertEquals(WallpaperManager.ScaleMode.TILE, third.mode());
        assertEquals(common.color(), third.color(), "a workspace without a colour of its own takes the common one");
        assertEquals(common, WallpaperManager.backdropOf(props, -1), "-1 is all workspaces");
    }

    @Test
    void anEmptyBackdropIsTheColourOnly() {
        WallpaperManager.Backdrop backdrop = new WallpaperManager.Backdrop("", null, null);
        assertEquals(WallpaperManager.NO_BACKDROP, backdrop.path());
        assertEquals(WallpaperManager.ScaleMode.FILL, backdrop.mode());
        assertNotNull(backdrop.color());
    }
}
