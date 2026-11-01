package eu.amadeos.system.wm.x11;

import eu.amadeos.system.base.x11.LibX11;
import eu.amadeos.system.base.x11.LibXkb;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import eu.amadeos.system.wm.FloatingLayoutPolicy;
import eu.amadeos.system.wm.FocusSuccessor;
import eu.amadeos.system.wm.WindowGeometry;
import eu.amadeos.system.wm.WindowManagerCore;
import eu.amadeos.system.wm.WindowRecord;
import eu.amadeos.system.wm.WindowRegistry;
import eu.amadeos.system.wm.decoration.DecorationTheme;
import eu.amadeos.system.wm.decoration.DesktopIconTile;
import eu.amadeos.system.wm.decoration.FrameMetrics;
import eu.amadeos.system.wm.decoration.FrameWindow;
import eu.amadeos.system.wm.decoration.MotifDecorationTheme;
import eu.amadeos.system.wm.decoration.ResizeDirection;
import eu.amadeos.system.wm.decoration.WindowButton;
import eu.amadeos.system.base.process.LockLauncher;
import eu.amadeos.system.base.x11.LockSelection;
import eu.amadeos.system.base.sound.BellPlayer;
import eu.amadeos.system.wm.hang.HangTexts;
import eu.amadeos.system.wm.hang.PingTracker;
import eu.amadeos.system.wm.menu.DesktopMenuActions;
import eu.amadeos.system.wm.menu.DesktopRootMenu;
import eu.amadeos.system.wm.menu.WindowMenu;
import eu.amadeos.system.wm.switcher.PreviewArea;
import eu.amadeos.system.wm.switcher.RecentWindows;
import eu.amadeos.system.wm.switcher.SwitcherEntry;
import eu.amadeos.system.wm.switcher.SwitcherPopup;
import eu.amadeos.system.wm.switcher.SwitcherSelection;
import eu.amadeos.system.base.x11.LaunchFeedback;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.theme.WallpaperManager;
import eu.amadeos.system.base.theme.WallpaperManager.Backdrop;
import eu.amadeos.system.base.workspace.WorkspaceNames;
import eu.amadeos.system.base.window.WindowSettings;
import eu.amadeos.system.base.window.WindowSettingsStore;
import eu.amadeos.commons.config.ConfigWatcher;
import java.util.Set;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.ui.SessionDialogHelper;
import eu.amadeos.system.wm.power.PowerButton;

