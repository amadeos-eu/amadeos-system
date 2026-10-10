package org.datazuul.dazeos.wm.x11;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datazuul.dazeos.wm.FloatingLayoutPolicy;
import org.datazuul.dazeos.wm.WindowGeometry;
import org.datazuul.dazeos.wm.WindowManagerCore;
import org.datazuul.dazeos.wm.WindowRecord;
import org.datazuul.dazeos.wm.WindowRegistry;
import org.datazuul.dazeos.wm.decoration.DecorationTheme;
import org.datazuul.dazeos.wm.decoration.FrameMetrics;
import org.datazuul.dazeos.wm.decoration.FrameWindow;
import org.datazuul.dazeos.wm.decoration.MotifDecorationTheme;
import org.datazuul.dazeos.wm.decoration.ResizeDirection;
import org.datazuul.dazeos.wm.decoration.WindowButton;

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

    private final long checkWindow;
    private final Pointer gc;
    private final Pointer xorGc;
    private final DecorationTheme theme = new MotifDecorationTheme();
    private final Map<Long, FrameWindow> clientToFrame;
    private final Map<Long, FrameWindow> frameToClient;

    // Wireframe dragging state
    private boolean wireframeActive = false;
    private int wireframeX;
    private int wireframeY;
    private int wireframeW;
    private int wireframeH;

    // Cursors
    private final NativeLong cursorDefault;
    private final NativeLong cursorMove;
    private final NativeLong cursorN;
    private final NativeLong cursorS;
    private final NativeLong cursorW;
    private final NativeLong cursorE;
    private final NativeLong cursorNW;
    private final NativeLong cursorNE;
    private final NativeLong cursorSW;
    private final NativeLong cursorSE;

    // EWMH and ICCCM atoms
    private final NativeLong netSupportingWmCheck;
    private final NativeLong netWmName;
    private final NativeLong netSupported;
    private final NativeLong netClientList;
    private final NativeLong netActiveWindow;
    private final NativeLong netCloseWindow;
    private final NativeLong utf8String;
    private final NativeLong xaWindowAtom;
    private final NativeLong xaAtomAtom;

    private static volatile boolean anotherWmDetected = false;

    // Mouse interaction states
    private enum InteractionType {
        NONE,
        MOVING,
        RESIZING,
        BUTTON_PRESS
    }

    private InteractionType interactionType = InteractionType.NONE;
    private FrameWindow interactionFrame = null;
    private WindowButton currentPressedButton = null;
    private ResizeDirection resizeDirection = ResizeDirection.NONE;

    private int dragStartRootX;
    private int dragStartRootY;
    private int dragStartFrameX;
    private int dragStartFrameY;
    private int dragStartFrameW;
    private int dragStartFrameH;

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

        Map<Long, FrameWindow> clientToFrame = new HashMap<>();
        Map<Long, FrameWindow> frameToClient = new HashMap<>();

        Pointer gc = x11.XCreateGC(display, new NativeLong(rootWindow), new NativeLong(0), null);
        Pointer xorGc = x11.XCreateGC(display, new NativeLong(rootWindow), new NativeLong(0), null);
        x11.XSetFunction(display, xorGc, LibX11.GXxor);
        x11.XSetSubwindowMode(display, xorGc, LibX11.IncludeInferiors);
        x11.XSetForeground(display, xorGc, new NativeLong(0xFFFFFF));
        x11.XSetLineAttributes(display, xorGc, 2, LibX11.LineSolid, LibX11.CapButt, LibX11.JoinMiter);

        NativeX11Adapter adapter = new NativeX11Adapter(x11, display, clientToFrame::get, gc, screen);
        WindowManagerCore core = new WindowManagerCore(
                new WindowRegistry(), adapter, new FloatingLayoutPolicy(20, 30));

        return new X11WindowManagerService(
                x11, display, screen, rootWindow, screenWidth, screenHeight, core, clientToFrame, frameToClient, gc, xorGc);
    }

    private X11WindowManagerService(
            LibX11 x11,
            Pointer display,
            int screen,
            long rootWindow,
            int screenWidth,
            int screenHeight,
            WindowManagerCore core,
            Map<Long, FrameWindow> clientToFrame,
            Map<Long, FrameWindow> frameToClient,
            Pointer gc,
            Pointer xorGc) {
        this.x11 = x11;
        this.display = display;
        this.screen = screen;
        this.rootWindow = rootWindow;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.core = core;
        this.clientToFrame = clientToFrame;
        this.frameToClient = frameToClient;
        this.gc = gc;
        this.xorGc = xorGc;

        this.cursorDefault = x11.XCreateFontCursor(display, LibX11.XC_left_ptr);
        this.cursorMove = x11.XCreateFontCursor(display, LibX11.XC_fleur);
        this.cursorN = x11.XCreateFontCursor(display, LibX11.XC_top_side);
        this.cursorS = x11.XCreateFontCursor(display, LibX11.XC_bottom_side);
        this.cursorW = x11.XCreateFontCursor(display, LibX11.XC_left_side);
        this.cursorE = x11.XCreateFontCursor(display, LibX11.XC_right_side);
        this.cursorNW = x11.XCreateFontCursor(display, LibX11.XC_top_left_corner);
        this.cursorNE = x11.XCreateFontCursor(display, LibX11.XC_top_right_corner);
        this.cursorSW = x11.XCreateFontCursor(display, LibX11.XC_bottom_left_corner);
        this.cursorSE = x11.XCreateFontCursor(display, LibX11.XC_bottom_right_corner);

        this.netSupportingWmCheck = x11.XInternAtom(display, "_NET_SUPPORTING_WM_CHECK", 0);
        this.netWmName = x11.XInternAtom(display, "_NET_WM_NAME", 0);
        this.netSupported = x11.XInternAtom(display, "_NET_SUPPORTED", 0);
        this.netClientList = x11.XInternAtom(display, "_NET_CLIENT_LIST", 0);
        this.netActiveWindow = x11.XInternAtom(display, "_NET_ACTIVE_WINDOW", 0);
        this.netCloseWindow = x11.XInternAtom(display, "_NET_CLOSE_WINDOW", 0);
        this.utf8String = x11.XInternAtom(display, "UTF8_STRING", 0);
        this.xaWindowAtom = new NativeLong(LibX11.XA_WINDOW);
        this.xaAtomAtom = new NativeLong(LibX11.XA_ATOM);

        NativeLong createdCheck = x11.XCreateSimpleWindow(
                display, new NativeLong(rootWindow), 0, 0, 1, 1, 0, new NativeLong(0), new NativeLong(0));
        this.checkWindow = createdCheck != null ? createdCheck.longValue() : 0;
        initEwmh();
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

    public long checkWindow() {
        return checkWindow;
    }

    public FrameWindow getFrameForClient(long clientWin) {
        return clientToFrame.get(clientWin);
    }

    private NativeLong getCursorForDirection(ResizeDirection dir) {
        if (dir == null) return cursorDefault;
        return switch (dir) {
            case N -> cursorN;
            case S -> cursorS;
            case W -> cursorW;
            case E -> cursorE;
            case NW -> cursorNW;
            case NE -> cursorNE;
            case SW -> cursorSW;
            case SE -> cursorSE;
            case NONE -> cursorDefault;
        };
    }

    private void initEwmh() {
        if (checkWindow == 0) {
            return;
        }

        // _NET_SUPPORTING_WM_CHECK on root and check window
        Memory checkMem = new Memory(NativeLong.SIZE);
        checkMem.setNativeLong(0, new NativeLong(checkWindow));
        x11.XChangeProperty(display, new NativeLong(rootWindow), netSupportingWmCheck, xaWindowAtom, 32, LibX11.PropModeReplace, checkMem, 1);
        x11.XChangeProperty(display, new NativeLong(checkWindow), netSupportingWmCheck, xaWindowAtom, 32, LibX11.PropModeReplace, checkMem, 1);

        // _NET_WM_NAME on check window
        byte[] nameBytes = "DazeWM".getBytes(StandardCharsets.UTF_8);
        Memory nameMem = new Memory(nameBytes.length);
        nameMem.write(0, nameBytes, 0, nameBytes.length);
        x11.XChangeProperty(display, new NativeLong(checkWindow), netWmName, utf8String, 8, LibX11.PropModeReplace, nameMem, nameBytes.length);

        // _NET_SUPPORTED on root
        NativeLong[] supportedAtoms = new NativeLong[]{
            netSupportingWmCheck, netWmName, netSupported, netClientList, netActiveWindow, netCloseWindow
        };
        Memory supMem = new Memory((long) supportedAtoms.length * NativeLong.SIZE);
        for (int i = 0; i < supportedAtoms.length; i++) {
            supMem.setNativeLong((long) i * NativeLong.SIZE, supportedAtoms[i]);
        }
        x11.XChangeProperty(display, new NativeLong(rootWindow), netSupported, xaAtomAtom, 32, LibX11.PropModeReplace, supMem, supportedAtoms.length);
        x11.XFlush(display);
    }

    public void updateClientList() {
        var windows = core.registry().snapshot();
        if (windows.isEmpty()) {
            x11.XDeleteProperty(display, new NativeLong(rootWindow), netClientList);
        } else {
            Memory mem = new Memory((long) windows.size() * NativeLong.SIZE);
            int idx = 0;
            for (var record : windows) {
                mem.setNativeLong((long) idx * NativeLong.SIZE, new NativeLong(record.id()));
                idx++;
            }
            x11.XChangeProperty(display, new NativeLong(rootWindow), netClientList, xaWindowAtom, 32, LibX11.PropModeReplace, mem, windows.size());
        }
        x11.XFlush(display);
    }

    private long activeWindow = 0;

    public void setActiveWindow(long win) {
        Memory mem = new Memory(NativeLong.SIZE);
        mem.setNativeLong(0, new NativeLong(win));
        x11.XChangeProperty(display, new NativeLong(rootWindow), netActiveWindow, xaWindowAtom, 32, LibX11.PropModeReplace, mem, 1);
        x11.XFlush(display);
    }

    public void focusClient(long win) {
        if (win == 0 || core.registry().find(win).isEmpty()) {
            return;
        }
        if (activeWindow != 0 && activeWindow != win && core.registry().find(activeWindow).isPresent()) {
            grabWindowButtons(activeWindow);
        }
        activeWindow = win;
        ungrabWindowButtons(win);
        core.focus(win);
        core.raise(win);
        setActiveWindow(win);

        // Update active highlight across all decoration frames
        for (FrameWindow frame : clientToFrame.values()) {
            boolean isActive = (frame.clientWindow().longValue() == win);
            if (frame.isActive() != isActive) {
                frame.setActive(isActive);
                frame.render();
                frame.blit(display, gc, screen);
            }
        }
        x11.XFlush(display);
    }

    private void grabWindowButtons(long win) {
        x11.XGrabButton(
            display,
            LibX11.Button1,
            new NativeLong(LibX11.AnyModifier),
            new NativeLong(win),
            1,
            new NativeLong(LibX11.ButtonPressMask),
            LibX11.GrabModeSync,
            LibX11.GrabModeAsync,
            new NativeLong(0),
            new NativeLong(0)
        );
    }

    private void ungrabWindowButtons(long win) {
        x11.XUngrabButton(
            display,
            LibX11.AnyButton,
            new NativeLong(LibX11.AnyModifier),
            new NativeLong(win)
        );
    }

    public String fetchWindowTitle(long win) {
        PointerByReference nameRet = new PointerByReference();
        if (x11.XFetchName(display, new NativeLong(win), nameRet) != 0) {
            Pointer ptr = nameRet.getValue();
            if (ptr != null) {
                try {
                    String title = ptr.getString(0);
                    if (title != null && !title.isBlank()) {
                        return title;
                    }
                } finally {
                    x11.XFree(ptr);
                }
            }
        }
        return "Window";
    }

    public FrameWindow decorateAndReparent(long clientWin, WindowGeometry geom) {
        if (clientToFrame.containsKey(clientWin)) {
            return clientToFrame.get(clientWin);
        }

        String title = fetchWindowTitle(clientWin);
        FrameMetrics m = theme.metrics();

        int clientX = geom.x();
        int clientY = geom.y();
        int clientW = Math.max(100, geom.width());
        int clientH = Math.max(60, geom.height());

        int frameW = m.frameWidth(clientW);
        int frameH = m.frameHeight(clientH);
        int frameX = Math.max(0, clientX);
        int frameY = Math.max(0, clientY);

        NativeLong frameWin = x11.XCreateSimpleWindow(
                display,
                new NativeLong(rootWindow),
                frameX,
                frameY,
                frameW,
                frameH,
                0,
                new NativeLong(0),
                new NativeLong(0)
        );

        if (frameWin == null || frameWin.longValue() == 0) {
            LOGGER.warning("Failed to create frame window for client " + clientWin);
            return null;
        }

        // Add client to save set so if WM stops, client windows are preserved and restored to root
        x11.XAddToSaveSet(display, new NativeLong(clientWin));

        // Select events on frame
        long frameMask = LibX11.ExposureMask
                | LibX11.ButtonPressMask
                | LibX11.ButtonReleaseMask
                | LibX11.PointerMotionMask
                | LibX11.Button1MotionMask
                | LibX11.EnterWindowMask
                | LibX11.LeaveWindowMask
                | LibX11.StructureNotifyMask
                | LibX11.SubstructureRedirectMask
                | LibX11.SubstructureNotifyMask;
        x11.XSelectInput(display, frameWin, new NativeLong(frameMask));

        // Select StructureNotify and PropertyChange on client
        x11.XSelectInput(display, new NativeLong(clientWin), new NativeLong(LibX11.StructureNotifyMask | LibX11.PropertyChangeMask));

        // Reparent client into frame
        x11.XReparentWindow(display, new NativeLong(clientWin), frameWin, m.clientOffsetX(), m.clientOffsetY());

        // Ensure client is sized to inner frame area
        x11.XMoveResizeWindow(display, new NativeLong(clientWin), m.clientOffsetX(), m.clientOffsetY(), clientW, clientH);

        FrameWindow frame = new FrameWindow(
                new NativeLong(clientWin),
                frameWin,
                frameX,
                frameY,
                frameW,
                frameH,
                theme
        );
        frame.setTitle(title);
        frame.render();

        clientToFrame.put(clientWin, frame);
        frameToClient.put(frameWin.longValue(), frame);

        // Map both client and frame
        x11.XMapWindow(display, new NativeLong(clientWin));
        x11.XMapWindow(display, frameWin);

        frame.blit(display, gc, screen);
        sendSyntheticConfigureNotify(frame);
        x11.XFlush(display);

        return frame;
    }

    public void sendSyntheticConfigureNotify(FrameWindow frame) {
        if (frame == null) {
            return;
        }
        long clientWin = frame.clientWindow().longValue();
        LibX11.XConfigureEvent ce = new LibX11.XConfigureEvent();
        ce.type = LibX11.ConfigureNotify;
        ce.serial = new NativeLong(0);
        ce.send_event = 1;
        ce.display = display;
        ce.event = new NativeLong(clientWin);
        ce.window = new NativeLong(clientWin);
        ce.x = frame.frameX() + frame.clientX();
        ce.y = frame.frameY() + frame.clientY();
        ce.width = frame.clientWidth();
        ce.height = frame.clientHeight();
        ce.border_width = 0;
        ce.above = new NativeLong(0);
        ce.override_redirect = 0;
        ce.write();

        x11.XSendEvent(display, new NativeLong(clientWin), 0, new NativeLong(LibX11.StructureNotifyMask), ce.getPointer());
        x11.XFlush(display);
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
                if (child == 0 || child == rootWindow || child == checkWindow || frameToClient.containsKey(child)) {
                    continue;
                }
                LibX11.XWindowAttributes attrs = new LibX11.XWindowAttributes();
                if (x11.XGetWindowAttributes(display, new NativeLong(child), attrs) != 0) {
                    if (attrs.override_redirect == 0 && attrs.map_state == 2 /* IsViewable */) {
                        WindowGeometry geom = new WindowGeometry(attrs.x, attrs.y, attrs.width, attrs.height);
                        FrameWindow frame = decorateAndReparent(child, geom);
                        WindowGeometry managedGeom = (frame != null)
                                ? new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight())
                                : geom;
                        core.manage(child, managedGeom);
                        grabWindowButtons(child);
                    }
                }
            }
            x11.XFree(childrenReturn.getValue());
            core.arrange(screenWidth, screenHeight);
            updateClientList();
            var managed = core.registry().snapshot();
            if (!managed.isEmpty()) {
                focusClient(managed.iterator().next().id());
            }
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

    void handleEvent(LibX11.XEvent event) {
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

                // Check for transient window hint (dialogs/popups)
                Memory parentMem = new Memory(NativeLong.SIZE);
                long transientFor = 0;
                if (x11.XGetTransientForHint(display, new NativeLong(win), parentMem) != 0) {
                    transientFor = parentMem.getNativeLong(0).longValue();
                }

                FrameMetrics m = theme.metrics();
                int clientW = Math.max(100, w);
                int clientH = Math.max(60, h);
                int frameW = m.frameWidth(clientW);
                int frameH = m.frameHeight(clientH);

                int frameX, frameY;
                var parentRecord = (transientFor != 0) ? core.registry().find(transientFor) : Optional.<WindowRecord>empty();
                if (parentRecord.isPresent()) {
                    WindowGeometry parentGeom = parentRecord.get().geometry();
                    frameX = parentGeom.x() + (parentGeom.width() - frameW) / 2;
                    frameY = parentGeom.y() + (parentGeom.height() - frameH) / 2;
                    frameX = Math.max(10, Math.min(frameX, screenWidth - frameW - 10));
                    frameY = Math.max(10, Math.min(frameY, screenHeight - frameH - 10));
                } else {
                    frameX = Math.max(0, x);
                    frameY = Math.max(0, y);
                }
                WindowGeometry initialGeom = new WindowGeometry(frameX, frameY, clientW, clientH);

                FrameWindow frame = decorateAndReparent(win, initialGeom);
                WindowGeometry managedGeom = (frame != null)
                        ? new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight())
                        : initialGeom;

                core.manage(win, managedGeom);
                if (parentRecord.isPresent()) {
                    core.adapter().configureWindow(win, managedGeom);
                } else {
                    core.arrange(screenWidth, screenHeight);
                }

                grabWindowButtons(win);
                focusClient(win);
                updateClientList();
                long finalTransientFor = transientFor;
                LOGGER.info(() -> "Managed reparented client window: " + win + " geometry=" + managedGeom
                        + (finalTransientFor != 0 ? " (transient for " + finalTransientFor + ")" : ""));
            }
            case LibX11.ButtonPress -> {
                LibX11.XButtonEvent btnEvent = new LibX11.XButtonEvent(event.getPointer());
                long eventWin = btnEvent.window.longValue();

                FrameWindow frameByFrame = frameToClient.get(eventWin);
                if (frameByFrame != null) {
                    handleFrameButtonPress(frameByFrame, btnEvent);
                    return;
                }

                if (core.registry().find(eventWin).isPresent()) {
                    focusClient(eventWin);
                    LOGGER.fine(() -> "ButtonPress: focused and raised client window " + eventWin);
                }
                x11.XAllowEvents(display, LibX11.ReplayPointer, btnEvent.time != null ? btnEvent.time : new NativeLong(LibX11.CurrentTime));
                x11.XFlush(display);
            }
            case LibX11.ButtonRelease -> {
                LibX11.XButtonEvent btnEvent = new LibX11.XButtonEvent(event.getPointer());
                handleFrameButtonRelease(btnEvent);
            }
            case LibX11.MotionNotify -> {
                LibX11.XMotionEvent motion = new LibX11.XMotionEvent(event.getPointer());
                handleFrameMotion(motion);
            }
            case LibX11.Expose -> {
                LibX11.XExposeEvent exp = new LibX11.XExposeEvent(event.getPointer());
                if (exp.count == 0) {
                    FrameWindow frame = frameToClient.get(exp.window.longValue());
                    if (frame != null) {
                        frame.render();
                        frame.blit(display, gc, screen);
                        x11.XFlush(display);
                    }
                }
            }
            case LibX11.LeaveNotify -> {
                LibX11.XAnyEvent any = new LibX11.XAnyEvent(event.getPointer());
                FrameWindow frame = frameToClient.get(any.window.longValue());
                if (frame != null && frame.hoveredButton() != null && interactionType == InteractionType.NONE) {
                    frame.setHoveredButton(null);
                    frame.render();
                    frame.blit(display, gc, screen);
                    x11.XFlush(display);
                }
            }
            case LibX11.PropertyNotify -> {
                LibX11.XAnyEvent any = new LibX11.XAnyEvent(event.getPointer());
                long win = any.window.longValue();
                FrameWindow frame = clientToFrame.get(win);
                if (frame != null) {
                    String newTitle = fetchWindowTitle(win);
                    if (!newTitle.equals(frame.title())) {
                        frame.setTitle(newTitle);
                        frame.render();
                        frame.blit(display, gc, screen);
                        x11.XFlush(display);
                    }
                }
            }
            case LibX11.ClientMessage -> {
                LibX11.XClientMessageEvent cmsg = new LibX11.XClientMessageEvent(event.getPointer());
                long win = cmsg.window.longValue();
                if (cmsg.message_type != null && cmsg.message_type.equals(netCloseWindow)) {
                    core.close(win);
                    LOGGER.info(() -> "ClientMessage _NET_CLOSE_WINDOW handled for " + win);
                } else if (cmsg.message_type != null && cmsg.message_type.equals(netActiveWindow)) {
                    focusClient(win);
                    LOGGER.info(() -> "ClientMessage _NET_ACTIVE_WINDOW handled for " + win);
                }
            }
            case LibX11.ConfigureRequest -> {
                LibX11.XConfigureRequestEvent cfg = new LibX11.XConfigureRequestEvent(event.getPointer());
                long win = cfg.window.longValue();
                FrameWindow frame = clientToFrame.get(win);
                if (frame != null) {
                    FrameMetrics m = theme.metrics();
                    long mask = (cfg.value_mask != null) ? cfg.value_mask.longValue() : 0L;
                    int clientW = ((mask & LibX11.CWWidth) != 0) ? Math.max(100, cfg.width) : frame.clientWidth();
                    int clientH = ((mask & LibX11.CWHeight) != 0) ? Math.max(60, cfg.height) : frame.clientHeight();
                    int frameW = m.frameWidth(clientW);
                    int frameH = m.frameHeight(clientH);

                    int frameX = frame.frameX();
                    int frameY = frame.frameY();
                    if ((mask & LibX11.CWX) != 0) {
                        frameX = cfg.x;
                    }
                    if ((mask & LibX11.CWY) != 0) {
                        frameY = cfg.y;
                    }

                    frame.setBounds(frameX, frameY, frameW, frameH);
                    x11.XMoveResizeWindow(display, frame.frameWindow(), frameX, frameY, frameW, frameH);
                    x11.XMoveResizeWindow(display, frame.clientWindow(), frame.clientX(), frame.clientY(), frame.clientWidth(), frame.clientHeight());
                    frame.render();
                    frame.blit(display, gc, screen);

                    sendSyntheticConfigureNotify(frame);

                    core.registry().updateGeometry(win, new WindowGeometry(frameX, frameY, frameW, frameH));
                    x11.XFlush(display);
                } else {
                    LibX11.XWindowChanges changes = new LibX11.XWindowChanges();
                    changes.x = cfg.x;
                    changes.y = cfg.y;
                    changes.width = cfg.width;
                    changes.height = cfg.height;
                    changes.border_width = cfg.border_width;
                    changes.sibling = cfg.above;
                    changes.stack_mode = cfg.detail;
                    x11.XConfigureWindow(display, new NativeLong(win), cfg.value_mask != null ? cfg.value_mask : new NativeLong(0), changes);
                    x11.XFlush(display);
                }
            }
            case LibX11.DestroyNotify -> {
                LibX11.XDestroyWindowEvent dest = new LibX11.XDestroyWindowEvent(event.getPointer());
                long win = dest.window.longValue();
                cleanupWindow(win);
                LOGGER.info(() -> "Unmanaged destroyed window: " + win);
            }
            case LibX11.UnmapNotify -> {
                LibX11.XUnmapEvent unmap = new LibX11.XUnmapEvent(event.getPointer());
                long win = unmap.window.longValue();
                if (frameToClient.containsKey(win)) {
                    // Frame itself unmapped (e.g. iconified), ignore
                    return;
                }
                FrameWindow frame = clientToFrame.get(win);
                if (frame != null && frame.isMinimized()) {
                    return;
                }
                cleanupWindow(win);
                LOGGER.info(() -> "Unmanaged unmapped window: " + win);
            }
            default -> {
                // Ignore unhandled events
            }
        }
    }

    private void toggleWireframe(int x, int y, int w, int h) {
        x11.XDrawRectangle(display, new NativeLong(rootWindow), xorGc, x, y, Math.max(1, w), Math.max(1, h));
        int tbh = theme.metrics().titleBarHeight();
        if (h > tbh) {
            x11.XDrawLine(display, new NativeLong(rootWindow), xorGc, x, y + tbh, x + w, y + tbh);
        }
    }

    private void handleFrameButtonPress(FrameWindow frame, LibX11.XButtonEvent btnEvent) {
        long clientWin = frame.clientWindow().longValue();
        focusClient(clientWin);

        int localX = btnEvent.x;
        int localY = btnEvent.y;

        // 1. Check window buttons
        Optional<WindowButton> btn = frame.hitTestButton(localX, localY);
        if (btn.isPresent() && btnEvent.button == LibX11.Button1) {
            interactionType = InteractionType.BUTTON_PRESS;
            interactionFrame = frame;
            currentPressedButton = btn.get();
            frame.setPressedButton(currentPressedButton);
            frame.render();
            frame.blit(display, gc, screen);
            x11.XFlush(display);
            return;
        }

        // 2. Check titlebar drag zone (wireframe dragging)
        if (frame.isTitleBarDragZone(localX, localY) && btnEvent.button == LibX11.Button1) {
            interactionType = InteractionType.MOVING;
            interactionFrame = frame;
            dragStartRootX = btnEvent.x_root;
            dragStartRootY = btnEvent.y_root;
            dragStartFrameX = frame.frameX();
            dragStartFrameY = frame.frameY();
            dragStartFrameW = frame.frameWidth();
            dragStartFrameH = frame.frameHeight();

            // Draw initial wireframe outline
            wireframeX = dragStartFrameX;
            wireframeY = dragStartFrameY;
            wireframeW = dragStartFrameW;
            wireframeH = dragStartFrameH;
            toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);
            wireframeActive = true;

            long grabMask = LibX11.ButtonReleaseMask | LibX11.PointerMotionMask | LibX11.Button1MotionMask;
            x11.XGrabPointer(display, frame.frameWindow(), 0, new NativeLong(grabMask),
                    LibX11.GrabModeAsync, LibX11.GrabModeAsync, new NativeLong(0), cursorMove, new NativeLong(LibX11.CurrentTime));
            x11.XFlush(display);
            return;
        }

        // 3. Check border resize zones (wireframe resizing)
        ResizeDirection dir = frame.hitTestBorder(localX, localY);
        if (dir != ResizeDirection.NONE && btnEvent.button == LibX11.Button1) {
            interactionType = InteractionType.RESIZING;
            interactionFrame = frame;
            resizeDirection = dir;
            dragStartRootX = btnEvent.x_root;
            dragStartRootY = btnEvent.y_root;
            dragStartFrameX = frame.frameX();
            dragStartFrameY = frame.frameY();
            dragStartFrameW = frame.frameWidth();
            dragStartFrameH = frame.frameHeight();

            // Draw initial wireframe outline
            wireframeX = dragStartFrameX;
            wireframeY = dragStartFrameY;
            wireframeW = dragStartFrameW;
            wireframeH = dragStartFrameH;
            toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);
            wireframeActive = true;

            NativeLong dirCursor = getCursorForDirection(dir);
            long grabMask = LibX11.ButtonReleaseMask | LibX11.PointerMotionMask | LibX11.Button1MotionMask;
            x11.XGrabPointer(display, frame.frameWindow(), 0, new NativeLong(grabMask),
                    LibX11.GrabModeAsync, LibX11.GrabModeAsync, new NativeLong(0), dirCursor, new NativeLong(LibX11.CurrentTime));
            x11.XFlush(display);
        }
    }

    private void handleFrameButtonRelease(LibX11.XButtonEvent btnEvent) {
        if (interactionType == InteractionType.MOVING || interactionType == InteractionType.RESIZING) {
            if (wireframeActive) {
                // Erase active wireframe
                toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);
                wireframeActive = false;
            }
            x11.XUngrabPointer(display, new NativeLong(LibX11.CurrentTime));

            if (interactionFrame != null) {
                if (interactionType == InteractionType.MOVING) {
                    interactionFrame.setBounds(wireframeX, wireframeY, wireframeW, wireframeH);
                    x11.XMoveWindow(display, interactionFrame.frameWindow(), wireframeX, wireframeY);
                } else {
                    interactionFrame.setBounds(wireframeX, wireframeY, wireframeW, wireframeH);
                    x11.XMoveResizeWindow(display, interactionFrame.frameWindow(), wireframeX, wireframeY, wireframeW, wireframeH);
                    x11.XMoveResizeWindow(display, interactionFrame.clientWindow(),
                            interactionFrame.clientX(), interactionFrame.clientY(),
                            interactionFrame.clientWidth(), interactionFrame.clientHeight());
                    interactionFrame.render();
                    interactionFrame.blit(display, gc, screen);
                }

                long clientWin = interactionFrame.clientWindow().longValue();
                core.registry().updateGeometry(clientWin, new WindowGeometry(
                        wireframeX, wireframeY,
                        wireframeW, wireframeH));
                x11.XDefineCursor(display, interactionFrame.frameWindow(), cursorDefault);
                sendSyntheticConfigureNotify(interactionFrame);
            }
            interactionType = InteractionType.NONE;
            interactionFrame = null;
            resizeDirection = ResizeDirection.NONE;
            x11.XFlush(display);
            return;
        }

        if (interactionType == InteractionType.BUTTON_PRESS && interactionFrame != null) {
            FrameWindow frame = interactionFrame;
            WindowButton pressed = currentPressedButton;
            frame.setPressedButton(null);
            interactionType = InteractionType.NONE;
            interactionFrame = null;
            currentPressedButton = null;

            frame.render();
            frame.blit(display, gc, screen);

            Optional<WindowButton> releasedOver = frame.hitTestButton(btnEvent.x, btnEvent.y);
            if (releasedOver.isPresent() && releasedOver.get() == pressed) {
                handleButtonClick(frame, pressed);
            }
            x11.XFlush(display);
        }
    }

    private void handleFrameMotion(LibX11.XMotionEvent motion) {
        if (interactionType == InteractionType.MOVING && interactionFrame != null) {
            int dx = motion.x_root - dragStartRootX;
            int dy = motion.y_root - dragStartRootY;
            int newX = dragStartFrameX + dx;
            int newY = dragStartFrameY + dy;

            if (wireframeActive && (newX != wireframeX || newY != wireframeY)) {
                // Erase previous wireframe
                toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);
                // Update position
                wireframeX = newX;
                wireframeY = newY;
                // Draw new wireframe
                toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);
                x11.XFlush(display);
            }
            return;
        }

        if (interactionType == InteractionType.RESIZING && interactionFrame != null) {
            int dx = motion.x_root - dragStartRootX;
            int dy = motion.y_root - dragStartRootY;

            FrameMetrics m = interactionFrame.metrics();
            int minW = Math.max(140, m.borderWidth() * 2 + 100);
            int minH = Math.max(60, m.titleBarHeight() + m.borderWidth() + 20);

            int fx = dragStartFrameX;
            int fy = dragStartFrameY;
            int fw = dragStartFrameW;
            int fh = dragStartFrameH;

            if (resizeDirection == ResizeDirection.E || resizeDirection == ResizeDirection.NE || resizeDirection == ResizeDirection.SE) {
                fw = Math.max(minW, dragStartFrameW + dx);
            } else if (resizeDirection == ResizeDirection.W || resizeDirection == ResizeDirection.NW || resizeDirection == ResizeDirection.SW) {
                fw = Math.max(minW, dragStartFrameW - dx);
                fx = dragStartFrameX + (dragStartFrameW - fw);
            }

            if (resizeDirection == ResizeDirection.S || resizeDirection == ResizeDirection.SE || resizeDirection == ResizeDirection.SW) {
                fh = Math.max(minH, dragStartFrameH + dy);
            } else if (resizeDirection == ResizeDirection.N || resizeDirection == ResizeDirection.NE || resizeDirection == ResizeDirection.NW) {
                fh = Math.max(minH, dragStartFrameH - dy);
                fy = dragStartFrameY + (dragStartFrameH - fh);
            }

            if (wireframeActive && (fx != wireframeX || fy != wireframeY || fw != wireframeW || fh != wireframeH)) {
                // Erase previous wireframe
                toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);
                // Update bounds
                wireframeX = fx;
                wireframeY = fy;
                wireframeW = fw;
                wireframeH = fh;
                // Draw new wireframe
                toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);
                x11.XFlush(display);
            }
            return;
        }

        // Pointer hovering over frame
        FrameWindow frame = frameToClient.get(motion.window.longValue());
        if (frame != null && interactionType == InteractionType.NONE) {
            // Hover button highlight
            Optional<WindowButton> hovered = frame.hitTestButton(motion.x, motion.y);
            WindowButton h = hovered.orElse(null);
            if (h != frame.hoveredButton()) {
                frame.setHoveredButton(h);
                frame.render();
                frame.blit(display, gc, screen);
            }

            // Directional cursor
            ResizeDirection dir = frame.hitTestBorder(motion.x, motion.y);
            NativeLong targetCursor = getCursorForDirection(dir);
            x11.XDefineCursor(display, frame.frameWindow(), targetCursor);
            x11.XFlush(display);
        }
    }

    private void handleButtonClick(FrameWindow frame, WindowButton button) {
        long clientWin = frame.clientWindow().longValue();
        switch (button) {
            case CLOSE -> {
                LOGGER.info(() -> "Close button clicked for window " + clientWin);
                core.close(clientWin);
            }
            case MAXIMIZE -> {
                LOGGER.info(() -> "Maximize button clicked for window " + clientWin);
                toggleMaximize(frame);
            }
            case MINIMIZE -> {
                LOGGER.info(() -> "Minimize button clicked for window " + clientWin);
                minimizeWindow(frame);
            }
            case MENU -> {
                LOGGER.info(() -> "Menu button clicked for window " + clientWin);
                core.close(clientWin);
            }
        }
    }

    private void toggleMaximize(FrameWindow frame) {
        if (!frame.isMaximized()) {
            frame.setUnmaximizedBounds(new Rectangle(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight()));
            frame.setBounds(0, 0, screenWidth, screenHeight);
            frame.setMaximized(true);
        } else {
            Rectangle r = frame.unmaximizedBounds();
            if (r == null) {
                r = new Rectangle(50, 50, 600, 400);
            }
            frame.setBounds(r.x, r.y, r.width, r.height);
            frame.setMaximized(false);
        }
        x11.XMoveResizeWindow(display, frame.frameWindow(), frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight());
        x11.XMoveResizeWindow(display, frame.clientWindow(),
                frame.clientX(), frame.clientY(), frame.clientWidth(), frame.clientHeight());
        frame.render();
        frame.blit(display, gc, screen);
        core.registry().updateGeometry(frame.clientWindow().longValue(),
                new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight()));
        x11.XFlush(display);
    }

    private void minimizeWindow(FrameWindow frame) {
        frame.setMinimized(true);
        x11.XUnmapWindow(display, frame.frameWindow());
        long clientWin = frame.clientWindow().longValue();
        if (activeWindow == clientWin) {
            activeWindow = 0;
            var remaining = core.registry().snapshot();
            for (var rec : remaining) {
                if (rec.id() != clientWin) {
                    FrameWindow other = clientToFrame.get(rec.id());
                    if (other != null && !other.isMinimized()) {
                        focusClient(rec.id());
                        break;
                    }
                }
            }
        }
        x11.XFlush(display);
    }

    private void cleanupWindow(long win) {
        FrameWindow frame = clientToFrame.remove(win);
        if (frame != null) {
            frameToClient.remove(frame.frameWindow().longValue());
            x11.XDestroyWindow(display, frame.frameWindow());
        }
        core.unmanage(win);
        if (activeWindow == win) {
            activeWindow = 0;
            var remaining = core.registry().snapshot();
            if (!remaining.isEmpty()) {
                focusClient(remaining.iterator().next().id());
            }
        }
        updateClientList();
        LOGGER.info(() -> "Cleaned up window: " + win);
    }

    @Override
    public void close() {
        stop();
        if (display != null) {
            if (cursorDefault != null) x11.XFreeCursor(display, cursorDefault);
            if (cursorMove != null) x11.XFreeCursor(display, cursorMove);
            if (cursorN != null) x11.XFreeCursor(display, cursorN);
            if (cursorS != null) x11.XFreeCursor(display, cursorS);
            if (cursorW != null) x11.XFreeCursor(display, cursorW);
            if (cursorE != null) x11.XFreeCursor(display, cursorE);
            if (cursorNW != null) x11.XFreeCursor(display, cursorNW);
            if (cursorNE != null) x11.XFreeCursor(display, cursorNE);
            if (cursorSW != null) x11.XFreeCursor(display, cursorSW);
            if (cursorSE != null) x11.XFreeCursor(display, cursorSE);
            if (gc != null) {
                x11.XFreeGC(display, gc);
            }
            if (xorGc != null) {
                x11.XFreeGC(display, xorGc);
            }
            if (checkWindow != 0) {
                x11.XDestroyWindow(display, new NativeLong(checkWindow));
            }
            x11.XCloseDisplay(display);
        }
    }
}
