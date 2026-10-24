package eu.amadeos.system.wm.switcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewAreaTest {
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1280, 720);

    @Test
    void aWindowInFullViewIsReadWhole() {
        Rectangle frame = new Rectangle(100, 50, 600, 400);
        assertEquals(frame, PreviewArea.readable(frame, List.of(new Rectangle(800, 50, 300, 300)), SCREEN));
    }

    @Test
    void aCoveredWindowIsNotRead() {
        Rectangle frame = new Rectangle(100, 50, 600, 400);
        assertNull(PreviewArea.readable(frame, List.of(new Rectangle(650, 400, 300, 300)), SCREEN));
    }

    @Test
    void theAreaIsClippedToTheScreenAndThePanel() {
        Rectangle frame = new Rectangle(-50, 600, 400, 300);
        assertEquals(new Rectangle(0, 600, 350, 120), PreviewArea.readable(frame, List.of(), SCREEN));
    }

    @Test
    void aWindowOffScreenIsNotRead() {
        assertNull(PreviewArea.readable(new Rectangle(1300, 0, 400, 300), List.of(), SCREEN));
    }
}
