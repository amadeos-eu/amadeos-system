package org.datazuul.dazeos.wm.x11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
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

                // Test focus and raise operations
                wm.focusClient(windowId);
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
}

