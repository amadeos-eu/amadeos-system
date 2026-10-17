package org.datazuul.amadeos.wm.decoration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MotifDecorationThemeTest {
    private MotifDecorationTheme theme;
    private FrameMetrics metrics;

    @BeforeEach
    void setUp() {
        theme = new MotifDecorationTheme();
        metrics = theme.metrics();
    }

    @Test
    void testMetricsCalculations() {
        assertEquals(26, metrics.titleBarHeight());
        assertEquals(6, metrics.borderWidth());

        // Test frame to client conversion
        int clientW = 400;
        int clientH = 300;
        int frameW = metrics.frameWidth(clientW);
        int frameH = metrics.frameHeight(clientH);

        assertEquals(412, frameW); // 400 + 6*2
        assertEquals(332, frameH); // 300 + 26 + 6

        assertEquals(clientW, metrics.clientWidth(frameW));
        assertEquals(clientH, metrics.clientHeight(frameH));
    }

    @Test
    void testButtonBoundsAndHitTesting() {
        int frameW = 500;

        Rectangle menuBounds = metrics.getButtonBounds(frameW, WindowButton.MENU);
        Rectangle closeBounds = metrics.getButtonBounds(frameW, WindowButton.CLOSE);
        Rectangle maxBounds = metrics.getButtonBounds(frameW, WindowButton.MAXIMIZE);
        Rectangle minBounds = metrics.getButtonBounds(frameW, WindowButton.MINIMIZE);

        // Buttons must not overlap
        assertFalse(menuBounds.intersects(closeBounds));
        assertFalse(closeBounds.intersects(maxBounds));
        assertFalse(maxBounds.intersects(minBounds));

        // Test hit testing
        assertEquals(WindowButton.MENU, metrics.hitTestButton(frameW, menuBounds.x + 5, menuBounds.y + 5).orElse(null));
        assertEquals(WindowButton.CLOSE, metrics.hitTestButton(frameW, closeBounds.x + 5, closeBounds.y + 5).orElse(null));
        assertEquals(WindowButton.MAXIMIZE, metrics.hitTestButton(frameW, maxBounds.x + 5, maxBounds.y + 5).orElse(null));
        assertEquals(WindowButton.MINIMIZE, metrics.hitTestButton(frameW, minBounds.x + 5, minBounds.y + 5).orElse(null));

        // Outside buttons
        assertTrue(metrics.hitTestButton(frameW, 200, 10).isEmpty());
        assertTrue(metrics.isTitleBarDragZone(frameW, 200, 10));
    }

    @Test
    void testBorderResizeZones() {
        int w = 500;
        int h = 400;

        // Corners
        assertEquals(ResizeDirection.NW, metrics.hitTestBorder(w, h, 2, 2));
        assertEquals(ResizeDirection.NE, metrics.hitTestBorder(w, h, 498, 2));
        assertEquals(ResizeDirection.SW, metrics.hitTestBorder(w, h, 2, 398));
        assertEquals(ResizeDirection.SE, metrics.hitTestBorder(w, h, 498, 398));

        // Edges
        assertEquals(ResizeDirection.N, metrics.hitTestBorder(w, h, 250, 2));
        assertEquals(ResizeDirection.S, metrics.hitTestBorder(w, h, 250, 398));
        assertEquals(ResizeDirection.W, metrics.hitTestBorder(w, h, 2, 200));
        assertEquals(ResizeDirection.E, metrics.hitTestBorder(w, h, 498, 200));

        // Inside
        assertEquals(ResizeDirection.NONE, metrics.hitTestBorder(w, h, 250, 200));
    }

    @Test
    void testPaintFrame() {
        BufferedImage image = new BufferedImage(500, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            theme.paintFrame(g, 500, 400, "Test Window", true, WindowButton.CLOSE, null);
        } finally {
            g.dispose();
        }

        assertNotNull(image);
        // Verify pixel at (0, 0) is highlight color (top-left 3D bevel)
        int rgb00 = image.getRGB(0, 0);
        assertEquals(theme.theme().highlight().getRGB(), rgb00);
    }
}
