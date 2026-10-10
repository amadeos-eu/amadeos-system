package org.datazuul.dazeos.wm.x11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import org.datazuul.dazeos.wm.WindowGeometry;
import org.datazuul.dazeos.wm.decoration.FrameWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class X11WindowManagerIntegrationTest {
    private Process xvfbProcess;
    private final String display = ":98";

    @BeforeEach
    void setUp() throws Exception {
        boolean hasXvfb = new File("/usr/bin/Xvfb").exists();
        assumeTrue(hasXvfb, "Xvfb is required for native X11 integration test");

        ProcessBuilder pb = new ProcessBuilder("Xvfb", display, "-screen", "0", "800x600x24");
        xvfbProcess = pb.start();
        Thread.sleep(1000);
    }

    @AfterEach
    void tearDown() {
        if (xvfbProcess != null && xvfbProcess.isAlive()) {
            xvfbProcess.destroyForcibly();
        }
    }

    @Test
    void testConnectAndManageClientWindow() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            assertNotNull(wm);
            assertEquals(800, wm.screenWidth());
            assertEquals(600, wm.screenHeight());
            assertTrue(wm.rootWindow() != 0);
            assertTrue(wm.checkWindow() != 0, "EWMH check window must be created");

            // Launch a real X client
            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Test Window").start();
            try {
                Thread.sleep(500);
                int processed = wm.processPendingEvents();
                assertTrue(processed > 0, "Expected at least one X event to be processed");
                assertFalse(wm.core().registry().snapshot().isEmpty(), "Expected window to be managed");

                long windowId = wm.core().registry().snapshot().iterator().next().id();
                assertTrue(windowId != 0);

                // Verify reparented decoration frame
                org.datazuul.dazeos.wm.decoration.FrameWindow frame = wm.getFrameForClient(windowId);
                assertNotNull(frame, "Expected client window to be reparented into a FrameWindow");
                assertTrue(frame.frameWindow().longValue() != 0);
                assertEquals(windowId, frame.clientWindow().longValue());
                assertTrue(frame.frameWidth() > frame.clientWidth());
                assertTrue(frame.frameHeight() > frame.clientHeight());

                // Test focus and raise operations
                wm.focusClient(windowId);
                assertTrue(frame.isActive(), "Frame should be active when focused");
                wm.core().focus(windowId);
                wm.core().raise(windowId);

                // Test closeWindow via ICCCM WM_DELETE_WINDOW protocol
                wm.core().close(windowId);
                Thread.sleep(200);
                wm.processPendingEvents();
            } finally {
                client.destroyForcibly();
            }

            Thread.sleep(500);
            wm.processPendingEvents();
            assertTrue(wm.core().registry().snapshot().isEmpty(), "Window should be unmanaged after client close");
        }
    }

    @Test
    void testInteractiveFrameMoveAndResize() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Drag Test").start();
            try {
                Thread.sleep(500);
                wm.processPendingEvents();

                assertFalse(wm.core().registry().snapshot().isEmpty());
                long windowId = wm.core().registry().snapshot().iterator().next().id();
                org.datazuul.dazeos.wm.decoration.FrameWindow frame = wm.getFrameForClient(windowId);
                assertNotNull(frame);

                // Set known test dimensions (400x300 at 50, 50)
                frame.setBounds(50, 50, 400, 300);
                wm.core().registry().updateGeometry(windowId, new WindowGeometry(50, 50, 400, 300));

                int startX = frame.frameX();
                int startY = frame.frameY();

                // 1. Simulate ButtonPress on titlebar drag zone (x=150, y=15)
                assertTrue(frame.isTitleBarDragZone(150, 15));
                LibX11.XEvent eventPress = new LibX11.XEvent();
                LibX11.XButtonEvent press = new LibX11.XButtonEvent(eventPress.getPointer());
                press.type = LibX11.ButtonPress;
                press.window = frame.frameWindow();
                press.button = LibX11.Button1;
                press.x = 150;
                press.y = 15;
                press.x_root = startX + 150;
                press.y_root = startY + 15;
                press.write();
                eventPress.type = LibX11.ButtonPress;
                wm.handleEvent(eventPress);

                // 2. Simulate MotionNotify (drag delta: +60, +40)
                LibX11.XEvent eventMotion = new LibX11.XEvent();
                LibX11.XMotionEvent motion = new LibX11.XMotionEvent(eventMotion.getPointer());
                motion.type = LibX11.MotionNotify;
                motion.window = frame.frameWindow();
                motion.x_root = startX + 150 + 60;
                motion.y_root = startY + 15 + 40;
                motion.write();
                eventMotion.type = LibX11.MotionNotify;
                wm.handleEvent(eventMotion);

                assertEquals(startX + 60, frame.frameX());
                assertEquals(startY + 40, frame.frameY());

                // 3. Simulate ButtonRelease
                LibX11.XEvent eventRelease = new LibX11.XEvent();
                LibX11.XButtonEvent release = new LibX11.XButtonEvent(eventRelease.getPointer());
                release.type = LibX11.ButtonRelease;
                release.window = frame.frameWindow();
                release.button = LibX11.Button1;
                release.write();
                eventRelease.type = LibX11.ButtonRelease;
                wm.handleEvent(eventRelease);

                assertEquals(startX + 60, frame.frameX());
                assertEquals(startY + 40, frame.frameY());

                // 4. Simulate Border Resize (SE corner grab)
                int curW = frame.frameWidth();
                int curH = frame.frameHeight();
                int curX = frame.frameX();
                int curY = frame.frameY();

                LibX11.XEvent eventResizePress = new LibX11.XEvent();
                LibX11.XButtonEvent resizePress = new LibX11.XButtonEvent(eventResizePress.getPointer());
                resizePress.type = LibX11.ButtonPress;
                resizePress.window = frame.frameWindow();
                resizePress.button = LibX11.Button1;
                resizePress.x = curW - 2;
                resizePress.y = curH - 2;
                resizePress.x_root = curX + curW - 2;
                resizePress.y_root = curY + curH - 2;
                resizePress.write();
                eventResizePress.type = LibX11.ButtonPress;
                wm.handleEvent(eventResizePress);

                // Motion +50, +30
                LibX11.XEvent eventResizeMotion = new LibX11.XEvent();
                LibX11.XMotionEvent resizeMotion = new LibX11.XMotionEvent(eventResizeMotion.getPointer());
                resizeMotion.type = LibX11.MotionNotify;
                resizeMotion.window = frame.frameWindow();
                resizeMotion.x_root = curX + curW - 2 + 50;
                resizeMotion.y_root = curY + curH - 2 + 30;
                resizeMotion.write();
                eventResizeMotion.type = LibX11.MotionNotify;
                wm.handleEvent(eventResizeMotion);

                assertEquals(curW + 50, frame.frameWidth());
                assertEquals(curH + 30, frame.frameHeight());

                // Release
                wm.handleEvent(eventRelease);

                assertEquals(curW + 50, frame.frameWidth());
                assertEquals(curH + 30, frame.frameHeight());

                // 5. Test Maximize button
                java.awt.Rectangle maxBounds = frame.metrics().getButtonBounds(frame.frameWidth(), org.datazuul.dazeos.wm.decoration.WindowButton.MAXIMIZE);
                LibX11.XEvent maxPressEv = new LibX11.XEvent();
                LibX11.XButtonEvent maxPress = new LibX11.XButtonEvent(maxPressEv.getPointer());
                maxPress.type = LibX11.ButtonPress;
                maxPress.window = frame.frameWindow();
                maxPress.button = LibX11.Button1;
                maxPress.x = maxBounds.x + 5;
                maxPress.y = maxBounds.y + 5;
                maxPress.write();
                maxPressEv.type = LibX11.ButtonPress;
                wm.handleEvent(maxPressEv);

                LibX11.XEvent maxRelEv = new LibX11.XEvent();
                LibX11.XButtonEvent maxRel = new LibX11.XButtonEvent(maxRelEv.getPointer());
                maxRel.type = LibX11.ButtonRelease;
                maxRel.window = frame.frameWindow();
                maxRel.button = LibX11.Button1;
                maxRel.x = maxBounds.x + 5;
                maxRel.y = maxBounds.y + 5;
                maxRel.write();
                maxRelEv.type = LibX11.ButtonRelease;
                wm.handleEvent(maxRelEv);

                assertTrue(frame.isMaximized(), "Frame should be maximized");
                assertEquals(800, frame.frameWidth());
                assertEquals(600, frame.frameHeight());

                // Restore
                java.awt.Rectangle unmaxBounds = frame.metrics().getButtonBounds(frame.frameWidth(), org.datazuul.dazeos.wm.decoration.WindowButton.MAXIMIZE);
                maxPress.x = unmaxBounds.x + 5;
                maxPress.y = unmaxBounds.y + 5;
                maxPress.write();
                wm.handleEvent(maxPressEv);
                maxRel.x = unmaxBounds.x + 5;
                maxRel.y = unmaxBounds.y + 5;
                maxRel.write();
                wm.handleEvent(maxRelEv);

                assertFalse(frame.isMaximized(), "Frame should be restored to unmaximized state");
                assertEquals(curW + 50, frame.frameWidth());
                assertEquals(curH + 30, frame.frameHeight());
            } finally {
                client.destroyForcibly();
            }
        }
    }
}

