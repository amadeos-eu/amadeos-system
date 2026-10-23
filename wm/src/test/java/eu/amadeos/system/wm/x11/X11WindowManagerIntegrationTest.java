package eu.amadeos.system.wm.x11;

import eu.amadeos.system.base.x11.LibX11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import eu.amadeos.system.wm.WindowGeometry;
import eu.amadeos.system.wm.decoration.FrameWindow;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.x11.LaunchFeedback;
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
                eu.amadeos.system.wm.decoration.FrameWindow frame = wm.getFrameForClient(windowId);
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
                eu.amadeos.system.wm.decoration.FrameWindow frame = wm.getFrameForClient(windowId);
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

                // During wireframe drag motion, actual window does not move (preventing ghosting in QEMU)
                assertEquals(startX, frame.frameX());
                assertEquals(startY, frame.frameY());

                // 3. Simulate ButtonRelease
                LibX11.XEvent eventRelease = new LibX11.XEvent();
                LibX11.XButtonEvent release = new LibX11.XButtonEvent(eventRelease.getPointer());
                release.type = LibX11.ButtonRelease;
                release.window = frame.frameWindow();
                release.button = LibX11.Button1;
                release.write();
                eventRelease.type = LibX11.ButtonRelease;
                wm.handleEvent(eventRelease);

                // Window moves on release
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

                // During wireframe resize, actual window is not yet resized
                assertEquals(curW, frame.frameWidth());
                assertEquals(curH, frame.frameHeight());

                // Release
                wm.handleEvent(eventRelease);

                // Window resizes on release
                assertEquals(curW + 50, frame.frameWidth());
                assertEquals(curH + 30, frame.frameHeight());

                // 5. Test Maximize button
                java.awt.Rectangle maxBounds = frame.metrics().getButtonBounds(frame.frameWidth(), eu.amadeos.system.wm.decoration.WindowButton.MAXIMIZE);
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
                java.awt.Rectangle unmaxBounds = frame.metrics().getButtonBounds(frame.frameWidth(), eu.amadeos.system.wm.decoration.WindowButton.MAXIMIZE);
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

    @Test
    void testConfigureRequestPreservesClientInsetsAndResizesFrame() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Configure Test").start();
            try {
                Thread.sleep(500);
                wm.processPendingEvents();

                assertFalse(wm.core().registry().snapshot().isEmpty());
                long windowId = wm.core().registry().snapshot().iterator().next().id();
                FrameWindow frame = wm.getFrameForClient(windowId);
                assertNotNull(frame);

                // Initial position
                int initialClientX = frame.clientX();
                int initialClientY = frame.clientY();
                assertEquals(frame.metrics().clientOffsetX(), initialClientX);
                assertEquals(frame.metrics().clientOffsetY(), initialClientY);

                // Simulate client requesting a move to screen (120, 180) and resize to (350, 150)
                // (Like Swing JOptionPane sizing and placing itself on root)
                LibX11.XEvent cfgEvent = new LibX11.XEvent();
                LibX11.XConfigureRequestEvent cfg = new LibX11.XConfigureRequestEvent(cfgEvent.getPointer());
                cfg.type = LibX11.ConfigureRequest;
                cfg.window = new com.sun.jna.NativeLong(windowId);
                cfg.x = 120;
                cfg.y = 180;
                cfg.width = 350;
                cfg.height = 150;
                cfg.value_mask = new com.sun.jna.NativeLong(LibX11.CWX | LibX11.CWY | LibX11.CWWidth | LibX11.CWHeight);
                cfg.write();
                cfgEvent.type = LibX11.ConfigureRequest;

                wm.handleEvent(cfgEvent);

                // Verify: Frame moved to (120, 180) on root
                assertEquals(120, frame.frameX());
                assertEquals(180, frame.frameY());

                // Verify: Frame resized to wrap the requested client size
                assertEquals(frame.metrics().frameWidth(350), frame.frameWidth());
                assertEquals(frame.metrics().frameHeight(150), frame.frameHeight());

                // CRITICAL: Verify client internal coordinates inside frame did NOT get clobbered to (120, 180)
                // Client must remain locked to (clientOffsetX, clientOffsetY) inside frame
                assertEquals(frame.metrics().clientOffsetX(), frame.clientX());
                assertEquals(frame.metrics().clientOffsetY(), frame.clientY());
                assertEquals(350, frame.clientWidth());
                assertEquals(150, frame.clientHeight());
            } finally {
                client.destroyForcibly();
            }
        }
    }

    @Test
    void testOpeningNewWindowDoesNotMoveExistingMovedWindows() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Process client1 = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Win1").start();
            try {
                Thread.sleep(500);
                wm.processPendingEvents();

                assertFalse(wm.core().registry().snapshot().isEmpty());
                long win1Id = wm.core().registry().snapshot().iterator().next().id();
                FrameWindow frame1 = wm.getFrameForClient(win1Id);
                assertNotNull(frame1);

                // User moves window 1 to center of screen (e.g. 350, 250)
                frame1.setBounds(350, 250, frame1.frameWidth(), frame1.frameHeight());
                wm.core().registry().updateGeometry(win1Id, new WindowGeometry(350, 250, frame1.frameWidth(), frame1.frameHeight()));
                assertEquals(350, frame1.frameX());
                assertEquals(250, frame1.frameY());

                // Now launch window 2 (like user launching xterm)
                Process client2 = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Win2").start();
                try {
                    Thread.sleep(500);
                    wm.processPendingEvents();

                    // CRITICAL: Window 1 must STAY at (350, 250) and NOT be reset back to (20, 20)!
                    assertEquals(350, frame1.frameX(), "Existing moved window must not be reset to cascade position");
                    assertEquals(250, frame1.frameY(), "Existing moved window must not be reset to cascade position");
                } finally {
                    client2.destroyForcibly();
                }
            } finally {
                client1.destroyForcibly();
            }
        }
    }

    @Test
    void testMinimizeAndRestoreWindow() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display);
             eu.amadeos.system.base.x11.X11EwmhHelper helper = eu.amadeos.system.base.x11.X11EwmhHelper.open(display)) {
            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "MinRestoreTest").start();
            try {
                Thread.sleep(500);
                wm.processPendingEvents();

                assertFalse(wm.core().registry().snapshot().isEmpty());
                long winId = wm.core().registry().snapshot().iterator().next().id();
                FrameWindow frame = wm.getFrameForClient(winId);
                assertNotNull(frame);
                assertFalse(frame.isMinimized());

                // 1. Minimize window
                wm.minimizeWindow(frame);
                assertTrue(frame.isMinimized(), "Frame must be marked minimized");
                assertTrue(helper.isWindowMinimized(winId), "Helper must report window as minimized via EWMH/ICCCM");

                // 2. Restore window via ClientMessage _NET_ACTIVE_WINDOW (as sent by taskbar click)
                helper.activateWindow(winId);
                Thread.sleep(100);
                wm.processPendingEvents();

                assertFalse(frame.isMinimized(), "Frame must be un-minimized (restored)");
                assertFalse(helper.isWindowMinimized(winId), "Window must no longer be minimized");
                assertTrue(frame.isActive(), "Restored window must be active");
            } finally {
                client.destroyForcibly();
            }
        }
    }

    @Test
    void testClientMessageWmChangeStateMinimizesWindow() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display);
             eu.amadeos.system.base.x11.X11EwmhHelper helper = eu.amadeos.system.base.x11.X11EwmhHelper.open(display)) {
            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "ChangeStateTest").start();
            try {
                Thread.sleep(500);
                wm.processPendingEvents();

                long winId = wm.core().registry().snapshot().iterator().next().id();
                FrameWindow frame = wm.getFrameForClient(winId);
                assertNotNull(frame);
                assertFalse(frame.isMinimized());

                // Taskbar sends WM_CHANGE_STATE (Iconic) when active window button is clicked
                helper.minimizeWindow(winId);
                Thread.sleep(100);
                wm.processPendingEvents();

                assertTrue(frame.isMinimized(), "Window must be minimized after receiving WM_CHANGE_STATE Iconic");
                assertTrue(helper.isWindowMinimized(winId));
            } finally {
                client.destroyForcibly();
            }
        }
    }

    /**
     * A client that hides its window (unmaps it, as Swing's setVisible(false) does) and shows it again later
     * keeps its window: the frame goes, the client goes back to the root and gets a new frame when mapped.
     */
    @Test
    void aHiddenWindowCanBeShownAgain() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            LibX11 x11 = LibX11.INSTANCE;
            com.sun.jna.Pointer client = x11.XOpenDisplay(display);
            assertNotNull(client);
            try {
                com.sun.jna.NativeLong root = new com.sun.jna.NativeLong(wm.rootWindow());
                com.sun.jna.NativeLong win = x11.XCreateSimpleWindow(client, root, 100, 100, 200, 120, 0,
                        new com.sun.jna.NativeLong(0), new com.sun.jna.NativeLong(0xffffff));
                x11.XMapWindow(client, win);
                x11.XSync(client, 0);
                Thread.sleep(200);
                wm.processPendingEvents();
                FrameWindow first = wm.getFrameForClient(win.longValue());
                assertNotNull(first, "managed with a frame");
                int x = first.frameX();
                int y = first.frameY();

                x11.XUnmapWindow(client, win);
                x11.XSync(client, 0);
                Thread.sleep(200);
                wm.processPendingEvents();
                assertNull(wm.getFrameForClient(win.longValue()), "the frame is gone");
                assertEquals(wm.rootWindow(), parentOf(x11, client, win), "the window is back on the root");

                x11.XMapWindow(client, win);
                x11.XSync(client, 0);
                Thread.sleep(200);
                wm.processPendingEvents();
                FrameWindow second = wm.getFrameForClient(win.longValue());
                assertNotNull(second, "managed again with a new frame");
                assertEquals(second.frameWindow().longValue(), parentOf(x11, client, win));
                assertEquals(x, second.frameX(), "in the same place");
                assertEquals(y, second.frameY(), "in the same place");
            } finally {
                x11.XCloseDisplay(client);
            }
        }
    }

    /**
     * Ralf's date picker in the contact editor: main window, another window, the editor (a dialog of the main
     * window) and the picker (a dialog of the editor). When the picker closes, the editor must stay in front.
     */
    @Test
    void closingADialogActivatesItsOwnerAndKeepsItInFront() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            LibX11 x11 = LibX11.INSTANCE;
            com.sun.jna.Pointer client = x11.XOpenDisplay(display);
            assertNotNull(client);
            try {
                com.sun.jna.NativeLong main = mapWindow(wm, x11, client, 0);
                com.sun.jna.NativeLong other = mapWindow(wm, x11, client, 0);
                com.sun.jna.NativeLong editor = mapWindow(wm, x11, client, main.longValue());
                com.sun.jna.NativeLong picker = mapWindow(wm, x11, client, editor.longValue());
                assertTrue(wm.getFrameForClient(picker.longValue()).isActive());

                x11.XUnmapWindow(client, picker);
                x11.XSync(client, 0);
                Thread.sleep(200);
                wm.processPendingEvents();
                assertTrue(wm.getFrameForClient(editor.longValue()).isActive(), "the editor is active again");
                assertEquals(editor.longValue(), topmost(wm), "and in front");

                x11.XDestroyWindow(client, editor);
                x11.XSync(client, 0);
                Thread.sleep(200);
                wm.processPendingEvents();
                assertTrue(wm.getFrameForClient(main.longValue()).isActive(), "closing the editor activates its owner");
                assertEquals(main.longValue(), topmost(wm));
                assertNotNull(wm.getFrameForClient(other.longValue()));
            } finally {
                x11.XCloseDisplay(client);
            }
        }
    }

    private static com.sun.jna.NativeLong mapWindow(X11WindowManagerService wm, LibX11 x11, com.sun.jna.Pointer client,
                                                    long owner) throws InterruptedException {
        com.sun.jna.NativeLong win = x11.XCreateSimpleWindow(client, new com.sun.jna.NativeLong(wm.rootWindow()),
                100, 100, 200, 120, 0, new com.sun.jna.NativeLong(0), new com.sun.jna.NativeLong(0xffffff));
        if (owner != 0) x11.XSetTransientForHint(client, win, new com.sun.jna.NativeLong(owner));
        x11.XMapWindow(client, win);
        x11.XSync(client, 0);
        Thread.sleep(200);
        wm.processPendingEvents();
        assertNotNull(wm.getFrameForClient(win.longValue()), "managed");
        return win;
    }

    private static long topmost(X11WindowManagerService wm) {
        var stack = new java.util.ArrayList<>(wm.core().registry().snapshot());
        return stack.get(stack.size() - 1).id();
    }

    private static long parentOf(LibX11 x11, com.sun.jna.Pointer display, com.sun.jna.NativeLong window) {
        com.sun.jna.ptr.PointerByReference root = new com.sun.jna.ptr.PointerByReference();
        com.sun.jna.ptr.PointerByReference parent = new com.sun.jna.ptr.PointerByReference();
        com.sun.jna.ptr.PointerByReference children = new com.sun.jna.ptr.PointerByReference();
        com.sun.jna.ptr.IntByReference count = new com.sun.jna.ptr.IntByReference();
        assertTrue(x11.XQueryTree(display, window, root, parent, children, count) != 0, "the window exists");
        if (children.getValue() != null) x11.XFree(children.getValue());
        return com.sun.jna.Pointer.nativeValue(parent.getValue());
    }

    /**
     * AWT's XToolkit replaces a Swing panel's _NET_WM_WINDOW_TYPE_DOCK with _NET_WM_WINDOW_TYPE_NORMAL while
     * it maps the window under a window manager; the strut stays. A window with a strut is a dock.
     */
    @Test
    void testWindowWithAStrutIsADockWhateverItsType() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display);
             eu.amadeos.system.base.x11.X11EwmhHelper helper = eu.amadeos.system.base.x11.X11EwmhHelper.open(display)) {
            LibX11 x11 = LibX11.INSTANCE;
            com.sun.jna.NativeLong panelWin = x11.XCreateSimpleWindow(
                    helper.display(), new com.sun.jna.NativeLong(helper.rootWindow()),
                    0, 564, 800, 36, 0, new com.sun.jna.NativeLong(0), new com.sun.jna.NativeLong(0));
            helper.setDockProperties(panelWin.longValue(), 800, 600, 36);
            // What XToolkit does at map time: the type becomes NORMAL
            com.sun.jna.NativeLong typeAtom = x11.XInternAtom(helper.display(), "_NET_WM_WINDOW_TYPE", 0);
            com.sun.jna.NativeLong normalAtom = x11.XInternAtom(helper.display(), "_NET_WM_WINDOW_TYPE_NORMAL", 0);
            com.sun.jna.Memory typeMem = new com.sun.jna.Memory(com.sun.jna.NativeLong.SIZE);
            typeMem.setNativeLong(0, normalAtom);
            x11.XChangeProperty(helper.display(), panelWin, typeAtom, new com.sun.jna.NativeLong(LibX11.XA_ATOM), 32,
                    LibX11.PropModeReplace, typeMem, 1);
            x11.XMapWindow(helper.display(), panelWin);
            x11.XFlush(helper.display());

            Thread.sleep(200);
            wm.processPendingEvents();

            assertTrue(wm.isDockWindow(panelWin.longValue()), "a window with a strut is a dock");
            assertNull(wm.getFrameForClient(panelWin.longValue()), "a dock must not have a decoration frame");
            assertEquals(36, wm.dockMarginBottom());
        }
    }

    @Test
    void testDockWindowIsNotDecoratedAndReservesBottomStrut() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display);
             eu.amadeos.system.base.x11.X11EwmhHelper helper = eu.amadeos.system.base.x11.X11EwmhHelper.open(display)) {
            // Create a native dock window
            LibX11 x11 = LibX11.INSTANCE;
            com.sun.jna.NativeLong dockWin = x11.XCreateSimpleWindow(
                    helper.display(), new com.sun.jna.NativeLong(helper.rootWindow()),
                    0, 564, 800, 36, 0, new com.sun.jna.NativeLong(0), new com.sun.jna.NativeLong(0));
            assertTrue(dockWin.longValue() != 0);

            // Set dock window type and strut
            helper.setDockProperties(dockWin.longValue(), 800, 600, 36);
            x11.XMapWindow(helper.display(), dockWin);
            x11.XFlush(helper.display());

            Thread.sleep(200);
            wm.processPendingEvents();

            // Dock window must be recognized as dock
            assertTrue(wm.isDockWindow(dockWin.longValue()));
            // Dock window must NOT be framed/decorated
            assertNull(wm.getFrameForClient(dockWin.longValue()), "Dock must not have a decoration frame");
            // Strut must be reserved
            assertEquals(36, wm.dockMarginBottom(), "Dock margin bottom strut must be 36");

            // Now test maximizing a client window respects the dock strut
            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "MaxTest").start();
            try {
                Thread.sleep(500);
                wm.processPendingEvents();

                long winId = wm.core().registry().snapshot().iterator().next().id();
                FrameWindow frame = wm.getFrameForClient(winId);
                assertNotNull(frame);

                // Maximize button click
                java.awt.Rectangle maxBounds = frame.metrics().getButtonBounds(frame.frameWidth(), eu.amadeos.system.wm.decoration.WindowButton.MAXIMIZE);
                LibX11.XEvent maxEv = new LibX11.XEvent();
                LibX11.XButtonEvent maxBtn = new LibX11.XButtonEvent(maxEv.getPointer());
                maxBtn.type = LibX11.ButtonPress;
                maxBtn.window = frame.frameWindow();
                maxBtn.button = LibX11.Button1;
                maxBtn.x = maxBounds.x + 5;
                maxBtn.y = maxBounds.y + 5;
                maxBtn.write();
                maxEv.type = LibX11.ButtonPress;
                wm.handleEvent(maxEv);

                maxBtn.type = LibX11.ButtonRelease;
                maxBtn.write();
                maxEv.type = LibX11.ButtonRelease;
                wm.handleEvent(maxEv);

                assertTrue(frame.isMaximized());
                assertEquals(800, frame.frameWidth());
                // Crucial: maximized height must be 600 - 36 = 564, leaving space for the dock!
                assertEquals(564, frame.frameHeight(), "Maximized window height must reserve bottom dock space");
            } finally {
                client.destroyForcibly();
                x11.XDestroyWindow(helper.display(), dockWin);
            }
        }
    }

    @Test
    void testDesktopIconTilesStackVerticallyAtTopLeftAndReflowOnRestore() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Process client1 = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "App 1").start();
            Process client2 = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "App 2").start();
            try {
                Thread.sleep(600);
                wm.processPendingEvents();

                assertEquals(2, wm.core().registry().snapshot().size());

                java.util.Iterator<eu.amadeos.system.wm.WindowRecord> it = wm.core().registry().snapshot().iterator();
                long win1 = it.next().id();
                long win2 = it.next().id();

                FrameWindow frame1 = wm.getFrameForClient(win1);
                FrameWindow frame2 = wm.getFrameForClient(win2);
                assertNotNull(frame1);
                assertNotNull(frame2);

                // 1. Minimize Window 1 -> creates icon tile at (0, 0)
                wm.minimizeWindow(frame1);
                assertEquals(1, wm.iconTiles().size());
                eu.amadeos.system.wm.decoration.DesktopIconTile tile0 = wm.iconTiles().get(0);
                assertEquals(0, tile0.x());
                assertEquals(0, tile0.y());
                assertEquals(win1, tile0.clientWin());

                // 2. Minimize Window 2 -> creates icon tile directly below at (0, 68) with 0px gap
                wm.minimizeWindow(frame2);
                assertEquals(2, wm.iconTiles().size());
                eu.amadeos.system.wm.decoration.DesktopIconTile tile1 = wm.iconTiles().get(1);
                assertEquals(0, tile1.x());
                assertEquals(68, tile1.y(), "Second tile must be vertically stacked with 0px gap");
                assertEquals(win2, tile1.clientWin());

                // 3. Click on tile 0 to restore Window 1
                LibX11.XEvent clickEv = new LibX11.XEvent();
                LibX11.XButtonEvent btn = new LibX11.XButtonEvent(clickEv.getPointer());
                btn.type = LibX11.ButtonPress;
                btn.window = tile0.tileWin();
                btn.button = LibX11.Button1;
                btn.write();
                clickEv.type = LibX11.ButtonPress;
                wm.handleEvent(clickEv);

                // Window 1 must be restored
                assertFalse(frame1.isMinimized(), "Window 1 must be restored upon tile click");

                // Remaining tile (Window 2) must reflow to (0, 0)
                assertEquals(1, wm.iconTiles().size());
                eu.amadeos.system.wm.decoration.DesktopIconTile remainingTile = wm.iconTiles().get(0);
                assertEquals(win2, remainingTile.clientWin());
                assertEquals(0, remainingTile.x());
                assertEquals(0, remainingTile.y(), "Remaining tile must reflow to y=0 at top-left");

                // 4. Click remaining tile to restore Window 2
                btn.window = remainingTile.tileWin();
                btn.write();
                wm.handleEvent(clickEv);

                assertFalse(frame2.isMinimized(), "Window 2 must be restored upon tile click");
                assertTrue(wm.iconTiles().isEmpty(), "All icon tiles should be gone after restoration");
            } finally {
                client1.destroyForcibly();
                client2.destroyForcibly();
            }
        }
    }

    @Test
    void testVirtualWorkspacesAndDesktopRootMenu() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            // 1. Verify EWMH virtual workspace properties on root window
            assertEquals(0, wm.currentDesktop(), "Initial workspace should be 0 (One)");

            // 2. Launch two client windows
            Process client1 = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Win 1").start();
            Process client2 = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Win 2").start();
            try {
                Thread.sleep(600);
                wm.processPendingEvents();

                var managed = wm.core().registry().snapshot().stream().map(eu.amadeos.system.wm.WindowRecord::id).toList();
                assertEquals(2, managed.size(), "Two windows should be managed");
                long win1 = managed.get(0);
                long win2 = managed.get(1);

                FrameWindow frame1 = wm.getFrameForClient(win1);
                FrameWindow frame2 = wm.getFrameForClient(win2);
                assertNotNull(frame1);
                assertNotNull(frame2);

                assertEquals(0, wm.getClientDesktop(win1));
                assertEquals(0, wm.getClientDesktop(win2));

                // Move win2 to workspace 1 (Two)
                wm.setClientDesktop(win2, 1);
                assertEquals(1, wm.getClientDesktop(win2));

                // Switch to workspace 1
                wm.switchDesktop(1);
                assertEquals(1, wm.currentDesktop());

                // Switch back to workspace 0
                wm.switchDesktop(0);
                assertEquals(0, wm.currentDesktop());

                // Test shuffle window management
                wm.shuffleUp();
                wm.shuffleDown();

                // Test Button 3 on root window (Desktop Root Menu)
                LibX11.XEvent clickEv = new LibX11.XEvent();
                LibX11.XButtonEvent btn = new LibX11.XButtonEvent(clickEv.getPointer());
                btn.type = LibX11.ButtonPress;
                btn.window = new com.sun.jna.NativeLong(wm.rootWindow());
                btn.button = LibX11.Button3;
                btn.x_root = 200;
                btn.y_root = 200;
                btn.write();
                clickEv.type = LibX11.ButtonPress;
                wm.handleEvent(clickEv);

                // Test Button 1 on root window (Dismiss Root Menu)
                btn.button = LibX11.Button1;
                btn.write();
                wm.handleEvent(clickEv);
            } finally {
                client1.destroyForcibly();
                client2.destroyForcibly();
            }
        }
    }

    @Test
    void testScreenLockerWindowIsNotDecoratedAndCoversFullScreen() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            LibX11 x11 = LibX11.INSTANCE;
            com.sun.jna.NativeLong lockWin = x11.XCreateSimpleWindow(
                    wm.display(), new com.sun.jna.NativeLong(wm.rootWindow()),
                    0, 0, 800, 600, 0, new com.sun.jna.NativeLong(0), new com.sun.jna.NativeLong(0));
            assertTrue(lockWin.longValue() != 0);

            x11.XStoreName(wm.display(), lockWin, "amadeOS Workstation Locked");
            x11.XMapWindow(wm.display(), lockWin);
            x11.XFlush(wm.display());

            Thread.sleep(200);
            wm.processPendingEvents();

            // Screen Locker must be recognized as special unmanaged window
            assertTrue(wm.isSpecialUnmanagedWindow(lockWin.longValue()), "Should be identified as special unmanaged window");
            // Must NOT have a decoration frame
            assertNull(wm.getFrameForClient(lockWin.longValue()), "Screen locker must not have a decoration frame");
            // Must NOT be in the managed window registry
            assertTrue(wm.core().registry().find(lockWin.longValue()).isEmpty(), "Screen locker must not be managed in registry");

            x11.XDestroyWindow(wm.display(), lockWin);
        }
    }

    @Test
    void actionsFromOtherThreadsRunOnTheEventLoopThread() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Thread loop = new Thread(wm::startEventLoop, "wm-event-loop");
            loop.start();
            try {
                // Give the loop time to start and block in XNextEvent.
                Thread.sleep(300);

                // Like the Swing EDT: queue work from another thread; it must wake the blocked loop.
                AtomicReference<Thread> ranOn = new AtomicReference<>();
                CountDownLatch done = new CountDownLatch(1);
                wm.runOnEventThread(() -> {
                    ranOn.set(Thread.currentThread());
                    done.countDown();
                });
                assertTrue(done.await(5, TimeUnit.SECONDS), "queued action must run without further X events");
                assertEquals(loop, ranOn.get(), "X calls must happen on the event loop thread");

                // Root menu action from a non-loop thread switches the workspace through the loop.
                wm.menuActions().switchWorkspace(2);
                long deadline = System.currentTimeMillis() + 5000;
                while (wm.currentDesktop() != 2 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20);
                }
                assertEquals(2, wm.currentDesktop());
            } finally {
                // stop() must also wake the loop out of XNextEvent.
                wm.stop();
                loop.join(5000);
            }
            assertFalse(loop.isAlive(), "event loop must end after stop()");
        }
    }

    @Test
    void launchMessageShowsBusyUntilTheNextWindow() throws Exception {
        try (X11WindowManagerService wm = X11WindowManagerService.open(display);
             X11EwmhHelper panel = X11EwmhHelper.open(display)) {
            assertFalse(panel.isLaunchBusy());

            LaunchFeedback.send(LibX11.INSTANCE, panel.display());
            assertTrue(waitFor(wm, panel::isLaunchBusy), "The launch message must set the busy flag on the root window");

            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Started").start();
            try {
                assertTrue(waitFor(wm, () -> !panel.isLaunchBusy()), "The new window must end the busy cursor");
                assertFalse(wm.core().registry().snapshot().isEmpty());
            } finally {
                client.destroyForcibly();
            }
        }
    }

    @Test
    void screenResizeKeepsWindowsOnTheScreen() throws Exception {
        assumeTrue(new File("/usr/bin/xrandr").exists(), "xrandr is required to change the screen size");
        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Process client = new ProcessBuilder("xmessage", "-display", display, "-buttons", "OK:0", "Resize").start();
            try {
                assertTrue(waitFor(wm, () -> !wm.core().registry().snapshot().isEmpty()));
                long windowId = wm.core().registry().snapshot().iterator().next().id();
                FrameWindow frame = wm.getFrameForClient(windowId);
                assertNotNull(frame);

                // Xvfb cannot grow its screen, but xrandr can make it smaller (the CRTC error is expected).
                new ProcessBuilder("xrandr", "-display", display, "--fb", "320x240").start().waitFor(5, TimeUnit.SECONDS);
                assertTrue(waitFor(wm, () -> wm.screenWidth() == 320), "The window manager must follow the new size");
                assertEquals(240, wm.screenHeight());
                assertTrue(frame.frameX() + frame.frameWidth() <= 320, "The window must stay on the screen");
                assertTrue(frame.frameY() + frame.frameHeight() <= 240, "The window must stay on the screen");
            } finally {
                client.destroyForcibly();
            }
        }
    }

    /** Lets the window manager handle its events until the condition holds; slow CI machines need more than a fixed pause. */
    private static boolean waitFor(X11WindowManagerService wm, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            wm.processPendingEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