public final class X11WindowManagerService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(X11WindowManagerService.class.getName());

    private final LibX11 x11;
    private final Pointer display;
    private final int screen;
    private final long rootWindow;
    // The size of the root window; follows the Settings' Display page and other xrandr changes.
    private int screenWidth;
    private int screenHeight;
    private final WindowManagerCore core;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Xlib is not thread-safe and the display is used without XInitThreads, so every call on
    // `display` must happen on the event loop thread. Other threads (the Swing EDT for the root
    // menu and theme changes) queue their work here and wake the loop with a ClientMessage
    // sent over a separate connection.
    private final ConcurrentLinkedQueue<Runnable> eventThreadActions = new ConcurrentLinkedQueue<>();
    private volatile Thread eventThread;
    /** XKB's event type, or -1 while the bell is still the server's own (PC speaker). */
    private int xkbEventBase = -1;
    private LibXkb xkb;
    private BellPlayer bellPlayer;
    private final String displayName;
    private final Object wakeLock = new Object();
    private Pointer wakeDisplay;
    private final NativeLong wakeAtom;
    private final DesktopMenuActions menuActions;

    private final long checkWindow;
    private final Pointer gc;
    private final Pointer xorGc;
    private final DecorationTheme theme = new MotifDecorationTheme();
    private final Map<Long, FrameWindow> clientToFrame;
    /**
     * Where the frame of a withdrawn (hidden) client was, and where the client was put on the root: if it is
     * mapped there again, its new frame goes where the old one was.
     */
    private final Map<Long, int[]> withdrawnFrames = new HashMap<>();
    /** The window each managed dialog belongs to (WM_TRANSIENT_FOR), read when it was mapped. */
    private final Map<Long, Long> transientOwners = new HashMap<>();
    private final Map<Long, FrameWindow> frameToClient;

    /** Focus, raising and moving as chosen in Settings &gt; Window; read anew whenever window.conf changes. */
    private final WindowSettingsStore windowSettingsStore = WindowSettingsStore.user();
    private volatile WindowSettings windowSettings = windowSettingsStore.load();
    private final ConfigWatcher windowSettingsWatcher = new ConfigWatcher(
            windowSettingsStore.file().getParent(), Set.of(WindowSettingsStore.FILE_NAME), "amadeos-wm-window-settings",
            () -> {
                WindowSettings loaded = windowSettingsStore.load();
                LOGGER.info(() -> "Window settings changed: " + loaded);
                windowSettings = loaded;
            });

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
    private final NativeLong cursorBusy;

    // Launch feedback (see LaunchFeedback): the busy cursor until a new window appears.
    // busyUntil is only used on the event loop thread; 0 means not busy.
    private final NativeLong launchAtom;
    private final NativeLong launchBusyAtom;
    private long busyUntil;
    private final java.util.Timer busyTimer = new java.util.Timer("wm-launch-feedback", true);

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
    private final NativeLong netWmStateFullscreen;
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
    // _NET_WM_PING (in WM_PROTOCOLS) and the mark of a window that does not answer it.
    private final NativeLong wmProtocols;
    private final NativeLong netWmPing;
    private final NativeLong amadeosNotResponding;
    private final PingTracker pings = new PingTracker();
    private eu.amadeos.commons.i18n.Messages hangTexts;
    private final NativeLong xaWindowAtom;
    private final NativeLong xaAtomAtom;
    private final NativeLong xaCardinalAtom;

    public static final int NUMBER_OF_DESKTOPS = 4;
    public static final long STICKY_DESKTOP = 0xFFFFFFFFL;
    private volatile int currentDesktop = 0;
    private final Map<Long, Integer> clientToDesktop = new HashMap<>();
    private final DesktopRootMenu desktopMenu;
    private final WindowMenu windowMenu;
    /** The last click on a window menu button, for CDE's double click that closes the window. */
    private long lastMenuButtonWin;
    private long lastMenuButtonTime;
    private static final long MENU_DOUBLE_CLICK_MS = 400;
    /** Set (CARDINAL 1) on a client window while it does not answer _NET_WM_PING. */
    public static final String NOT_RESPONDING_ATOM = "_AMADEOS_NOT_RESPONDING";
    /** How often the active window is pinged and the waiting pings are checked. */
    private static final long PING_PERIOD_MILLIS = 2000;
    private static final String TASK_MANAGER = "amadeos-taskmanager";
    private static final long XK_DELETE = 0xFFFF;

    private final java.util.Set<Long> dockWindows = new java.util.HashSet<>();
    /** The rendered backdrops of the workspaces, so a switch only swaps the root window's pixmap. */
    private final Map<Backdrop, NativeLong> backdropPixmaps = new HashMap<>();
    private int dockMarginBottom = 0;

    // CDE Desktop Minimized Icon Tiles (top-left, vertically stacked with 0px spacing)
    private final java.util.List<DesktopIconTile> iconTiles = new java.util.ArrayList<>();
    private final Map<Long, DesktopIconTile> tileWinToTile = new HashMap<>();
    private final Map<Long, DesktopIconTile> clientToTile = new HashMap<>();

    private int lockKeycode = 0;
    private int powerKeycode = 0;
    private PowerButton powerButton;

    // Alt+Tab: the windows in the order they were last active, their previews and icons, and the open round
    // (null while the switcher is closed).
    private int tabKeycode = 0;
    private final Set<Integer> altKeycodes = new java.util.HashSet<>();
    private int escapeKeycode = 0;
    // Ctrl+Shift+Esc and Ctrl+Alt+Delete start the Task Manager.
    private int taskManagerEscapeKeycode = 0;
    private int deleteKeycode = 0;
    // Ctrl+Alt+T opens a terminal.
    private static final long XK_T = 0x0074;
    private int terminalKeycode = 0;
    private int returnKeycode = 0;
    private int leftKeycode = 0;
    private int rightKeycode = 0;
    private final RecentWindows recentWindows = new RecentWindows();
    private WindowPreviews previews;
    private SwitcherPopup switcherPopup;
    private SwitcherSelection switcher;

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

        // Claim WM role; ButtonPressMask delivers desktop clicks (e.g. to close the root menu)
        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask
                | LibX11.StructureNotifyMask | LibX11.PropertyChangeMask | LibX11.ButtonPressMask;
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
                x11, displayName, display, screen, rootWindow, screenWidth, screenHeight, core, clientToFrame, frameToClient, gc, xorGc);
    }

    private X11WindowManagerService(
            LibX11 x11,
            String displayName,
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
        this.displayName = displayName;
        this.display = display;
        this.wakeAtom = x11.XInternAtom(display, "_AMADEOS_WM_WAKE", 0);
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
        this.cursorBusy = x11.XCreateFontCursor(display, LibX11.XC_watch);
        this.launchAtom = x11.XInternAtom(display, LaunchFeedback.MESSAGE_ATOM, 0);
        this.launchBusyAtom = x11.XInternAtom(display, LaunchFeedback.BUSY_PROPERTY, 0);

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
        this.netWmStateFullscreen = x11.XInternAtom(display, "_NET_WM_STATE_FULLSCREEN", 0);
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
        this.wmProtocols = x11.XInternAtom(display, "WM_PROTOCOLS", 0);
        this.netWmPing = x11.XInternAtom(display, "_NET_WM_PING", 0);
        this.amadeosNotResponding = x11.XInternAtom(display, NOT_RESPONDING_ATOM, 0);
        this.xaWindowAtom = new NativeLong(LibX11.XA_WINDOW);
        this.xaAtomAtom = new NativeLong(LibX11.XA_ATOM);
        this.xaCardinalAtom = new NativeLong(LibX11.XA_CARDINAL);

        // Native Root Cursor Polish: set left_ptr arrow cursor on root window
        x11.XDefineCursor(display, new NativeLong(rootWindow), cursorDefault);

        // No button grab on the root window for the Desktop Root Menu: a passive grab on the root also
        // catches clicks on every window above it, so the front panel and the applications would never
        // see their right-clicks. The ButtonPressMask selected on the root delivers bare desktop clicks.

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

        // The power button: the window manager does what Settings > Power says instead of logind.
        try {
            this.powerKeycode = keycode(PowerButton.XF86_POWER_OFF);
            if (powerKeycode != 0) {
                for (long lock : new long[]{0, LibX11.Mod2Mask, LibX11.LockMask, LibX11.Mod2Mask | LibX11.LockMask}) {
                    x11.XGrabKey(display, powerKeycode, new NativeLong(lock), new NativeLong(rootWindow), 1,
                            LibX11.GrabModeAsync, LibX11.GrabModeAsync);
                }
                this.powerButton = PowerButton.desktop();
                powerButton.takeOver();
                LOGGER.fine(() -> "Registered the power button (keycode=" + powerKeycode + ")");
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to bind the power button: " + e.getMessage());
        }

        // --8<-- [start:task-manager-keys]
        // Ctrl+Shift+Esc and Ctrl+Alt+Delete open the Task Manager. Under X, Ctrl+Alt+Delete is an ordinary
        // key: the X server owns the keyboard, so systemd's reboot on the text console never sees it.
        try {
            this.taskManagerEscapeKeycode = keycode(LibX11.XK_Escape);
            this.deleteKeycode = keycode(XK_DELETE);
            for (long lock : new long[]{0, LibX11.Mod2Mask, LibX11.LockMask, LibX11.Mod2Mask | LibX11.LockMask}) {
                if (taskManagerEscapeKeycode != 0) {
                    x11.XGrabKey(display, taskManagerEscapeKeycode,
                            new NativeLong(LibX11.ControlMask | LibX11.ShiftMask | lock),
                            new NativeLong(rootWindow), 1, LibX11.GrabModeAsync, LibX11.GrabModeAsync);
                }
                if (deleteKeycode != 0) {
                    x11.XGrabKey(display, deleteKeycode, new NativeLong(LibX11.ControlMask | LibX11.Mod1Mask | lock),
                            new NativeLong(rootWindow), 1, LibX11.GrabModeAsync, LibX11.GrabModeAsync);
                }
            }
            LOGGER.fine("Registered Ctrl+Shift+Esc and Ctrl+Alt+Delete for the Task Manager");
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to bind the Task Manager keys: " + e.getMessage());
        }
        // --8<-- [end:task-manager-keys]

        // Ctrl+Alt+T opens a terminal (amade-terminal, see Terminals), as on most Linux desktops.
        try {
            this.terminalKeycode = keycode(XK_T);
            for (long lock : new long[]{0, LibX11.Mod2Mask, LibX11.LockMask, LibX11.Mod2Mask | LibX11.LockMask}) {
                if (terminalKeycode != 0) {
                    x11.XGrabKey(display, terminalKeycode, new NativeLong(LibX11.ControlMask | LibX11.Mod1Mask | lock),
                            new NativeLong(rootWindow), 1, LibX11.GrabModeAsync, LibX11.GrabModeAsync);
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to bind Ctrl+Alt+T: " + e.getMessage());
        }

        // --8<-- [start:alt-tab-keys]
        // Alt+Tab and Alt+Shift+Tab open the window switcher; once it is open the keyboard is grabbed, so
        // the further Tabs, the arrows, Esc and the release of Alt come here too.
        try {
            this.previews = new WindowPreviews(x11, display, rootWindow);
            this.tabKeycode = keycode(LibX11.XK_Tab);
            for (long sym : new long[]{LibX11.XK_Alt_L, LibX11.XK_Alt_R, LibX11.XK_Meta_L, LibX11.XK_Meta_R}) {
                int kc = keycode(sym);
                if (kc != 0) altKeycodes.add(kc);
            }
            this.escapeKeycode = keycode(LibX11.XK_Escape);
            this.returnKeycode = keycode(LibX11.XK_Return);
            this.leftKeycode = keycode(LibX11.XK_Left);
            this.rightKeycode = keycode(LibX11.XK_Right);
            if (tabKeycode != 0) {
                for (long shift : new long[]{0, LibX11.ShiftMask}) {
                    for (long lock : new long[]{0, LibX11.Mod2Mask, LibX11.LockMask, LibX11.Mod2Mask | LibX11.LockMask}) {
                        x11.XGrabKey(display, tabKeycode, new NativeLong(LibX11.Mod1Mask | shift | lock),
                                new NativeLong(rootWindow), 1, LibX11.GrabModeAsync, LibX11.GrabModeAsync);
                    }
                }
                LOGGER.fine(() -> "Registered the Alt+Tab window switcher (keycode=" + tabKeycode + ")");
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to bind the Alt+Tab window switcher: " + e.getMessage());
        }
        // --8<-- [end:alt-tab-keys]
        this.switcherPopup = new SwitcherPopup(new SwitcherPopup.Actions() {
            @Override
            public void select(int index) {
                runOnEventThread(() -> {
                    if (switcher != null) switcher.select(index);
                });
            }

            @Override
            public void pick(int index) {
                runOnEventThread(() -> {
                    if (switcher != null) {
                        switcher.select(index);
                        commitSwitcher();
                    }
                });
            }
        });

        // Menu actions arrive on the Swing EDT; anything touching X runs on the event thread.
        this.menuActions = new DesktopMenuActions() {
            @Override
            public void switchWorkspace(int workspaceIndex) {
                runOnEventThread(() -> X11WindowManagerService.this.switchDesktop(workspaceIndex));
            }

            @Override
            public int currentWorkspace() {
                return X11WindowManagerService.this.currentDesktop();
            }

            @Override
            public String workspaceName(int workspaceIndex) {
                return X11WindowManagerService.this.workspaceName(workspaceIndex);
            }

            @Override
            public void shuffleUp() {
                runOnEventThread(X11WindowManagerService.this::shuffleUp);
            }

            @Override
            public void shuffleDown() {
                runOnEventThread(X11WindowManagerService.this::shuffleDown);
            }

            @Override
            public void minimizeAll() {
                runOnEventThread(X11WindowManagerService.this::minimizeAllCurrentWorkspace);
            }

            @Override
            public void restoreAll() {
                runOnEventThread(X11WindowManagerService.this::restoreAllCurrentWorkspace);
            }

            @Override
            public void refreshDesktop() {
                runOnEventThread(X11WindowManagerService.this::refreshDesktop);
            }

            @Override
            public void openSettings() {
                // The Settings are their own program (package amadeos-settings).
                try {
                    eu.amadeos.system.base.process.ProcessLauncher.start(java.util.List.of("amadeos-settings"));
                } catch (java.io.IOException e) {
                    LOGGER.warning(() -> "Could not start the Settings (amadeos-settings): " + e.getMessage());
                }
            }

            @Override
            public void lockScreen() {
                SessionDialogHelper.lockScreen(null);
            }

            @Override
            public void logoutSession() {
                SessionDialogHelper.showSessionPowerDialog(null);
            }
        };
        this.desktopMenu = new DesktopRootMenu(menuActions);
        this.windowMenu = new WindowMenu(new WindowMenu.Actions() {
            @Override
            public void restore(long clientWin) {
                runOnEventThread(() -> withFrame(clientWin, f -> { if (f.isMaximized()) toggleMaximize(f); }));
            }

            @Override
            public void minimize(long clientWin) {
                runOnEventThread(() -> withFrame(clientWin, X11WindowManagerService.this::minimizeWindow));
            }

            @Override
            public void maximize(long clientWin) {
                runOnEventThread(() -> withFrame(clientWin, f -> { if (!f.isMaximized()) toggleMaximize(f); }));
            }

            @Override
            public void lower(long clientWin) {
                runOnEventThread(() -> {
                    core.lower(clientWin);
                    x11.XFlush(display);
                });
            }

            @Override
            public void close(long clientWin) {
                runOnEventThread(() -> requestClose(clientWin));
            }
        });

        NativeLong createdCheck = x11.XCreateSimpleWindow(
                display, new NativeLong(rootWindow), 0, 0, 1, 1, 0, new NativeLong(0), new NativeLong(0));
        this.checkWindow = createdCheck != null ? createdCheck.longValue() : 0;
        initEwmh();

        ThemeManager.addListener(newTheme -> runOnEventThread(() -> applyThemeGlobally(newTheme)));
        windowSettingsWatcher.start();

        try {
            showWorkspaceBackdrop();
        } catch (Exception e) {
            LOGGER.fine("Wallpaper init deferred: " + e.getMessage());
        }
    }

    // --8<-- [start:backdrop]
    /**
     * Shows the current workspace's backdrop on the root window, as CDE gives every workspace its own. The
     * Settings store the backdrops; they are read anew on every switch and rendered once per backdrop.
     */
    void showWorkspaceBackdrop() {
        Backdrop backdrop = WallpaperManager.backdropOf(currentDesktop);
        NativeLong pixmap = backdropPixmaps.get(backdrop);
        if (pixmap == null) {
            int depth = x11.XDefaultDepth(display, screen);
            Pointer visual = x11.XDefaultVisual(display, screen);
            pixmap = WallpaperManager.renderPixmap(x11, display, rootWindow, screenWidth, screenHeight, depth, visual, backdrop);
            if (pixmap == null) return;
            backdropPixmaps.put(backdrop, pixmap);
        }
        WallpaperManager.showPixmap(x11, display, rootWindow, pixmap);
        // Keep only the pixmaps of backdrops a workspace still has.
        java.util.Set<Backdrop> inUse = new java.util.HashSet<>();
        for (int i = 0; i < NUMBER_OF_DESKTOPS; i++) {
            inUse.add(WallpaperManager.backdropOf(i));
        }
        inUse.add(backdrop);
        backdropPixmaps.entrySet().removeIf(entry -> {
            if (inUse.contains(entry.getKey())) return false;
            x11.XFreePixmap(display, entry.getValue());
            return true;
        });
    }
    // --8<-- [end:backdrop]

    // --8<-- [start:resize]
    /**
     * The screen got a new size, e.g. from the Display page of the Settings (xrandr): the backdrop is drawn
     * anew, maximized and full screen windows fill the new screen, and other windows are moved back onto it.
     * The front panel places itself.
     */
    void screenResized(int width, int height) {
        if (width <= 0 || height <= 0 || (width == screenWidth && height == screenHeight)) {
            return;
        }
        LOGGER.info("Screen resized from " + screenWidth + "x" + screenHeight + " to " + width + "x" + height);
        screenWidth = width;
        screenHeight = height;
        forgetBackdropPixmaps();
        try {
            showWorkspaceBackdrop();
        } catch (RuntimeException e) {
            LOGGER.fine("Backdrop not redrawn: " + e.getMessage());
        }
        int maxH = Math.max(100, screenHeight - dockMarginBottom);
        for (FrameWindow frame : java.util.List.copyOf(clientToFrame.values())) {
            FrameMetrics m = frame.metrics();
            if (frame.isFullScreen()) {
                frame.setBounds(-m.clientOffsetX(), -m.clientOffsetY(), m.frameWidth(screenWidth), m.frameHeight(screenHeight));
            } else if (frame.isMaximized()) {
                frame.setBounds(0, 0, screenWidth, maxH);
            } else {
                int w = Math.min(frame.frameWidth(), screenWidth);
                int h = Math.min(frame.frameHeight(), maxH);
                int x = Math.max(0, Math.min(frame.frameX(), screenWidth - w));
                int y = Math.max(0, Math.min(frame.frameY(), maxH - h));
                if (x == frame.frameX() && y == frame.frameY() && w == frame.frameWidth() && h == frame.frameHeight()) {
                    continue;
                }
                frame.setBounds(x, y, w, h);
            }
            x11.XMoveResizeWindow(display, frame.frameWindow(), frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight());
            x11.XMoveResizeWindow(display, frame.clientWindow(),
                    frame.clientX(), frame.clientY(), frame.clientWidth(), frame.clientHeight());
            frame.render();
            frame.blit(display, gc, screen);
            sendSyntheticConfigureNotify(frame);
            core.registry().updateGeometry(frame.clientWindow().longValue(),
                    new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight()));
        }
        x11.XFlush(display);
    }
    // --8<-- [end:resize]

    private void forgetBackdropPixmaps() {
        for (NativeLong pixmap : backdropPixmaps.values()) {
            x11.XFreePixmap(display, pixmap);
        }
        backdropPixmaps.clear();
    }

    /** The names of the workspaces as the Front Panel published them, else One to Four. */
    String workspaceName(int index) {
        java.util.List<String> names = desktopNames;
        if (index < names.size() && !names.get(index).isBlank()) {
            return names.get(index);
        }
        return DEFAULT_WORKSPACE_NAMES[index];
    }

    /** {@code _NET_DESKTOP_NAMES} as last read on the event thread; the root menu reads it on the EDT. */
    private volatile java.util.List<String> desktopNames = java.util.List.of();

    private void readDesktopNames() {
        desktopNames = java.util.List.copyOf(X11EwmhHelper.readDesktopNames(x11, display, rootWindow, netDesktopNames));
    }

    private static final String[] DEFAULT_WORKSPACE_NAMES = {"One", "Two", "Three", "Four"};

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

        try {
            showWorkspaceBackdrop();
        } catch (Exception e) {
            LOGGER.warning(() -> "Cannot show the backdrop of workspace " + (currentDesktop + 1) + ": " + e.getMessage());
        }

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
            // Pictures may have changed on disk: render them anew.
            forgetBackdropPixmaps();
            showWorkspaceBackdrop();
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

    /** The cursor where no resize or move is offered: the arrow, or the watch while an application starts. */
    private NativeLong idleCursor() {
        return busyUntil != 0 ? cursorBusy : cursorDefault;
    }

    /** An application is starting: the watch on the desktop and the frames, until its window appears. */
    private void startBusy() {
        boolean wasBusy = busyUntil != 0;
        busyUntil = System.currentTimeMillis() + LaunchFeedback.TIMEOUT_MILLIS;
        busyTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                runOnEventThread(() -> {
                    if (busyUntil != 0 && System.currentTimeMillis() >= busyUntil) {
                        LOGGER.info("No new window after a launch; busy cursor off");
                        endBusy();
                    }
                });
            }
        }, LaunchFeedback.TIMEOUT_MILLIS + 50);
        if (wasBusy) {
            return;
        }
        x11.XDefineCursor(display, new NativeLong(rootWindow), cursorBusy);
        Memory one = new Memory(NativeLong.SIZE);
        one.setNativeLong(0, new NativeLong(1));
        x11.XChangeProperty(display, new NativeLong(rootWindow), launchBusyAtom, xaCardinalAtom, 32,
                LibX11.PropModeReplace, one, 1);
        x11.XFlush(display);
    }

    private void endBusy() {
        if (busyUntil == 0) {
            return;
        }
        busyUntil = 0;
        x11.XDefineCursor(display, new NativeLong(rootWindow), cursorDefault);
        // Frames the pointer crossed meanwhile got the watch; the others inherit the root's cursor.
        for (FrameWindow frame : clientToFrame.values()) {
            x11.XDefineCursor(display, frame.frameWindow(), cursorDefault);
        }
        x11.XDeleteProperty(display, new NativeLong(rootWindow), launchBusyAtom);
        x11.XFlush(display);
    }

    private NativeLong getCursorForDirection(ResizeDirection dir) {
        if (dir == null) return idleCursor();
        return switch (dir) {
            case N -> cursorN;
            case S -> cursorS;
            case W -> cursorW;
            case E -> cursorE;
            case NW -> cursorNW;
            case NE -> cursorNE;
            case SW -> cursorSW;
            case SE -> cursorSE;
            case NONE -> idleCursor();
        };
    }

    public boolean isDockWindow(long win) {
        if (dockWindows.contains(win)) {
            return true;
        }
        // A dock says so with _NET_WM_WINDOW_TYPE_DOCK, or with a strut: only panels reserve screen
        // space, and AWT's XToolkit overwrites the type of a Swing panel with _NET_WM_WINDOW_TYPE_NORMAL
        // when it maps the window under a window manager (the strut it leaves alone). The window manager
        // knows no panel by name, so any panel can take the front panel's place.
        if (hasStrut(win)) {
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

    /** Whether the window reserves screen space with _NET_WM_STRUT_PARTIAL or _NET_WM_STRUT. */
    private boolean hasStrut(long win) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        com.sun.jna.ptr.NativeLongByReference nitemsRet = new com.sun.jna.ptr.NativeLongByReference();
        com.sun.jna.ptr.NativeLongByReference bytesAfterRet = new com.sun.jna.ptr.NativeLongByReference();
        PointerByReference propRet = new PointerByReference();
        for (NativeLong atom : new NativeLong[]{netWmStrutPartial, netWmStrut}) {
            int status = x11.XGetWindowProperty(
                    display, new NativeLong(win), atom,
                    new NativeLong(0), new NativeLong(12), 0,
                    new NativeLong(LibX11.XA_CARDINAL),
                    actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);
            if (status == 0 && propRet.getValue() != null) {
                x11.XFree(propRet.getValue());
                if (nitemsRet.getValue() != null && nitemsRet.getValue().longValue() >= 4) {
                    return true;
                }
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
        byte[] nameBytes = "amadeOS".getBytes(StandardCharsets.UTF_8);
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

        // _NET_DESKTOP_NAMES on root: the user's names, else "One\0Two\0Three\0Four\0"; the Front Panel
        // publishes them again in the desktop language and whenever a workspace is renamed.
        WorkspaceNames userNames = WorkspaceNames.user();
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < NUMBER_OF_DESKTOPS; i++) {
            names.append(userNames.name(i, DEFAULT_WORKSPACE_NAMES[i])).append('\0');
        }
        byte[] namesBytes = names.toString().getBytes(StandardCharsets.UTF_8);
        Memory namesMem = new Memory(namesBytes.length);
        namesMem.write(0, namesBytes, 0, namesBytes.length);
        x11.XChangeProperty(display, new NativeLong(rootWindow), netDesktopNames, utf8String, 8, LibX11.PropModeReplace, namesMem, namesBytes.length);
        readDesktopNames();

        // _NET_SUPPORTED on root
        NativeLong[] supportedAtoms = new NativeLong[]{
            netSupportingWmCheck, netWmName, netSupported, netClientList, netActiveWindow, netCloseWindow,
            netWmWindowType, netWmWindowTypeDock, netWmState, netWmStateHidden, netWmStateFullscreen, netWmStrut, netWmStrutPartial,
            netNumberOfDesktops, netCurrentDesktop, netDesktopNames, netWmDesktop, netWmPing
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
        focusClient(win, true);
    }

    /** Makes a window active; {@code raise} brings it to the front as well. */
    private void focusClient(long win, boolean raise) {
        if (win == 0 || core.registry().find(win).isEmpty()) {
            return;
        }
        // The window losing the focus is in full view until the new one comes up: keep its picture for
        // Alt+Tab. During a switcher round the popup is on screen, so nothing is read then.
        if (switcher == null && activeWindow != 0 && activeWindow != win) {
            capturePreview(activeWindow);
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
        recentWindows.touch(win);
        ungrabWindowButtons(win);
        core.focus(win);
        if (raise) core.raise(win);
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

    /**
     * The window that becomes active when {@code gone} is closed, withdrawn or minimized: its owner, else the
     * topmost visible window on this workspace; 0 if there is none.
     */
    private long successor(long owner, long gone) {
        List<Long> stack = core.registry().snapshot().stream().map(WindowRecord::id).toList();
        return FocusSuccessor.choose(stack, owner, id -> {
            if (id == gone) return false;
            FrameWindow frame = clientToFrame.get(id);
            return frame != null && !frame.isMinimized() && getClientDesktop(id) == currentDesktop;
        });
    }

    /** The window {@code win} is a dialog of (WM_TRANSIENT_FOR), or 0. */
    private long transientOwner(long win) {
        Memory parentMem = new Memory(NativeLong.SIZE);
        if (x11.XGetTransientForHint(display, new NativeLong(win), parentMem) != 0) {
            return parentMem.getNativeLong(0).longValue();
        }
        return 0;
    }

    /**
     * Whether {@code win} is a dialog of a window without frame, like the front panel or Swing's hidden
     * owner frame. Such dialogs are centred by the window manager, as the owner-relative position the
     * application computes for them is meaningless.
     */
    private boolean hasUnmanagedOwner(long win) {
        long owner = transientOwner(win);
        return owner != 0 && core.registry().find(owner).isEmpty();
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

    /**
     * The window's title: _NET_WM_NAME (UTF-8) first, as EWMH asks, then the legacy WM_NAME. Java and every
     * toolkit set both; WM_NAME holds non-Latin-1 titles as COMPOUND_TEXT, which read as bytes turned an
     * em dash or an umlaut into replacement characters in the title bar.
     */
    public String fetchWindowTitle(long win) {
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
                    String title = ptr.getString(0, "UTF-8");
                    if (title != null && !title.isBlank()) {
                        return title;
                    }
                } finally {
                    x11.XFree(ptr);
                }
            }
        }

        PointerByReference nameRet = new PointerByReference();
        if (x11.XFetchName(display, new NativeLong(win), nameRet) != 0) {
            Pointer ptr = nameRet.getValue();
            if (ptr != null) {
                try {
                    // WM_NAME as STRING is Latin-1 (ICCCM).
                    String title = ptr.getString(0, "ISO-8859-1");
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
        int[] before = withdrawnFrames.remove(clientWin);
        if (before != null && before[2] == clientX && before[3] == clientY) {
            frameX = before[0];
            frameY = before[1];
        }

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
        runPendingActions();
        int processed = 0;
        while (x11.XPending(display) > 0) {
            processNextEvent();
            processed++;
        }
        return processed;
    }

    public void startEventLoop() {
        eventThread = Thread.currentThread();
        running.set(true);
        scanExistingWindows();
        enableSoundCardBell();
        LOGGER.info(() -> "amadeOS Window Manager started event loop on screen " + screenWidth + "x" + screenHeight);
        busyTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                runOnEventThread(X11WindowManagerService.this::pingTick);
            }
        }, PING_PERIOD_MILLIS, PING_PERIOD_MILLIS);
        while (running.get()) {
            try {
                runPendingActions();
                if (!running.get()) {
                    break;
                }
                processNextEvent();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing X event", e);
            }
        }
        LOGGER.info("amadeOS Window Manager event loop terminated");
    }

    public void stop() {
        running.set(false);
        Thread loop = eventThread;
        if (loop != null && loop != Thread.currentThread()) {
            wakeEventLoop();
        }
    }

    /**
     * Runs an action that uses the X display on the event loop thread. Called on that thread,
     * or before the loop has started (e.g. in tests driving the service directly), the action
     * runs immediately; otherwise it is queued and the loop is woken up.
     */
    public void runOnEventThread(Runnable action) {
        Thread loop = eventThread;
        if (loop == null || loop == Thread.currentThread()) {
            action.run();
            return;
        }
        eventThreadActions.add(action);
        wakeEventLoop();
    }

    DesktopMenuActions menuActions() {
        return menuActions;
    }

    private void runPendingActions() {
        Runnable action;
        boolean ran = false;
        while ((action = eventThreadActions.poll()) != null) {
            ran = true;
            try {
                action.run();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Error running window manager action", e);
            }
        }
        if (ran) {
            x11.XFlush(display);
        }
    }

    /**
     * Sends a ClientMessage to the check window over a second connection that only this
     * method uses, so the event loop's blocking XNextEvent returns and the queue is drained.
     */
    private void wakeEventLoop() {
        if (checkWindow == 0) {
            return;
        }
        synchronized (wakeLock) {
            if (wakeDisplay == null) {
                wakeDisplay = x11.XOpenDisplay(displayName);
                if (wakeDisplay == null) {
                    LOGGER.warning("Cannot open a second X connection to wake the window manager event loop");
                    return;
                }
            }
            // Allocate a full XEvent: XSendEvent copies sizeof(XEvent) bytes.
            LibX11.XEvent buffer = new LibX11.XEvent();
            buffer.write();
            LibX11.XClientMessageEvent wake = new LibX11.XClientMessageEvent(buffer.getPointer());
            wake.type = LibX11.ClientMessage;
            wake.send_event = 1;
            wake.display = wakeDisplay;
            wake.window = new NativeLong(checkWindow);
            wake.message_type = wakeAtom;
            wake.format = 32;
            for (int i = 0; i < wake.data.length; i++) {
                wake.data[i] = new NativeLong(0);
            }
            wake.write();
            // Event mask 0: delivered to the client that created the check window, i.e. the event loop.
            x11.XSendEvent(wakeDisplay, new NativeLong(checkWindow), 0, new NativeLong(0), buffer.getPointer());
            x11.XFlush(wakeDisplay);
        }
    }

    void handleEvent(LibX11.XEvent event) {
        switch (event.type) {
            case LibX11.KeyPress -> {
                LibX11.XKeyEvent keyEvent = new LibX11.XKeyEvent(event.getPointer());
                if (powerButton != null && keyEvent.keycode == powerKeycode) {
                    powerButton.pressed(LockSelection.isLocked(x11, display));
                    return;
                }
                if (LockSelection.isLocked(x11, display)) {
                    LOGGER.fine("Screen is locked; ignoring window manager key press");
                    if (switcher != null) cancelSwitcher();
                    return;
                }
                if (switcher != null) {
                    handleSwitcherKey(keyEvent);
                    return;
                }
                boolean isAlt = (keyEvent.state & (int) LibX11.Mod1Mask) != 0;
                boolean isCtrl = (keyEvent.state & (int) LibX11.ControlMask) != 0;
                boolean isShift = (keyEvent.state & (int) LibX11.ShiftMask) != 0;
                if (isCtrl && ((isShift && !isAlt && keyEvent.keycode == taskManagerEscapeKeycode)
                        || (isAlt && deleteKeycode != 0 && keyEvent.keycode == deleteKeycode))) {
                    startTaskManager(List.of());
                    return;
                }
                if (isCtrl && isAlt && !isShift && terminalKeycode != 0 && keyEvent.keycode == terminalKeycode) {
                    startTerminal();
                    return;
                }
                if (isAlt && tabKeycode != 0 && keyEvent.keycode == tabKeycode) {
                    openSwitcher((keyEvent.state & (int) LibX11.ShiftMask) != 0);
                    return;
                }
                // Check if Super modifier (Mod4Mask) is held and keycode matches 'l'
                boolean isSuper = (keyEvent.state & (int) LibX11.Mod4Mask) != 0;
                if (isSuper && (lockKeycode != 0 && keyEvent.keycode == lockKeycode)) {
                    LOGGER.info("Super+L pressed: starting the amadeOS screen lock");
                    LockLauncher.lockScreen();
                }
            }
            case LibX11.KeyRelease -> {
                LibX11.XKeyEvent keyEvent = new LibX11.XKeyEvent(event.getPointer());
                if (switcher != null && altKeycodes.contains(keyEvent.keycode)) {
                    commitSwitcher();
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
                long transientFor = transientOwner(win);

                FrameMetrics m = theme.metrics();
                // A new window fits between the top of the screen and the top of the front panel
                int maxUsableH = Math.max(100, screenHeight - dockMarginBottom);
                int clientW = Math.min(Math.max(100, w), m.clientWidth(screenWidth));
                int clientH = Math.min(Math.max(60, h), m.clientHeight(maxUsableH));
                int frameW = m.frameWidth(clientW);
                int frameH = m.frameHeight(clientH);

                int frameX, frameY;
                var parentRecord = (transientFor != 0) ? core.registry().find(transientFor) : Optional.<WindowRecord>empty();
                if (parentRecord.isPresent()) {
                    WindowGeometry parentGeom = parentRecord.get().geometry();
                    frameX = parentGeom.x() + (parentGeom.width() - frameW) / 2;
                    frameY = parentGeom.y() + (parentGeom.height() - frameH) / 2;
                    frameX = Math.max(10, Math.min(frameX, screenWidth - frameW - 10));
                    frameY = Math.max(10, Math.min(frameY, maxUsableH - frameH - 10));
                } else if (transientFor != 0) {
                    // Dialog of an unmanaged window (the front panel, or Swing's hidden owner frame):
                    // its owner-relative position is meaningless, so centre it on the usable screen
                    frameX = (screenWidth - frameW) / 2;
                    frameY = (maxUsableH - frameH) / 2;
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
                // Keep the whole frame on screen and above the front panel, whatever the branch above chose
                frameX = Math.max(0, Math.min(frameX, screenWidth - frameW));
                frameY = Math.max(0, Math.min(frameY, maxUsableH - frameH));
                WindowGeometry initialGeom = new WindowGeometry(frameX, frameY, clientW, clientH);

                // The new window will cover the active one: keep the active one's picture for Alt+Tab first.
                if (switcher == null && activeWindow != 0) capturePreview(activeWindow);
                FrameWindow frame = decorateAndReparent(win, initialGeom);
                WindowGeometry managedGeom = (frame != null)
                        ? new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight())
                        : initialGeom;

                core.manage(win, managedGeom);
                core.adapter().configureWindow(win, managedGeom);
                if (transientFor != 0) transientOwners.put(win, transientFor);

                grabWindowButtons(win);
                focusClient(win);
                // Clients set _NET_WM_STATE themselves before mapping (EWMH), e.g. Java's setFullScreenWindow
                if (frame != null && hasWmState(win, netWmStateFullscreen)) {
                    setFullScreen(frame, true);
                }
                updateClientList();
                endBusy();
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

                // Desktop background click with Button 3 shows the Root Menu; any other click the
                // window manager sees is outside the menu (its own window is unmanaged) and closes it.
                if (eventWin == rootWindow && btnEvent.button == LibX11.Button3) {
                    LOGGER.info(() -> "ButtonPress 3 on Root Window: popping up Desktop Root Menu at "
                            + btnEvent.x_root + "," + btnEvent.y_root);
                    desktopMenu.show(btnEvent.x_root, btnEvent.y_root);
                    x11.XFlush(display);
                    return;
                }
                desktopMenu.hide();
                windowMenu.hide();
                if (eventWin == rootWindow) {
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
                    boolean raise = windowSettings.raiseWhenActive();
                    focusClient(eventWin, raise);
                    LOGGER.fine(() -> "ButtonPress: focused client window " + eventWin + (raise ? " and raised it" : ""));
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
            case LibX11.EnterNotify -> {
                LibX11.XCrossingEvent crossing = new LibX11.XCrossingEvent(event.getPointer());
                handleEnter(crossing);
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
                if (win == rootWindow && prop.atom != null && prop.atom.equals(netDesktopNames)) {
                    readDesktopNames();
                    break;
                }
                if (win == rootWindow && prop.atom != null && prop.atom.equals(amadeosThemeAtom)) {
                    String themeId = fetchRootPropertyString(amadeosThemeAtom);
                    if (themeId != null && !themeId.isEmpty()) {
                        ThemeManager.applyTheme(themeId);
                    }
                    break;
                }
                if (previews != null && previews.isIconProperty(prop.atom)) {
                    previews.iconChanged(win);
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
                if (wakeAtom != null && wakeAtom.equals(cmsg.message_type)) {
                    return; // only wakes the loop; queued actions run before the next XNextEvent
                }
                long win = cmsg.window.longValue();
                if (wmProtocols.equals(cmsg.message_type) && cmsg.format == 32
                        && cmsg.data[0] != null && cmsg.data[0].longValue() == netWmPing.longValue()) {
                    // The answer to _NET_WM_PING comes to the root window; data[2] names the client window.
                    pingAnswered(cmsg.data[2] == null ? 0 : cmsg.data[2].longValue());
                    return;
                }
                if (launchAtom.equals(cmsg.message_type)) {
                    startBusy();
                } else if (cmsg.message_type != null && cmsg.message_type.equals(netCloseWindow)) {
                    requestClose(win);
                    LOGGER.info(() -> "ClientMessage _NET_CLOSE_WINDOW handled for " + win);
                } else if (cmsg.message_type != null && cmsg.message_type.equals(netActiveWindow)) {
                    focusClient(win);
                    // An application that was already running (Firefox opening a tab) raises its window
                    endBusy();
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
                    if (prop1 == netWmStateFullscreen.longValue() || prop2 == netWmStateFullscreen.longValue()) {
                        FrameWindow frame = clientToFrame.get(win);
                        if (frame != null) {
                            boolean on = action == 1 /* ADD */ || (action == 2 /* TOGGLE */ && !frame.isFullScreen());
                            setFullScreen(frame, on);
                            LOGGER.info(() -> "ClientMessage _NET_WM_STATE_FULLSCREEN " + (on ? "on" : "off") + " for " + win);
                        }
                    }
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
            case LibX11.ConfigureNotify -> {
                LibX11.XConfigureEvent cfg = new LibX11.XConfigureEvent(event.getPointer());
                if (cfg.window.longValue() == rootWindow) {
                    screenResized(cfg.width, cfg.height);
                }
            }
            case LibX11.ConfigureRequest -> {
                LibX11.XConfigureRequestEvent cfg = new LibX11.XConfigureRequestEvent(event.getPointer());
                long win = cfg.window.longValue();
                FrameWindow frame = clientToFrame.get(win);
                if (frame != null && frame.isFullScreen()) {
                    // A full screen client keeps the whole screen; tell it so instead of moving it
                    sendSyntheticConfigureNotify(frame);
                    x11.XFlush(display);
                } else if (frame != null) {
                    FrameMetrics m = theme.metrics();
                    long mask = (cfg.value_mask != null) ? cfg.value_mask.longValue() : 0L;
                    int maxUsableH = Math.max(100, screenHeight - dockMarginBottom);
                    int clientW = ((mask & LibX11.CWWidth) != 0) ? Math.max(100, cfg.width) : frame.clientWidth();
                    int clientH = ((mask & LibX11.CWHeight) != 0) ? Math.max(60, cfg.height) : frame.clientHeight();
                    clientW = Math.min(clientW, m.clientWidth(screenWidth));
                    clientH = Math.min(clientH, m.clientHeight(maxUsableH));
                    int frameW = m.frameWidth(clientW);
                    int frameH = m.frameHeight(clientH);

                    int frameX = frame.frameX();
                    int frameY = frame.frameY();
                    boolean centred = hasUnmanagedOwner(win);
                    if (centred) {
                        // Swing moves a dialog right after mapping it; keep it centred (see MapRequest)
                        frameX = (screenWidth - frameW) / 2;
                        frameY = (maxUsableH - frameH) / 2;
                    } else {
                        if ((mask & LibX11.CWX) != 0) {
                            frameX = cfg.x;
                        }
                        if ((mask & LibX11.CWY) != 0) {
                            frameY = cfg.y;
                        }
                    }
                    // Like new windows, the frame stays on screen and above the front panel
                    frameX = Math.max(0, Math.min(frameX, screenWidth - frameW));
                    frameY = Math.max(0, Math.min(frameY, maxUsableH - frameH));

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
                cleanupWindow(win, false);
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
                // The client withdrew its window (e.g. Swing's setVisible(false)): it still exists and may be
                // mapped again, so it goes back to the root before its frame is destroyed.
                cleanupWindow(win, true);
            }
            default -> {
                if (xkbEventBase >= 0 && event.type == xkbEventBase) {
                    handleXkbEvent(event);
                }
            }
        }
    }

    /**
     * Moves the bell from the PC speaker, which most computers and QEMU lack, to the sound card: the
     * server's audible bell is switched off and every bell is reported as an XKB event instead, which
     * {@link #handleXkbEvent} plays with the volume, tone and duration set by {@code xset b} (Style
     * Manager, Beep). Without XKB or libX11's XKB functions the server's bell stays as it is.
     */
    private void enableSoundCardBell() {
        try {
            xkb = LibXkb.load();
            IntByReference opcode = new IntByReference();
            IntByReference eventBase = new IntByReference();
            IntByReference errorBase = new IntByReference();
            IntByReference major = new IntByReference(LibXkb.XkbMajorVersion);
            IntByReference minor = new IntByReference(LibXkb.XkbMinorVersion);
            if (!xkb.XkbQueryExtension(display, opcode, eventBase, errorBase, major, minor)) {
                LOGGER.warning("X server without XKB; the bell stays on the PC speaker");
                return;
            }
            NativeLong bellMask = new NativeLong(LibXkb.XkbBellNotifyMask);
            if (!xkb.XkbSelectEvents(display, LibXkb.XkbUseCoreKbd, bellMask, bellMask)) {
                LOGGER.warning("XKB bell events not available; the bell stays on the PC speaker");
                return;
            }
            bellPlayer = new BellPlayer();
            xkb.XkbChangeEnabledControls(display, LibXkb.XkbUseCoreKbd, LibXkb.XkbAudibleBellMask, 0);
            xkbEventBase = eventBase.getValue();
            x11.XFlush(display);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Cannot take over the bell; it stays on the PC speaker", e);
        }
    }

    private void handleXkbEvent(LibX11.XEvent event) {
        LibXkb.XkbBellNotifyEvent bell = new LibXkb.XkbBellNotifyEvent(event.getPointer());
        if (bell.xkb_type == LibXkb.XkbBellNotify && bellPlayer != null) {
            bellPlayer.ring(bell.percent, bell.pitch, bell.duration);
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

            // Draw initial wireframe outline, unless the window itself moves (Settings > Window)
            wireframeX = dragStartFrameX;
            wireframeY = dragStartFrameY;
            wireframeW = dragStartFrameW;
            wireframeH = dragStartFrameH;
            wireframeActive = !windowSettings.showContentsDuringMove();
            if (wireframeActive) toggleWireframe(wireframeX, wireframeY, wireframeW, wireframeH);

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

    /**
     * Point in window to make active (Settings &gt; Window): the pointer entering a window from outside makes it
     * active. Moves between a frame and its client, and the crossings a grab causes, change nothing.
     */
    private void handleEnter(LibX11.XCrossingEvent crossing) {
        if (windowSettings.focus() != WindowSettings.Focus.POINTER || interactionType != InteractionType.NONE
                || crossing.mode != LibX11.NotifyNormal || crossing.detail == LibX11.NotifyInferior) {
            return;
        }
        FrameWindow frame = frameToClient.get(crossing.window.longValue());
        if (frame == null || frame.isMinimized()) return;
        long clientWin = frame.clientWindow().longValue();
        if (clientWin != activeWindow) {
            focusClient(clientWin, windowSettings.raiseWhenActive());
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
                x11.XDefineCursor(display, interactionFrame.frameWindow(), idleCursor());
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
            } else if (!wireframeActive && (newX != wireframeX || newY != wireframeY)) {
                // Show contents during move: the frame itself follows the pointer
                wireframeX = newX;
                wireframeY = newY;
                interactionFrame.setBounds(wireframeX, wireframeY, wireframeW, wireframeH);
                x11.XMoveWindow(display, interactionFrame.frameWindow(), wireframeX, wireframeY);
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

    // --8<-- [start:ping]
    /** Asks the window to close (WM_DELETE_WINDOW) and checks with a ping that its program still answers. */
    private void requestClose(long clientWin) {
        core.close(clientWin);
        ping(clientWin);
    }

    /** Sends _NET_WM_PING to a window that supports it, unless an earlier ping is still unanswered. */
    private void ping(long clientWin) {
        if (clientWin == 0 || !clientToFrame.containsKey(clientWin) || !pings.shouldPing(clientWin)
                || !supportsProtocol(clientWin, netWmPing)) {
            return;
        }
        LibX11.XEvent buffer = new LibX11.XEvent();
        buffer.write();
        LibX11.XClientMessageEvent message = new LibX11.XClientMessageEvent(buffer.getPointer());
        message.type = LibX11.ClientMessage;
        message.send_event = 1;
        message.display = display;
        message.window = new NativeLong(clientWin);
        message.message_type = wmProtocols;
        message.format = 32;
        message.data[0] = netWmPing;
        message.data[1] = new NativeLong(LibX11.CurrentTime);
        message.data[2] = new NativeLong(clientWin);
        message.data[3] = new NativeLong(0);
        message.data[4] = new NativeLong(0);
        message.write();
        x11.XSendEvent(display, new NativeLong(clientWin), 0, new NativeLong(0), buffer.getPointer());
        x11.XFlush(display);
        pings.sent(clientWin, System.currentTimeMillis());
    }

    /** Every {@link #PING_PERIOD_MILLIS}: pings the active window and marks those that have not answered. */
    private void pingTick() {
        long now = System.currentTimeMillis();
        for (long win : pings.newlyNotResponding(now)) {
            markNotResponding(win, true);
        }
        if (activeWindow != 0) ping(activeWindow);
    }

    private void pingAnswered(long clientWin) {
        if (pings.answered(clientWin)) {
            markNotResponding(clientWin, false);
        }
    }

    /**
     * Marks a window as not responding, or takes the mark away: the title bar says so, the client window
     * gets {@code _AMADEOS_NOT_RESPONDING} (which the Task Manager and its dialog read), and a newly hanging
     * window brings up the Task Manager's "not responding" dialog.
     */
    private void markNotResponding(long clientWin, boolean notResponding) {
        FrameWindow frame = clientToFrame.get(clientWin);
        if (frame == null) return;
        NativeLong client = new NativeLong(clientWin);
        if (notResponding) {
            LOGGER.info(() -> "Window " + clientWin + " does not answer _NET_WM_PING");
            if (hangTexts == null) hangTexts = HangTexts.load();
            frame.setTitleSuffix(hangTexts.get("title.not-responding"));
            Memory one = new Memory(NativeLong.SIZE);
            one.setNativeLong(0, new NativeLong(1));
            x11.XChangeProperty(display, client, amadeosNotResponding, xaCardinalAtom, 32,
                    LibX11.PropModeReplace, one, 1);
            startTaskManager(List.of("--not-responding", Long.toString(clientWin)));
        } else {
            LOGGER.info(() -> "Window " + clientWin + " answers again");
            frame.setTitleSuffix(null);
            x11.XDeleteProperty(display, client, amadeosNotResponding);
        }
        frame.render();
        frame.blit(display, gc, screen);
        x11.XFlush(display);
    }

    private boolean supportsProtocol(long clientWin, NativeLong protocol) {
        com.sun.jna.ptr.PointerByReference protocols = new com.sun.jna.ptr.PointerByReference();
        com.sun.jna.ptr.IntByReference count = new com.sun.jna.ptr.IntByReference();
        if (x11.XGetWMProtocols(display, new NativeLong(clientWin), protocols, count) == 0) return false;
        Pointer list = protocols.getValue();
        if (list == null) return false;
        try {
            for (int i = 0; i < count.getValue(); i++) {
                if (list.getNativeLong((long) i * NativeLong.SIZE).longValue() == protocol.longValue()) return true;
            }
            return false;
        } finally {
            x11.XFree(list);
        }
    }
    // --8<-- [end:ping]

    /** Starts the Task Manager (package amadeos-taskmanager), which shows its running window if there is one. */
    private static void startTaskManager(List<String> arguments) {
        List<String> command = new java.util.ArrayList<>();
        command.add(TASK_MANAGER);
        command.addAll(arguments);
        try {
            eu.amadeos.system.base.process.ProcessLauncher.start(command);
        } catch (java.io.IOException e) {
            LOGGER.warning(() -> "Could not start the Task Manager (" + TASK_MANAGER + "): " + e.getMessage());
        }
    }

    private static void startTerminal() {
        String terminal = eu.amadeos.system.base.process.Terminals.program();
        try {
            eu.amadeos.system.base.process.ProcessLauncher.start(List.of(terminal));
        } catch (java.io.IOException e) {
            LOGGER.warning(() -> "Could not start the terminal (" + terminal + "): " + e.getMessage());
        }
    }

    private void handleButtonClick(FrameWindow frame, WindowButton button) {
        long clientWin = frame.clientWindow().longValue();
        switch (button) {
            case CLOSE -> {
                LOGGER.info(() -> "Close button clicked for window " + clientWin);
                requestClose(clientWin);
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
                // As in dtwm: a click opens the window menu below the button, a double click closes.
                long now = System.currentTimeMillis();
                if (lastMenuButtonWin == clientWin && now - lastMenuButtonTime <= MENU_DOUBLE_CLICK_MS) {
                    LOGGER.info(() -> "Menu button double-clicked, closing window " + clientWin);
                    lastMenuButtonWin = 0;
                    windowMenu.hide();
                    requestClose(clientWin);
                } else {
                    LOGGER.info(() -> "Menu button clicked, window menu for window " + clientWin);
                    lastMenuButtonWin = clientWin;
                    lastMenuButtonTime = now;
                    FrameMetrics m = frame.metrics();
                    windowMenu.show(clientWin, frame.isMaximized(),
                            frame.frameX() + m.borderWidth(), frame.frameY() + m.borderWidth() + m.titleBarHeight());
                }
            }
        }
    }

    /**
     * Puts a client into full screen (_NET_WM_STATE_FULLSCREEN) or back. The frame stays, but moves so
     * that its title bar and borders lie off screen and the client covers the whole screen, above the
     * front panel; leaving full screen restores the frame bounds from before.
     */
    private void setFullScreen(FrameWindow frame, boolean on) {
        if (frame.isFullScreen() == on) {
            return;
        }
        long clientWin = frame.clientWindow().longValue();
        FrameMetrics m = frame.metrics();
        if (on) {
            frame.setNormalBounds(new Rectangle(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight()));
            frame.setBounds(-m.clientOffsetX(), -m.clientOffsetY(), m.frameWidth(screenWidth), m.frameHeight(screenHeight));
            frame.setFullScreen(true);
            Memory stateMem = new Memory(NativeLong.SIZE);
            stateMem.setNativeLong(0, netWmStateFullscreen);
            x11.XChangeProperty(display, new NativeLong(clientWin), netWmState, xaAtomAtom, 32, LibX11.PropModeReplace, stateMem, 1);
        } else {
            Rectangle r = frame.normalBounds();
            if (r == null) {
                r = new Rectangle(50, 50, 600, 400);
            }
            frame.setBounds(r.x, r.y, r.width, r.height);
            frame.setFullScreen(false);
            x11.XDeleteProperty(display, new NativeLong(clientWin), netWmState);
        }
        x11.XMoveResizeWindow(display, frame.frameWindow(), frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight());
        x11.XMoveResizeWindow(display, frame.clientWindow(),
                frame.clientX(), frame.clientY(), frame.clientWidth(), frame.clientHeight());
        frame.render();
        frame.blit(display, gc, screen);
        sendSyntheticConfigureNotify(frame);
        core.registry().updateGeometry(clientWin,
                new WindowGeometry(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight()));
        if (on) {
            x11.XRaiseWindow(display, frame.frameWindow());
        } else {
            for (long dock : dockWindows) {
                x11.XRaiseWindow(display, new NativeLong(dock));
            }
        }
        x11.XFlush(display);
    }

    /** Whether the client's _NET_WM_STATE property contains {@code state}. */
    private boolean hasWmState(long win, NativeLong state) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        com.sun.jna.ptr.NativeLongByReference nitemsRet = new com.sun.jna.ptr.NativeLongByReference();
        com.sun.jna.ptr.NativeLongByReference bytesAfterRet = new com.sun.jna.ptr.NativeLongByReference();
        PointerByReference propRet = new PointerByReference();
        int status = x11.XGetWindowProperty(
                display, new NativeLong(win), netWmState,
                new NativeLong(0), new NativeLong(32), 0,
                new NativeLong(LibX11.XA_ATOM),
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);
        if (status != 0 || propRet.getValue() == null) {
            return false;
        }
        Pointer ptr = propRet.getValue();
        try {
            long count = nitemsRet.getValue() != null ? nitemsRet.getValue().longValue() : 0;
            for (long i = 0; i < count; i++) {
                if (ptr.getNativeLong(i * NativeLong.SIZE).longValue() == state.longValue()) {
                    return true;
                }
            }
            return false;
        } finally {
            x11.XFree(ptr);
        }
    }

    /** Runs the action with the frame of the client window, if it is still managed. */
    private int keycode(long keysym) {
        return Byte.toUnsignedInt(x11.XKeysymToKeycode(display, new NativeLong(keysym)));
    }

    private boolean onCurrentDesktop(long clientWin) {
        int desk = getClientDesktop(clientWin);
        return desk == currentDesktop || isSticky(desk);
    }

    /** Whether the window is mapped and visible on this workspace. */
    private boolean isShown(long clientWin) {
        FrameWindow frame = clientToFrame.get(clientWin);
        return frame != null && !frame.isMinimized() && onCurrentDesktop(clientWin);
    }

    private static Rectangle bounds(FrameWindow frame) {
        return new Rectangle(frame.frameX(), frame.frameY(), frame.frameWidth(), frame.frameHeight());
    }

    // --8<-- [start:preview]
    /** Keeps a preview of the window for Alt+Tab, if nothing covers it right now. */
    private void capturePreview(long clientWin) {
        FrameWindow frame = clientToFrame.get(clientWin);
        if (previews == null || frame == null || !isShown(clientWin)) {
            return;
        }
        try {
            List<Rectangle> above = new java.util.ArrayList<>();
            boolean isAbove = false;
            for (WindowRecord rec : core.registry().snapshot()) {
                if (rec.id() == clientWin) {
                    isAbove = true;
                } else if (isAbove && isShown(rec.id())) {
                    above.add(bounds(clientToFrame.get(rec.id())));
                }
            }
            // Minimized windows' tiles lie on top of every window.
            for (DesktopIconTile tile : iconTiles) {
                if (onCurrentDesktop(tile.clientWin())) {
                    above.add(new Rectangle(tile.x(), tile.y(), DesktopIconTile.TILE_WIDTH, DesktopIconTile.TILE_HEIGHT));
                }
            }
            int usableHeight = frame.isFullScreen() ? screenHeight : screenHeight - dockMarginBottom;
            Rectangle area = PreviewArea.readable(bounds(frame), above, new Rectangle(0, 0, screenWidth, usableHeight));
            if (area != null) {
                previews.capture(clientWin, area);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Cannot keep a preview of window " + clientWin, e);
        }
    }
    // --8<-- [end:preview]

    /** The windows Alt+Tab offers, the topmost first: this workspace's, without dialogs of another window. */
    private List<Long> switcherCandidates() {
        List<Long> result = new java.util.ArrayList<>();
        List<WindowRecord> stack = new java.util.ArrayList<>(core.registry().snapshot());
        for (int i = stack.size() - 1; i >= 0; i--) {
            long win = stack.get(i).id();
            if (!clientToFrame.containsKey(win) || dockWindows.contains(win) || !onCurrentDesktop(win)) continue;
            Long owner = transientOwners.get(win);
            if (owner != null && clientToFrame.containsKey(owner)) continue;
            result.add(win);
        }
        return result;
    }

    // --8<-- [start:open-switcher]
    /** Alt+Tab: grabs the keyboard and shows the popup with the window that was active before selected. */
    void openSwitcher(boolean backwards) {
        List<Long> candidates = switcherCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        // Whatever is in full view right now gives a fresh picture, before the popup covers the screen.
        for (long win : candidates) capturePreview(win);
        List<SwitcherEntry> entries = new java.util.ArrayList<>();
        for (long win : recentWindows.order(candidates)) {
            FrameWindow frame = clientToFrame.get(win);
            entries.add(new SwitcherEntry(win, frame.title(), previews.preview(win), previews.icon(win), frame.isMinimized()));
        }
        switcher = new SwitcherSelection(entries, backwards);
        int grab = x11.XGrabKeyboard(display, new NativeLong(rootWindow), 0,
                LibX11.GrabModeAsync, LibX11.GrabModeAsync, new NativeLong(LibX11.CurrentTime));
        if (grab != LibX11.GrabSuccess) {
            LOGGER.fine(() -> "Alt+Tab: keyboard grab failed (" + grab + "), switching without the popup");
            commitSwitcher();
            return;
        }
        // A quick Alt+Tab may be over before the grab: then switch straight away, as Windows does.
        com.sun.jna.ptr.IntByReference mask = new com.sun.jna.ptr.IntByReference();
        x11.XQueryPointer(display, new NativeLong(rootWindow), new com.sun.jna.ptr.NativeLongByReference(),
                new com.sun.jna.ptr.NativeLongByReference(), new IntByReference(), new IntByReference(),
                new IntByReference(), new IntByReference(), mask);
        if ((mask.getValue() & (int) LibX11.Mod1Mask) == 0) {
            commitSwitcher();
            return;
        }
        switcherPopup.show(switcher.entries(), switcher.selectedIndex());
        LOGGER.fine(() -> "Alt+Tab: switcher open with " + entries.size() + " windows");
    }
    // --8<-- [end:open-switcher]

    private void handleSwitcherKey(LibX11.XKeyEvent key) {
        boolean shift = (key.state & (int) LibX11.ShiftMask) != 0;
        if (key.keycode == tabKeycode) {
            switcher.move(shift ? -1 : 1);
        } else if (key.keycode == leftKeycode) {
            switcher.move(-1);
        } else if (key.keycode == rightKeycode) {
            switcher.move(1);
        } else if (key.keycode == escapeKeycode) {
            cancelSwitcher();
            return;
        } else if (key.keycode == returnKeycode) {
            commitSwitcher();
            return;
        } else {
            return;
        }
        switcherPopup.select(switcher.selectedIndex());
    }

    /** Alt released (or a tile clicked): brings the selected window to the front and closes the switcher. */
    void commitSwitcher() {
        if (switcher == null) {
            return;
        }
        long win = switcher.selected().clientWin();
        closeSwitcher();
        if (clientToFrame.containsKey(win)) {
            focusClient(win);
            // Its open dialogs come up with it, the topmost last so that it gets the focus.
            for (WindowRecord rec : core.registry().snapshot()) {
                Long owner = transientOwners.get(rec.id());
                if (owner != null && owner == win && isShown(rec.id())) focusClient(rec.id());
            }
        }
        switcher = null;
    }

    void cancelSwitcher() {
        closeSwitcher();
        switcher = null;
    }

    private void closeSwitcher() {
        switcherPopup.hide();
        x11.XUngrabKeyboard(display, new NativeLong(LibX11.CurrentTime));
        x11.XFlush(display);
    }

    /** The open Alt+Tab round, for tests; null while the switcher is closed. */
    SwitcherSelection switcher() {
        return switcher;
    }

    private void withFrame(long clientWin, java.util.function.Consumer<FrameWindow> action) {
        FrameWindow frame = clientToFrame.get(clientWin);
        if (frame != null) {
            action.accept(frame);
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
        long clientWin = frame.clientWindow().longValue();
        if (switcher == null) capturePreview(clientWin);
        frame.setMinimized(true);
        x11.XUnmapWindow(display, frame.frameWindow());

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
            long next = successor(transientOwners.getOrDefault(clientWin, 0L), clientWin);
            if (next != 0) focusClient(next);
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

    /**
     * Forgets a client window and destroys its frame.
     *
     * @param withdrawn true if the client only unmapped its window: it is reparented to the root first,
     *                  since destroying the frame would otherwise destroy the client's window with it
     */
    private void cleanupWindow(long win, boolean withdrawn) {
        if (!withdrawn) withdrawnFrames.remove(win);
        boolean isClient = clientToFrame.containsKey(win);
        boolean isDock = dockWindows.contains(win);
        boolean isTileClient = clientToTile.containsKey(win);
        if (!isClient && !isDock && !isTileClient) {
            return;
        }

        dockWindows.remove(win);
        clientToDesktop.remove(win);
        pings.forget(win);
        recentWindows.remove(win);
        if (previews != null) previews.forget(win);
        if (switcher != null && switcher.entries().stream().anyMatch(e -> e.clientWin() == win)) {
            cancelSwitcher();
        }
        removeAndReflowIconTile(win);
        FrameWindow frame = clientToFrame.remove(win);
        if (frame != null) {
            frameToClient.remove(frame.frameWindow().longValue());
            if (withdrawn) {
                NativeLong client = new NativeLong(win);
                int x = frame.frameX() + frame.clientX();
                int y = frame.frameY() + frame.clientY();
                x11.XReparentWindow(display, client, new NativeLong(rootWindow), x, y);
                withdrawnFrames.put(win, new int[]{frame.frameX(), frame.frameY(), x, y});
                x11.XRemoveFromSaveSet(display, client);
                // ICCCM 4.1.4: a withdrawn window has no WM_STATE (or WithdrawnState).
                x11.XDeleteProperty(display, client, wmState);
            }
            x11.XDestroyWindow(display, frame.frameWindow());
        }
        core.unmanage(win);
        Long owner = transientOwners.remove(win);
        if (activeWindow == win) {
            activeWindow = 0;
            long next = successor(owner == null ? 0 : owner, win);
            if (next != 0) focusClient(next);
        }
        updateClientList();
        LOGGER.info(() -> "Cleaned up window: " + win);
    }

    @Override
    public void close() {
        stop();
        synchronized (wakeLock) {
            if (wakeDisplay != null) {
                x11.XCloseDisplay(wakeDisplay);
                wakeDisplay = null;
            }
        }
        windowSettingsWatcher.close();
        desktopMenu.dispose();
        windowMenu.dispose();
        if (switcherPopup != null) switcherPopup.dispose();
        if (bellPlayer != null) {
            bellPlayer.close();
        }
        if (display != null) {
            if (xkbEventBase >= 0) {
                // Give the bell back to the server, e.g. for a window manager started after this one
                xkb.XkbChangeEnabledControls(display, LibXkb.XkbUseCoreKbd, LibXkb.XkbAudibleBellMask,
                        LibXkb.XkbAudibleBellMask);
            }
            for (DesktopIconTile tile : new java.util.ArrayList<>(iconTiles)) {
                tile.destroy(x11, display);
            }
            iconTiles.clear();
            tileWinToTile.clear();
            clientToTile.clear();

            busyTimer.cancel();
            x11.XDeleteProperty(display, new NativeLong(rootWindow), launchBusyAtom);
            if (cursorBusy != null) x11.XFreeCursor(display, cursorBusy);
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
