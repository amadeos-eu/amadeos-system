package org.datazuul.dazeos.wm.x11;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datazuul.dazeos.wm.FloatingLayoutPolicy;
import org.datazuul.dazeos.wm.WindowGeometry;
import org.datazuul.dazeos.wm.WindowManagerCore;
import org.datazuul.dazeos.wm.WindowRegistry;

public final class X11WindowManagerService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(X11WindowManagerService.class.getName());

    private final LibX11 x11;
    private final Pointer display;
    private final int screen;
    private final long rootWindow;
    private final int screenWidth;
    private final int screenHeight;
    private final WindowManagerCore core;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private static volatile boolean anotherWmDetected = false;

    private static final LibX11.XErrorHandler ERROR_HANDLER = (disp, errorEvent) -> {
        if (errorEvent != null && (errorEvent.error_code & 0xFF) == LibX11.BadAccess) {
            anotherWmDetected = true;
            LOGGER.severe("X11 BadAccess received: Another window manager is already running on this display!");
        } else if (errorEvent != null) {
            LOGGER.fine(() -> "X11 error: code=" + (errorEvent.error_code & 0xFF)
                    + " request=" + (errorEvent.request_code & 0xFF)
                    + " minor=" + (errorEvent.minor_code & 0xFF));
        }
        return 0;
    };

    public static X11WindowManagerService open(String displayName) {
        return open(displayName, LibX11.INSTANCE);
    }

    public static X11WindowManagerService open(String displayName, LibX11 x11) {
        Pointer display = x11.XOpenDisplay(displayName);
        if (display == null) {
            throw new IllegalStateException("Failed to open X display: "
                    + (displayName != null ? displayName : System.getenv("DISPLAY")));
        }

        anotherWmDetected = false;
        x11.XSetErrorHandler(ERROR_HANDLER);

        int screen = x11.XDefaultScreen(display);
        long rootWindow = x11.XRootWindow(display, screen).longValue();
        int screenWidth = x11.XDisplayWidth(display, screen);
        int screenHeight = x11.XDisplayHeight(display, screen);

        // Claim WM role
        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask
                | LibX11.StructureNotifyMask | LibX11.PropertyChangeMask;
        x11.XSelectInput(display, new NativeLong(rootWindow), new NativeLong(mask));
        x11.XSync(display, 0);

        if (anotherWmDetected) {
            x11.XCloseDisplay(display);
            throw new IllegalStateException("Another window manager is already running on this display!");
        }

        NativeX11Adapter adapter = new NativeX11Adapter(x11, display);
        WindowManagerCore core = new WindowManagerCore(
                new WindowRegistry(), adapter, new FloatingLayoutPolicy(20, 30));

        return new X11WindowManagerService(x11, display, screen, rootWindow, screenWidth, screenHeight, core);
    }

    private X11WindowManagerService(
            LibX11 x11,
            Pointer display,
            int screen,
            long rootWindow,
            int screenWidth,
            int screenHeight,
            WindowManagerCore core) {
        this.x11 = x11;
        this.display = display;
        this.screen = screen;
        this.rootWindow = rootWindow;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.core = core;
    }

    public WindowManagerCore core() {
        return core;
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    public long rootWindow() {
        return rootWindow;
    }

    public void scanExistingWindows() {
        PointerByReference rootReturn = new PointerByReference();
        PointerByReference parentReturn = new PointerByReference();
        PointerByReference childrenReturn = new PointerByReference();
        IntByReference nchildrenReturn = new IntByReference();

        int status = x11.XQueryTree(display, new NativeLong(rootWindow),
                rootReturn, parentReturn, childrenReturn, nchildrenReturn);
        if (status != 0 && childrenReturn.getValue() != null && nchildrenReturn.getValue() > 0) {
            int nchildren = nchildrenReturn.getValue();
            long[] children = childrenReturn.getValue().getLongArray(0, nchildren);
            for (long child : children) {
                if (child == 0 || child == rootWindow) {
                    continue;
                }
                LibX11.XWindowAttributes attrs = new LibX11.XWindowAttributes();
                if (x11.XGetWindowAttributes(display, new NativeLong(child), attrs) != 0) {
                    if (attrs.override_redirect == 0 && attrs.map_state == 2 /* IsViewable */) {
                        WindowGeometry geom = new WindowGeometry(attrs.x, attrs.y, attrs.width, attrs.height);
                        core.manage(child, geom);
                    }
                }
            }
            x11.XFree(childrenReturn.getValue());
            core.arrange(screenWidth, screenHeight);
        }
    }

    public void processNextEvent() {
        LibX11.XEvent event = new LibX11.XEvent();
        x11.XNextEvent(display, event.getPointer());
        event.read();
        handleEvent(event);
    }

    public int processPendingEvents() {
        int processed = 0;
        while (x11.XPending(display) > 0) {
            processNextEvent();
            processed++;
        }
        return processed;
    }

    public void startEventLoop() {
        running.set(true);
        scanExistingWindows();
        LOGGER.info(() -> "DazeOS Window Manager started event loop on screen " + screenWidth + "x" + screenHeight);
        while (running.get()) {
            try {
                processNextEvent();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing X event", e);
            }
        }
        LOGGER.info("DazeOS Window Manager event loop terminated");
    }

    public void stop() {
        running.set(false);
    }

    private void handleEvent(LibX11.XEvent event) {
        switch (event.type) {
            case LibX11.MapRequest -> {
                LibX11.XMapRequestEvent mapReq = new LibX11.XMapRequestEvent(event.getPointer());
                long win = mapReq.window.longValue();
                LibX11.XWindowAttributes attrs = new LibX11.XWindowAttributes();
                int x = 50, y = 50, w = 400, h = 300;
                if (x11.XGetWindowAttributes(display, new NativeLong(win), attrs) != 0) {
                    if (attrs.override_redirect != 0) {
                        x11.XMapWindow(display, new NativeLong(win));
                        return;
                    }
                    x = attrs.x;
                    y = attrs.y;
                    w = attrs.width;
                    h = attrs.height;
                }
                WindowGeometry initialGeom = new WindowGeometry(x, y, w, h);
                core.manage(win, initialGeom);
                core.arrange(screenWidth, screenHeight);
                core.focus(win);
                LOGGER.info(() -> "Managed new client window: " + win + " geometry=" + initialGeom);
            }
            case LibX11.ConfigureRequest -> {
                LibX11.XConfigureRequestEvent cfg = new LibX11.XConfigureRequestEvent(event.getPointer());
                long win = cfg.window.longValue();
                LibX11.XWindowChanges changes = new LibX11.XWindowChanges();
                changes.x = cfg.x;
                changes.y = cfg.y;
                changes.width = cfg.width;
                changes.height = cfg.height;
                changes.border_width = cfg.border_width;
                changes.sibling = cfg.above;
                changes.stack_mode = cfg.detail;
                x11.XConfigureWindow(display, new NativeLong(win), cfg.value_mask, changes);
                x11.XFlush(display);
            }
            case LibX11.DestroyNotify -> {
                LibX11.XDestroyWindowEvent dest = new LibX11.XDestroyWindowEvent(event.getPointer());
                long win = dest.window.longValue();
                core.unmanage(win);
                LOGGER.info(() -> "Unmanaged destroyed window: " + win);
            }
            case LibX11.UnmapNotify -> {
                LibX11.XUnmapEvent unmap = new LibX11.XUnmapEvent(event.getPointer());
                long win = unmap.window.longValue();
                core.unmanage(win);
                LOGGER.info(() -> "Unmanaged unmapped window: " + win);
            }
            default -> {
                // Ignore unhandled events
            }
        }
    }

    @Override
    public void close() {
        stop();
        if (display != null) {
            x11.XCloseDisplay(display);
        }
    }
}
