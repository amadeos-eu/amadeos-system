package eu.amadeos.wm.decoration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.NativeLong;
import org.junit.jupiter.api.Test;

class FrameWindowTest {
    @Test
    void testFrameWindowGeometryAndRendering() {
        NativeLong clientWin = new NativeLong(1001);
        NativeLong frameWin = new NativeLong(1002);
        DecorationTheme theme = new MotifDecorationTheme();

        FrameWindow frame = FrameWindow.createForClient(clientWin, frameWin, 50, 50, 400, 300, theme);

        assertEquals(clientWin, frame.clientWindow());
        assertEquals(frameWin, frame.frameWindow());

        // Check geometry
        // frameWidth = 400 + 2*6 = 412
        // frameHeight = 300 + 26 + 6 = 332
        // frameX = 50 - 6 = 44
        // frameY = 50 - 26 = 24
        assertEquals(412, frame.frameWidth());
        assertEquals(332, frame.frameHeight());
        assertEquals(44, frame.frameX());
        assertEquals(24, frame.frameY());

        assertEquals(400, frame.clientWidth());
        assertEquals(300, frame.clientHeight());
        assertEquals(6, frame.clientX());
        assertEquals(26, frame.clientY());

        // Render buffer
        frame.setTitle("Test Frame");
        frame.setActive(true);
        frame.render();

        assertNotNull(frame.buffer());
        assertEquals(412, frame.buffer().getWidth());
        assertEquals(332, frame.buffer().getHeight());

        // Hit testing
        assertTrue(frame.isTitleBarDragZone(200, 15));
        assertEquals(WindowButton.CLOSE, frame.hitTestButton(frame.frameWidth() - 15, 15).orElse(null));
        assertEquals(ResizeDirection.SE, frame.hitTestBorder(frame.frameWidth() - 2, frame.frameHeight() - 2));

        // Resizing
        frame.setBounds(10, 20, 600, 500);
        assertEquals(600, frame.frameWidth());
        assertEquals(500, frame.frameHeight());
        assertEquals(600, frame.buffer().getWidth());
        assertEquals(500, frame.buffer().getHeight());
    }
}
