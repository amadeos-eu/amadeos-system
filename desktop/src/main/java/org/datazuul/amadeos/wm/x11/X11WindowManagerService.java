package org.datazuul.amadeos.wm.x11;

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
import javax.swing.SwingUtilities;
import org.datazuul.amadeos.wm.FloatingLayoutPolicy;
import org.datazuul.amadeos.wm.WindowGeometry;
import org.datazuul.amadeos.wm.WindowManagerCore;
import org.datazuul.amadeos.wm.WindowRecord;
import org.datazuul.amadeos.wm.WindowRegistry;
import org.datazuul.amadeos.wm.decoration.DecorationTheme;
import org.datazuul.amadeos.wm.decoration.DesktopIconTile;
import org.datazuul.amadeos.wm.decoration.FrameMetrics;
import org.datazuul.amadeos.wm.decoration.FrameWindow;
import org.datazuul.amadeos.wm.decoration.MotifDecorationTheme;
import org.datazuul.amadeos.wm.decoration.ResizeDirection;
import org.datazuul.amadeos.wm.decoration.WindowButton;
import org.datazuul.amadeos.lock.LockLauncher;
import org.datazuul.amadeos.lock.LockSelection;
import org.datazuul.amadeos.wm.menu.DesktopMenuActions;
import org.datazuul.amadeos.wm.menu.DesktopRootMenu;
import org.datazuul.amadeos.wm.theme.DesktopTheme;
import org.datazuul.amadeos.wm.theme.ThemeManager;
import org.datazuul.amadeos.wm.theme.WallpaperManager;
import org.datazuul.amadeos.wm.ui.SessionDialogHelper;

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
    private final NativeLong netWmWindowType;
    private final NativeLong netWmWindowTypeDock;
    private final NativeLong netWmWindowTypeNormal;
    private final NativeLong netWmState;
    private final NativeLong netWmStateHidden;
    private final NativeLong netWmStrut;
    private final NativeLong netWmStrutPartial;
    private final NativeLong wmChangeState;
    private final NativeLong wmState;
    private final NativeLong utf8String;
    private final NativeLong amadeosThemeAtom;
    private final NativeLong netNumberOfDesktops;
    private final NativeLong netCurrentDesktop;
    private final NativeLong netDesktopNames;
    private final NativeLong netWmDesktop;
    private final NativeLong xaWindowAtom;
    private final NativeLong xaAtomAtom;
    private final NativeLong xaCardinalAtom;

    public static final int NUMBER_OF_DESKTOPS = 4;
    public static final long STICKY_DESKTOP = 0xFFFFFFFFL;
    private int currentDesktop = 0;
    private final Map<Long, Integer> clientToDesktop = new HashMap<>();
    private final DesktopRootMenu desktopMenu;

    private final java.util.Set<Long> dockWindows = new java.util.HashSet<>();
    private int dockMarginBottom = 0;

    // CDE Desktop Minimized Icon Tiles (top-left, vertically stacked with 0px spacing)
    private final java.util.List<DesktopIconTile> iconTiles = new java.util.ArrayList<>();
    private final Map<Long, DesktopIconTile> tileWinToTile = new HashMap<>();
    private final Map<Long, DesktopIconTile> clientToTile = new HashMap<>();

    private int lockKeycode = 0;

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
        this.netWmWindowType = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE", 0);
        this.netWmWindowTypeDock = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE_DOCK", 0);
        this.netWmWindowTypeNormal = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE_NORMAL", 0);
        this.netWmState = x11.XInternAtom(display, "_NET_WM_STATE", 0);
        this.netWmStateHidden = x11.XInternAtom(display, "_NET_WM_STATE_HIDDEN", 0);
        this.netWmStrut = x11.XInternAtom(display, "_NET_WM_STRUT", 0);
        this.netWmStrutPartial = x11.XInternAtom(display, "_NET_WM_STRUT_PARTIAL", 0);
        this.wmChangeState = x11.XInternAtom(display, "WM_CHANGE_STATE", 0);
        this.wmState = x11.XInternAtom(display, "WM_STATE", 0);
        this.utf8String = x11.XInternAtom(display, "UTF8_STRING", 0);
        this.amadeosThemeAtom = x11.XInternAtom(display, "_AMADEOS_THEME", 0);
        this.netNumberOfDesktops = x11.XInternAtom(display, "_NET_NUMBER_OF_DESKTOPS", 0);
        this.netCurrentDesktop = x11.XInternAtom(display, "_NET_CURRENT_DESKTOP", 0);
        this.netDesktopNames = x11.XInternAtom(display, "_NET_DESKTOP_NAMES", 0);
        this.netWmDesktop = x11.XInternAtom(display, "_NET_WM_DESKTOP", 0);
        this.xaWindowAtom = new NativeLong(LibX11.XA_WINDOW);
        this.xaAtomAtom = new NativeLong(LibX11.XA_ATOM);
        this.xaCardinalAtom = new NativeLong(LibX11.XA_CARDINAL);

        // Native Root Cursor Polish: set left_ptr arrow cursor on root window
        x11.XDefineCursor(display, new NativeLong(rootWindow), cursorDefault);

        // Root Window button grab for Desktop Root Menu (Button 3)
        x11.XGrabButton(
                display,
                LibX11.Button3,
                new NativeLong(LibX11.AnyModifier),
                new NativeLong(rootWindow),
                1,
                new NativeLong(LibX11.ButtonPressMask),
                LibX11.GrabModeAsync,
                LibX11.GrabModeAsync,
                new NativeLong(0),
                new NativeLong(0)
        );

        // Global shortcut: Super+L (Mod4Mask + 'l') to lock screen
        try {
            byte kc = x11.XKeysymToKeycode(display, new NativeLong(LibX11.XK_l));
            this.lockKeycode = Byte.toUnsignedInt(kc);
            if (this.lockKeycode != 0) {
                long[] modMasks = {
                    LibX11.Mod4Mask,
                    LibX11.Mod4Mask | LibX11.Mod2Mask, // NumLock
                    LibX11.Mod4Mask | LibX11.LockMask, // CapsLock
                    LibX11.Mod4Mask | LibX11.Mod2Mask | LibX11.LockMask
                };
                for (long mask : modMasks) {
                    x11.XGrabKey(
                            display,
                            this.lockKeycode,
                            new NativeLong(mask),
                            new NativeLong(rootWindow),
                            1,
                            LibX11.GrabModeAsync,
                            LibX11.GrabModeAsync
                    );
                }
                LOGGER.fine(() -> "Registered global Super+L lock screen shortcut (keycode=" + lockKeycode + ")");
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to bind Super+L screen lock shortcut: " + e.getMessage());
        }

        this.desktopMenu = new DesktopRootMenu(new DesktopMenuActions() {
            @Override
            public void switchWorkspace(int workspaceIndex) {
                X11WindowManagerService.this.switchDesktop(workspaceIndex);
            }

            @Override
            public int currentWorkspace() {
                return X11WindowManagerService.this.currentDesktop();
            }

            @Override
            public void shuffleUp() {
                X11WindowManagerService.this.shuffleUp();
            }

            @Override
            public void shuffleDown() {
                X11WindowManagerService.this.shuffleDown();
            }

            @Override
            public void minimizeAll() {
                X11WindowManagerService.this.minimizeAllCurrentWorkspace();
            }

            @Override
            public void restoreAll() {
                X11WindowManagerService.this.restoreAllCurrentWorkspace();
            }

            @Override
            public void refreshDesktop() {
                X11WindowManagerService.this.refreshDesktop();
            }

            @Override
            public void openSettings() {
                SwingUtilities.invokeLater(() -> new org.datazuul.amadeos.wm.settings.AmadeSettingsApplication().setVisible(true));
            }

            @Override
            public void lockScreen() {
                SessionDialogHelper.lockScreen(null);
            }

            @Override
            public void logoutSession() {
                SessionDialogHelper.showSessionPowerDialog(null);
            }
        });

        NativeLong createdCheck = x11.XCreateSimpleWindow(
                display, new NativeLong(rootWindow), 0, 0, 1, 1, 0, new NativeLong(0), new NativeLong(0));
        this.checkWindow = createdCheck != null ? createdCheck.longValue() : 0;
        initEwmh();

        ThemeManager.addListener(this::applyThemeGlobally);

        try {
            int depth = x11.XDefaultDepth(display, screen);
            Pointer visual = x11.XDefaultVisual(display, screen);
            WallpaperManager.applyToRootWindow(x11, display, rootWindow, screenWidth, screenHeight, depth, visual);
        } catch (Exception e) {
            LOGGER.fine("Wallpaper init deferred: " + e.getMessage());
        }
    }

    public Pointer display() {
        return display;
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

    public int dockMarginBottom() {
        return dockMarginBottom;
    }

    public void setDockMarginBottom(int margin) {
        this.dockMarginBottom = Math.max(0, margin);
    }

    public int currentDesktop() {
        return currentDesktop;
    }

    public boolean isSticky(int desktop) {
        return desktop == (int) STICKY_DESKTOP || desktop == -1;
    }

    public int getClientDesktop(long win) {
        return clientToDesktop.getOrDefault(win, currentDesktop);
    }

    public int readClientDesktopProperty(long win) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        com.sun.jna.ptr.NativeLongByReference nitemsRet = new com.sun.jna.ptr.NativeLongByReference();
        com.sun.jna.ptr.NativeLongByReference bytesAfterRet = new com.sun.jna.ptr.NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(win), netWmDesktop,
                new NativeLong(0), new NativeLong(1), 0,
                xaCardinalAtom,
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            Pointer ptr = propRet.getValue();
            try {
                return (int) ptr.getNativeLong(0).longValue();
            } finally {
                x11.XFree(ptr);
            }
        }
        return -1;
    }

    public void writeClientDesktopProperty(long win, int desktopIndex) {
        Memory mem = new Memory(NativeLong.SIZE);
        mem.setNativeLong(0, new NativeLong(desktopIndex));
        x11.XChangeProperty(display, new NativeLong(win), netWmDesktop, xaCardinalAtom, 32, LibX11.PropModeReplace, mem, 1);
        x11.XFlush(display);
    }

    public void switchDesktop(int targetDesktop) {
        if (targetDesktop < 0 || targetDesktop >= NUMBER_OF_DESKTOPS || targetDesktop == currentDesktop) {
            return;
        }
        int prevDesktop = currentDesktop;
        currentDesktop = targetDesktop;

        // Update root property _NET_CURRENT_DESKTOP
        Memory curMem = new Memory(NativeLong.SIZE);
        curMem.setNativeLong(0, new NativeLong(currentDesktop));
        x11.XChangeProperty(display, new NativeLong(rootWindow), netCurrentDesktop, xaCardinalAtom, 32, LibX11.PropModeReplace, curMem, 1);

        // Map/Unmap client windows based on active desktop
        for (Map.Entry<Long, FrameWindow> entry : clientToFrame.entrySet()) {
            long clientWin = entry.getKey();
            FrameWindow frame = entry.getValue();
            int winDesktop = getClientDesktop(clientWin);
            boolean sticky = isSticky(winDesktop);

            if (winDesktop == currentDesktop || sticky) {
                if (!frame.isMinimized()) {
                    x11.XMapWindow(display, frame.frameWindow());
                    frame.render();
                    frame.blit(display, gc, screen);
                    sendSyntheticConfigureNotify(frame);
                }
            } else {
                x11.XUnmapWindow(display, frame.frameWindow());
            }
        }

        // Map/Unmap minimized icon tiles based on active desktop
        for (DesktopIconTile tile : iconTiles) {
            int winDesktop = getClientDesktop(tile.clientWin());
            boolean sticky = isSticky(winDesktop);
            if (winDesktop == currentDesktop || sticky) {
                x11.XMapWindow(display, tile.tileWin());
                tile.blit(x11, display, gc, screen);
            } else {
                x11.XUnmapWindow(display, tile.tileWin());
            }
        }

        // Update active window
        if (activeWindow != 0 && getClientDesktop(activeWindow) != currentDesktop && !isSticky(getClientDesktop(activeWindow))) {
            activeWindow = 0;
            focusTopClientOnCurrentDesktop();
        } else if (activeWindow == 0) {
            focusTopClientOnCurrentDesktop();
        }

        x11.XFlush(display);
        LOGGER.info(() -> "Switched to workspace " + (currentDesktop + 1) + " (from workspace " + (prevDesktop + 1) + ")");
    }

    private void focusTopClientOnCurrentDesktop() {
        var managed = new java.util.ArrayList<>(core.registry().snapshot());
        for (int i = managed.size() - 1; i >= 0; i--) {
            long win = managed.get(i).id();
            FrameWindow frame = clientToFrame.get(win);
            if (frame != null && !frame.isMinimized()) {
                int desk = getClientDesktop(win);
                if (desk == currentDesktop || isSticky(desk)) {
                    focusClient(win);
                    return;
                }
            }
        }
        setActiveWindow(0);
    }

    public void setClientDesktop(long clientWin, int newDesktop) {
        if ((newDesktop < 0 || newDesktop >= NUMBER_OF_DESKTOPS) && newDesktop != (int) STICKY_DESKTOP) {
            return;
        }
        clientToDesktop.put(clientWin, newDesktop);
        writeClientDesktopProperty(clientWin, newDesktop);

        FrameWindow frame = clientToFrame.get(clientWin);
        if (frame != null) {
            boolean belongsToCurrent = (newDesktop == currentDesktop || isSticky(newDesktop));
            if (belongsToCurrent) {
                if (!frame.isMinimized()) {
                    x11.XMapWindow(display, frame.frameWindow());
                    frame.render();
                    frame.blit(display, gc, screen);
                    sendSyntheticConfigureNotify(frame);
                }
            } else {
                x11.XUnmapWindow(display, frame.frameWindow());
                if (activeWindow == clientWin) {
                    activeWindow = 0;
                    focusTopClientOnCurrentDesktop();
                }
            }
        }

        DesktopIconTile tile = clientToTile.get(clientWin);
        if (tile != null) {
            boolean belongsToCurrent = (newDesktop == currentDesktop || isSticky(newDesktop));
            if (belongsToCurrent) {
                x11.XMapWindow(display, tile.tileWin());
                tile.blit(x11, display, gc, screen);
            } else {
                x11.XUnmapWindow(display, tile.tileWin());
            }
        }
        x11.XFlush(display);
    }

    public void shuffleUp() {
        var managed = core.registry().snapshot();
        for (var rec : managed) {
            long win = rec.id();
            FrameWindow frame = clientToFrame.get(win);
            if (frame != null && !frame.isMinimized()) {
                int desk = getClientDesktop(win);
                if (desk == currentDesktop || isSticky(desk)) {
                    core.raise(win);
                    focusClient(win);
                    LOGGER.info(() -> "Shuffle Up raised and focused window: " + win);
                    return;
                }
            }
        }
    }

    public void shuffleDown() {
        if (activeWindow != 0) {
            long winToLower = activeWindow;
            core.lower(winToLower);
            LOGGER.info(() -> "Shuffle Down lowered window: " + winToLower);
            focusTopClientOnCurrentDesktop();
        }
    }

    public void minimizeAllCurrentWorkspace() {
        for (Map.Entry<Long, FrameWindow> entry : new java.util.ArrayList<>(clientToFrame.entrySet())) {
            long win = entry.getKey();
            FrameWindow frame = entry.getValue();
            if (frame != null && !frame.isMinimized()) {
                int desk = getClientDesktop(win);
                if (desk == currentDesktop || isSticky(desk)) {
                    minimizeWindow(frame);
                }
            }
        }
    }

    public void restoreAllCurrentWorkspace() {
        for (Map.Entry<Long, FrameWindow> entry : new java.util.ArrayList<>(clientToFrame.entrySet())) {
            long win = entry.getKey();
            FrameWindow frame = entry.getValue();
            if (frame != null && frame.isMinimized()) {
                int desk = getClientDesktop(win);
                if (desk == currentDesktop || isSticky(desk)) {
                    restoreWindow(frame);
                }
            }
        }
    }

    public void refreshDesktop() {
        x11.XClearWindow(display, new NativeLong(rootWindow));
        try {
            int depth = x11.XDefaultDepth(display, screen);
            Pointer visual = x11.XDefaultVisual(display, screen);
            WallpaperManager.applyToRootWindow(x11, display, rootWindow, screenWidth, screenHeight, depth, visual);
        } catch (Exception e) {
            LOGGER.warning(() -> "Error refreshing desktop wallpaper: " + e.getMessage());
        }
        for (FrameWindow frame : clientToFrame.values()) {
            if (!frame.isMinimized() && (getClientDesktop(frame.clientWindow().longValue()) == currentDesktop || isSticky(getClientDesktop(frame.clientWindow().longValue())))) {
                frame.render();
                frame.blit(display, gc, screen);
            }
        }
        for (DesktopIconTile tile : iconTiles) {
            if (getClientDesktop(tile.clientWin()) == currentDesktop || isSticky(getClientDesktop(tile.clientWin()))) {
                tile.blit(x11, display, gc, screen);
            }
        }
        x11.XFlush(display);
        LOGGER.info("Refreshed X11 desktop background and window frames");
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

    public boolean isDockWindow(long win) {
        if (dockWindows.contains(win)) {
            return true;
        }
        String title = fetchWindowTitle(win);
        if (title != null && (title.contains("Front Panel") || title.contains("Desktop Panel") || title.contains("amadeOS CDE"))) {
            return true;
        }
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        com.sun.jna.ptr.NativeLongByReference nitemsRet = new com.sun.jna.ptr.NativeLongByReference();
        com.sun.jna.ptr.NativeLongByReference bytesAfterRet = new com.sun.jna.ptr.NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(win), netWmWindowType,
                new NativeLong(0), new NativeLong(32), 0,
                new NativeLong(LibX11.XA_ATOM),
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            int count = (int) nitemsRet.getValue().longValue();
            Pointer ptr = propRet.getValue();
            try {
                for (int i = 0; i < count; i++) {
                    long atom = ptr.getNativeLong((long) i * NativeLong.SIZE).longValue();
                    if (atom == netWmWindowTypeDock.longValue()) {
                        return true;
                    }
                }
            } finally {
                x11.XFree(ptr);
            }
        }
        return false;
    }

    public int readDockStrutBottom(long win) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        com.sun.jna.ptr.NativeLongByReference nitemsRet = new com.sun.jna.ptr.NativeLongByReference();
        com.sun.jna.ptr.NativeLongByReference bytesAfterRet = new com.sun.jna.ptr.NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        NativeLong[] strutAtoms = new NativeLong[]{netWmStrutPartial, netWmStrut};
        for (NativeLong atom : strutAtoms) {
            int status = x11.XGetWindowProperty(
                    display, new NativeLong(win), atom,
                    new NativeLong(0), new NativeLong(12), 0,
                    new NativeLong(LibX11.XA_CARDINAL),
                    actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);
            if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() >= 4) {
                Pointer ptr = propRet.getValue();
                try {
                    long bottomStrut = ptr.getNativeLong(3L * NativeLong.SIZE).longValue();
                    if (bottomStrut > 0) {
                        return (int) bottomStrut;
                    }
                } finally {
                    x11.XFree(ptr);
                }
            }
        }
        return 0;
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

        // _NET_NUMBER_OF_DESKTOPS on root
        Memory numMem = new Memory(NativeLong.SIZE);
        numMem.setNativeLong(0, new NativeLong(NUMBER_OF_DESKTOPS));
        x11.XChangeProperty(display, new NativeLong(rootWindow), netNumberOfDesktops, xaCardinalAtom, 32, LibX11.PropModeReplace, numMem, 1);

        // _NET_CURRENT_DESKTOP on root
        Memory curMem = new Memory(NativeLong.SIZE);
        curMem.setNativeLong(0, new NativeLong(currentDesktop));
        x11.XChangeProperty(display, new NativeLong(rootWindow), netCurrentDesktop, xaCardinalAtom, 32, LibX11.PropModeReplace, curMem, 1);

        // _NET_DESKTOP_NAMES on root: "One\0Two\0Three\0Four\0"
        byte[] namesBytes = "One\0Two\0Three\0Four\0".getBytes(StandardCharsets.UTF_8);
        Memory namesMem = new Memory(namesBytes.length);
        namesMem.write(0, namesBytes, 0, namesBytes.length);
        x11.XChangeProperty(display, new NativeLong(rootWindow), netDesktopNames, utf8String, 8, LibX11.PropModeReplace, namesMem, namesBytes.length);

        // _NET_SUPPORTED on root
        NativeLong[] supportedAtoms = new NativeLong[]{
            netSupportingWmCheck, netWmName, netSupported, netClientList, netActiveWindow, netCloseWindow,
            netWmWindowType, netWmWindowTypeDock, netWmState, netWmStateHidden, netWmStrut, netWmStrutPartial,
            netNumberOfDesktops, netCurrentDesktop, netDesktopNames, netWmDesktop
        };
        Memory supMem = new Memory((long) supportedAtoms.length * NativeLong.SIZE);
        for (int i = 0; i < supportedAtoms.length; i++) {
            supMem.setNativeLong((long) i * NativeLong.SIZE, supportedAtoms[i]);
        }
        x11.XChangeProperty(display, new NativeLong(rootWindow), netSupported, xaAtomAtom, 32, LibX11.PropModeReplace, supMem, supportedAtoms.length);

        // Synchronize initial _AMADEOS_THEME on root window
        String existingTheme = fetchRootPropertyString(amadeosThemeAtom);
        if (existingTheme != null && !existingTheme.isBlank()) {
            ThemeManager.applyTheme(existingTheme);
        } else {
            byte[] themeBytes = ThemeManager.currentTheme().id().getBytes(StandardCharsets.UTF_8);
            Memory themeMem = new Memory(themeBytes.length + 1);
            themeMem.write(0, themeBytes, 0, themeBytes.length);
            themeMem.setByte(themeBytes.length, (byte) 0);
            x11.XChangeProperty(display, new NativeLong(rootWindow), amadeosThemeAtom, utf8String, 8, LibX11.PropModeReplace, themeMem, themeBytes.length);
        }

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
        if (win == 0) {
            x11.XDeleteProperty(display, new NativeLong(rootWindow), netActiveWindow);
        } else {
            Memory mem = new Memory(NativeLong.SIZE);
            mem.setNativeLong(0, new NativeLong(win));
            x11.XChangeProperty(display, new NativeLong(rootWindow), netActiveWindow, xaWindowAtom, 32, LibX11.PropModeReplace, mem, 1);
        }
        x11.XFlush(display);
    }

    public void focusClient(long win) {
        if (win == 0 || core.registry().find(win).isEmpty()) {
            return;
        }

        FrameWindow targetFrame = clientToFrame.get(win);
        if (targetFrame != null && targetFrame.isMinimized()) {
            removeAndReflowIconTile(win);
            targetFrame.setMinimized(false);
            x11.XMapWindow(display, targetFrame.frameWindow());
            x11.XDeleteProperty(display, new NativeLong(win), netWmState);

            Memory wmStateMem = new Memory(NativeLong.SIZE * 2);
            wmStateMem.setNativeLong(0, new NativeLong(LibX11.NormalState));
            wmStateMem.setNativeLong(NativeLong.SIZE, new NativeLong(0));
            x11.XChangeProperty(display, new NativeLong(win), wmState, wmState, 32, LibX11.PropModeReplace, wmStateMem, 2);

            targetFrame.render();
            targetFrame.blit(display, gc, screen);
            sendSyntheticConfigureNotify(targetFrame);
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
                if (!frame.isMinimized()) {
                    frame.render();
                    frame.blit(display, gc, screen);
                }
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

    public boolean isSpecialUnmanagedWindow(long win) {
        String title = fetchWindowTitle(win);
        if (title != null) {
            String lower = title.toLowerCase();
            if (lower.contains("workstation locked") || lower.contains("screen locker") || lower.contains("lock screen")) {
                return true;
            }
        }
        return false;
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

        // Also check _NET_WM_NAME (UTF-8)
        if (netWmName != null && utf8String != null) {
            PointerByReference actualTypeRet = new PointerByReference();
            IntByReference actualFormatRet = new IntByReference();
            com.sun.jna.ptr.NativeLongByReference nitemsRet = new com.sun.jna.ptr.NativeLongByReference();
            com.sun.jna.ptr.NativeLongByReference bytesAfterRet = new com.sun.jna.ptr.NativeLongByReference();
            PointerByReference propRet = new PointerByReference();

            int status = x11.XGetWindowProperty(
                    display, new NativeLong(win), netWmName,
                    new NativeLong(0), new NativeLong(256), 0,
                    utf8String,
                    actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

            if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
                Pointer ptr = propRet.getValue();
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

    public String fetchRootPropertyString(NativeLong atom) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        com.sun.jna.ptr.NativeLongByReference nitemsRet = new com.sun.jna.ptr.NativeLongByReference();
        com.sun.jna.ptr.NativeLongByReference bytesAfterRet = new com.sun.jna.ptr.NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(rootWindow), atom,
                new NativeLong(0), new NativeLong(64), 0,
                new NativeLong(0) /* AnyPropertyType */,
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            Pointer ptr = propRet.getValue();
            try {
                int len = (int) nitemsRet.getValue().longValue();
                byte[] bytes = ptr.getByteArray(0, len);
                return new String(bytes, StandardCharsets.UTF_8).trim();
            } finally {
                x11.XFree(ptr);
            }
        }
        return null;
    }

    public void applyThemeGlobally(DesktopTheme newTheme) {
        if (theme instanceof MotifDecorationTheme motifTheme) {
            motifTheme.setTheme(newTheme);
        }
        for (FrameWindow frame : clientToFrame.values()) {
            frame.render();
            frame.blit(display, gc, screen);
        }
        for (DesktopIconTile tile : iconTiles) {
            tile.setTheme(newTheme);
            tile.render();
            tile.blit(x11, display, gc, screen);
        }
        String currentRootTheme = fetchRootPropertyString(amadeosThemeAtom);
        if (currentRootTheme == null || !currentRootTheme.equals(newTheme.id())) {
            byte[] bytes = newTheme.id().getBytes(StandardCharsets.UTF_8);
            Memory mem = new Memory(bytes.length + 1);
            mem.write(0, bytes, 0, bytes.length);
            mem.setByte(bytes.length, (byte) 0);
            x11.XChangeProperty(display, new NativeLong(rootWindow), amadeosThemeAtom, utf8String, 8, LibX11.PropModeReplace, mem, bytes.length);
        }
        x11.XFlush(display);
        LOGGER.info(() -> "WM updated all window frames and desktop icon tiles to theme: " + newTheme.name());
    }

    public FrameWindow decorateAndReparent(long clientWin, WindowGeometry geom) {
        if (isDockWindow(clientWin) || isSpecialUnmanagedWindow(clientWin)) {
            if (isSpecialUnmanagedWindow(clientWin)) {
                x11.XMoveResizeWindow(display, new NativeLong(clientWin), 0, 0, screenWidth, screenHeight);
            }
            x11.XMapWindow(display, new NativeLong(clientWin));
            x11.XRaiseWindow(display, new NativeLong(clientWin));
            return null;
        }
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

        // Determine client's desktop
        int targetDesktop = readClientDesktopProperty(clientWin);
        if (targetDesktop < 0) {
            targetDesktop = currentDesktop;
            writeClientDesktopProperty(clientWin, targetDesktop);
        }
        clientToDesktop.put(clientWin, targetDesktop);

        clientToFrame.put(clientWin, frame);
        frameToClient.put(frameWin.longValue(), frame);

        // Map client window
        x11.XMapWindow(display, new NativeLong(clientWin));

        // Map frame only if on current desktop or sticky
        if (targetDesktop == currentDesktop || isSticky(targetDesktop)) {
            x11.XMapWindow(display, frameWin);
            frame.blit(display, gc, screen);
        }

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
                        if (isDockWindow(child) || isSpecialUnmanagedWindow(child)) {
                            if (isDockWindow(child)) {
                                dockWindows.add(child);
                                int strut = readDockStrutBottom(child);
                                if (strut > 0) {
                                    dockMarginBottom = strut;
                                }
                            } else if (isSpecialUnmanagedWindow(child)) {
                                x11.XMoveResizeWindow(display, new NativeLong(child), 0, 0, screenWidth, screenHeight);
                            }
                            x11.XRaiseWindow(display, new NativeLong(child));
                            continue;
                        }
                        WindowGeometry geom = new WindowGeometry(attrs.x, attrs.y, attrs.width, attrs.height);
                        FrameWindow frame = decorateAndReparent(child, geom);
                        WindowGeometry managedGeom = (frame != null)
                                ? new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight())
                                : geom;
                        core.manage(child, managedGeom);
                        core.adapter().configureWindow(child, managedGeom);
                        grabWindowButtons(child);
                    }
                }
            }
            x11.XFree(childrenReturn.getValue());
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
        LOGGER.info(() -> "amadeOS Window Manager started event loop on screen " + screenWidth + "x" + screenHeight);
        while (running.get()) {
            try {
                processNextEvent();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing X event", e);
            }
        }
        LOGGER.info("amadeOS Window Manager event loop terminated");
    }

    public void stop() {
        running.set(false);
    }

    void handleEvent(LibX11.XEvent event) {
        switch (event.type) {
            case LibX11.KeyPress -> {
                LibX11.XKeyEvent keyEvent = new LibX11.XKeyEvent(event.getPointer());
                if (LockSelection.isLocked(x11, display)) {
                    LOGGER.fine("Screen is locked; ignoring window manager key press");
                    return;
                }
                // Check if Super modifier (Mod4Mask) is held and keycode matches 'l'
                boolean isSuper = (keyEvent.state & (int) LibX11.Mod4Mask) != 0;
                if (isSuper && (lockKeycode != 0 && keyEvent.keycode == lockKeycode)) {
                    LOGGER.info("Super+L pressed: starting the amadeOS screen lock");
                    LockLauncher.lockScreen();
                }
            }
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

                if (isDockWindow(win)) {
                    dockWindows.add(win);
                    int strut = readDockStrutBottom(win);
                    if (strut > 0) {
                        dockMarginBottom = strut;
                    }
                    x11.XMapWindow(display, new NativeLong(win));
                    x11.XRaiseWindow(display, new NativeLong(win));
                    LOGGER.info(() -> "Mapped dock window without frame: " + win + " strutBottom=" + dockMarginBottom);
                    return;
                }

                if (isSpecialUnmanagedWindow(win)) {
                    LOGGER.info(() -> "Mapping unmanaged fullscreen special window: " + win + " (" + fetchWindowTitle(win) + ")");
                    x11.XMoveResizeWindow(display, new NativeLong(win), 0, 0, screenWidth, screenHeight);
                    x11.XMapWindow(display, new NativeLong(win));
                    x11.XRaiseWindow(display, new NativeLong(win));
                    x11.XSetInputFocus(display, new NativeLong(win), LibX11.RevertToPointerRoot, new NativeLong(LibX11.CurrentTime));
                    x11.XFlush(display);
                    return;
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

                int maxUsableH = Math.max(100, screenHeight - dockMarginBottom);
                int frameX, frameY;
                var parentRecord = (transientFor != 0) ? core.registry().find(transientFor) : Optional.<WindowRecord>empty();
                if (parentRecord.isPresent()) {
                    WindowGeometry parentGeom = parentRecord.get().geometry();
                    frameX = parentGeom.x() + (parentGeom.width() - frameW) / 2;
                    frameY = parentGeom.y() + (parentGeom.height() - frameH) / 2;
                    frameX = Math.max(10, Math.min(frameX, screenWidth - frameW - 10));
                    frameY = Math.max(10, Math.min(frameY, maxUsableH - frameH - 10));
                } else if (x > 0 || y > 0) {
                    frameX = Math.max(10, Math.min(x, screenWidth - frameW - 10));
                    frameY = Math.max(10, Math.min(y, maxUsableH - frameH - 10));
                } else {
                    // Smart floating cascade offset from active window (or last managed window)
                    var activeRecord = (activeWindow != 0) ? core.registry().find(activeWindow) : Optional.<WindowRecord>empty();
                    if (activeRecord.isPresent()) {
                        WindowGeometry ag = activeRecord.get().geometry();
                        frameX = ag.x() + 30;
                        frameY = ag.y() + 30;
                        if (frameX + frameW > screenWidth - 20 || frameY + frameH > maxUsableH - 20) {
                            frameX = 40;
                            frameY = 40;
                        }
                    } else {
                        var all = new java.util.ArrayList<>(core.registry().snapshot());
                        if (!all.isEmpty()) {
                            var last = all.get(all.size() - 1).geometry();
                            frameX = last.x() + 30;
                            frameY = last.y() + 30;
                            if (frameX + frameW > screenWidth - 20 || frameY + frameH > maxUsableH - 20) {
                                frameX = 40;
                                frameY = 40;
                            }
                        } else {
                            frameX = 50;
                            frameY = 50;
                        }
                    }
                }
                WindowGeometry initialGeom = new WindowGeometry(frameX, frameY, clientW, clientH);

                FrameWindow frame = decorateAndReparent(win, initialGeom);
                WindowGeometry managedGeom = (frame != null)
                        ? new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight())
                        : initialGeom;

                core.manage(win, managedGeom);
                core.adapter().configureWindow(win, managedGeom);

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

                if (LockSelection.isLocked(x11, display)) {
                    LOGGER.fine("Screen is locked; ignoring window manager button press");
                    x11.XFlush(display);
                    return;
                }

                // Desktop background click: Button 3 shows Root Menu, Button 1 hides it
                if (eventWin == rootWindow) {
                    if (btnEvent.button == LibX11.Button3) {
                        LOGGER.info(() -> "ButtonPress 3 on Root Window: popping up Desktop Root Menu at "
                                + btnEvent.x_root + "," + btnEvent.y_root);
                        desktopMenu.show(btnEvent.x_root, btnEvent.y_root);
                    } else if (btnEvent.button == LibX11.Button1) {
                        desktopMenu.hide();
                    }
                    x11.XFlush(display);
                    return;
                }

                DesktopIconTile clickedTile = tileWinToTile.get(eventWin);
                if (clickedTile != null) {
                    LOGGER.info(() -> "Desktop icon tile clicked for window " + clickedTile.clientWin());
                    focusClient(clickedTile.clientWin());
                    return;
                }

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
                    DesktopIconTile expTile = tileWinToTile.get(exp.window.longValue());
                    if (expTile != null) {
                        expTile.blit(x11, display, gc, screen);
                        x11.XFlush(display);
                        return;
                    }
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
                LibX11.XPropertyEvent prop = new LibX11.XPropertyEvent(event.getPointer());
                long win = prop.window != null ? prop.window.longValue() : 0L;
                if (win == rootWindow && prop.atom != null && prop.atom.equals(amadeosThemeAtom)) {
                    String themeId = fetchRootPropertyString(amadeosThemeAtom);
                    if (themeId != null && !themeId.isEmpty()) {
                        ThemeManager.applyTheme(themeId);
                    }
                    break;
                }
                FrameWindow frame = clientToFrame.get(win);
                if (frame != null) {
                    String newTitle = fetchWindowTitle(win);
                    if (!newTitle.equals(frame.title())) {
                        frame.setTitle(newTitle);
                        frame.render();
                        frame.blit(display, gc, screen);
                        DesktopIconTile tile = clientToTile.get(win);
                        if (tile != null) {
                            tile.setTitle(newTitle);
                            tile.render();
                            tile.blit(x11, display, gc, screen);
                        }
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
                } else if (cmsg.message_type != null && cmsg.message_type.equals(wmChangeState)) {
                    long targetState = (cmsg.data != null && cmsg.data.length > 0 && cmsg.data[0] != null)
                            ? cmsg.data[0].longValue() : 0L;
                    if (targetState == LibX11.IconicState) {
                        FrameWindow frame = clientToFrame.get(win);
                        if (frame != null && !frame.isMinimized()) {
                            minimizeWindow(frame);
                            LOGGER.info(() -> "ClientMessage WM_CHANGE_STATE (Iconic) minimized window " + win);
                        }
                    }
                } else if (cmsg.message_type != null && cmsg.message_type.equals(netWmState)) {
                    long action = (cmsg.data != null && cmsg.data.length > 0 && cmsg.data[0] != null) ? cmsg.data[0].longValue() : 0L;
                    long prop1 = (cmsg.data != null && cmsg.data.length > 1 && cmsg.data[1] != null) ? cmsg.data[1].longValue() : 0L;
                    long prop2 = (cmsg.data != null && cmsg.data.length > 2 && cmsg.data[2] != null) ? cmsg.data[2].longValue() : 0L;
                    if (prop1 == netWmStateHidden.longValue() || prop2 == netWmStateHidden.longValue()) {
                        FrameWindow frame = clientToFrame.get(win);
                        if (frame != null) {
                            if (action == 1 /* _NET_WM_STATE_ADD */ || (action == 2 /* TOGGLE */ && !frame.isMinimized())) {
                                minimizeWindow(frame);
                            } else if (action == 0 /* _NET_WM_STATE_REMOVE */ || (action == 2 && frame.isMinimized())) {
                                restoreWindow(frame);
                            }
                        }
                    }
                } else if (cmsg.message_type != null && cmsg.message_type.equals(netCurrentDesktop)) {
                    long targetDesktop = (cmsg.data != null && cmsg.data.length > 0 && cmsg.data[0] != null) ? cmsg.data[0].longValue() : 0L;
                    switchDesktop((int) targetDesktop);
                    LOGGER.info(() -> "ClientMessage _NET_CURRENT_DESKTOP handled: desktop=" + targetDesktop);
                } else if (cmsg.message_type != null && cmsg.message_type.equals(netWmDesktop)) {
                    long targetDesktop = (cmsg.data != null && cmsg.data.length > 0 && cmsg.data[0] != null) ? cmsg.data[0].longValue() : 0L;
                    setClientDesktop(win, (int) targetDesktop);
                    LOGGER.info(() -> "ClientMessage _NET_WM_DESKTOP handled: win=" + win + " desktop=" + targetDesktop);
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
                if (frameToClient.containsKey(win) || tileWinToTile.containsKey(win)) {
                    // Internal decoration frame or tile window destroyed, ignore
                    return;
                }
                cleanupWindow(win);
            }
            case LibX11.UnmapNotify -> {
                LibX11.XUnmapEvent unmap = new LibX11.XUnmapEvent(event.getPointer());
                long win = unmap.window.longValue();
                if (frameToClient.containsKey(win) || tileWinToTile.containsKey(win)) {
                    // Internal decoration frame or tile window unmapped, ignore
                    return;
                }
                FrameWindow frame = clientToFrame.get(win);
                if (frame != null && (frame.isMinimized() || getClientDesktop(win) != currentDesktop)) {
                    // Window minimized or on another workspace, unmap is expected, do not destroy
                    return;
                }
                cleanupWindow(win);
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
            int maxH = Math.max(100, screenHeight - dockMarginBottom);
            frame.setBounds(0, 0, screenWidth, maxH);
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

    public void minimizeWindow(FrameWindow frame) {
        if (frame == null || frame.isMinimized()) {
            return;
        }
        frame.setMinimized(true);
        x11.XUnmapWindow(display, frame.frameWindow());
        long clientWin = frame.clientWindow().longValue();

        // Spawn/show desktop icon tile at top-left corner
        createAndShowIconTile(clientWin, frame.title());

        // Set _NET_WM_STATE = _NET_WM_STATE_HIDDEN
        Memory stateMem = new Memory(NativeLong.SIZE);
        stateMem.setNativeLong(0, netWmStateHidden);
        x11.XChangeProperty(display, new NativeLong(clientWin), netWmState, xaAtomAtom, 32, LibX11.PropModeReplace, stateMem, 1);

        // Set ICCCM WM_STATE = IconicState
        Memory wmStateMem = new Memory(NativeLong.SIZE * 2);
        wmStateMem.setNativeLong(0, new NativeLong(LibX11.IconicState));
        wmStateMem.setNativeLong(NativeLong.SIZE, new NativeLong(0));
        x11.XChangeProperty(display, new NativeLong(clientWin), wmState, wmState, 32, LibX11.PropModeReplace, wmStateMem, 2);

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
            if (activeWindow == 0) {
                setActiveWindow(0);
            }
        }
        x11.XFlush(display);
    }

    public void restoreWindow(FrameWindow frame) {
        if (frame == null) {
            return;
        }
        focusClient(frame.clientWindow().longValue());
    }

    public java.util.List<DesktopIconTile> iconTiles() {
        return java.util.Collections.unmodifiableList(iconTiles);
    }

    private void createAndShowIconTile(long clientWin, String title) {
        if (clientToTile.containsKey(clientWin)) {
            return;
        }
        int index = iconTiles.size();
        int tileX = 0;
        int tileY = index * DesktopIconTile.TILE_HEIGHT;

        long mask = LibX11.ExposureMask | LibX11.ButtonPressMask | LibX11.ButtonReleaseMask;
        NativeLong tileWin = x11.XCreateSimpleWindow(
                display, new NativeLong(rootWindow),
                tileX, tileY, DesktopIconTile.TILE_WIDTH, DesktopIconTile.TILE_HEIGHT,
                0, new NativeLong(0), new NativeLong(0));

        if (tileWin == null || tileWin.longValue() == 0) {
            LOGGER.warning("Failed to create desktop icon tile window for client " + clientWin);
            return;
        }

        x11.XSelectInput(display, tileWin, new NativeLong(mask));

        DesktopIconTile tile = new DesktopIconTile(clientWin, tileWin, index, title);
        tile.setTheme(ThemeManager.currentTheme());
        tile.render();

        iconTiles.add(tile);
        tileWinToTile.put(tileWin.longValue(), tile);
        clientToTile.put(clientWin, tile);

        x11.XMapWindow(display, tileWin);
        x11.XRaiseWindow(display, tileWin);
        tile.blit(x11, display, gc, screen);
        x11.XFlush(display);
        LOGGER.info(() -> "Spawned desktop icon tile for window " + clientWin + " at y=" + tileY);
    }

    private void removeAndReflowIconTile(long clientWin) {
        DesktopIconTile removed = clientToTile.remove(clientWin);
        if (removed == null) {
            return;
        }
        tileWinToTile.remove(removed.tileWin().longValue());
        iconTiles.remove(removed);
        removed.destroy(x11, display);

        // Reflow remaining tiles: vertically under each other with 0px space between
        for (int i = 0; i < iconTiles.size(); i++) {
            DesktopIconTile tile = iconTiles.get(i);
            tile.setIndex(i);
            x11.XMoveWindow(display, tile.tileWin(), 0, tile.y());
        }
        x11.XFlush(display);
        LOGGER.info(() -> "Removed and reflowed desktop icon tiles (remaining: " + iconTiles.size() + ")");
    }

    private void cleanupWindow(long win) {
        boolean isClient = clientToFrame.containsKey(win);
        boolean isDock = dockWindows.contains(win);
        boolean isTileClient = clientToTile.containsKey(win);
        if (!isClient && !isDock && !isTileClient) {
            return;
        }

        dockWindows.remove(win);
        clientToDesktop.remove(win);
        removeAndReflowIconTile(win);
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
        desktopMenu.dispose();
        if (display != null) {
            for (DesktopIconTile tile : new java.util.ArrayList<>(iconTiles)) {
                tile.destroy(x11, display);
            }
            iconTiles.clear();
            tileWinToTile.clear();
            clientToTile.clear();

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
