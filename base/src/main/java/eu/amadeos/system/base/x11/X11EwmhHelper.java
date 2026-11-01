package eu.amadeos.system.base.x11;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.ArrayList;
import java.util.List;

public class X11EwmhHelper implements AutoCloseable {
    private final LibX11 x11;
    private final Pointer display;
    private final int screen;
    private final long rootWindow;
    private final boolean ownsDisplay;

    private final NativeLong netClientList;
    private final NativeLong netActiveWindow;
    private final NativeLong netCloseWindow;
    private final NativeLong netWmName;
    private final NativeLong netWmWindowType;
    private final NativeLong netWmWindowTypeDock;
    private final NativeLong netWmState;
    private final NativeLong netWmStateHidden;
    private final NativeLong netWmStrut;
    private final NativeLong netWmStrutPartial;
    private final NativeLong wmChangeState;
    private final NativeLong wmState;
    private final NativeLong utf8String;
    private final NativeLong amadeosThemeAtom;
    private final NativeLong launchBusyAtom;
    private final NativeLong netNumberOfDesktops;
    private final NativeLong netCurrentDesktop;
    private final NativeLong netDesktopNames;
    private final NativeLong netWmDesktop;
    private final NativeLong xaWindowAtom;
    private final NativeLong xaAtomAtom;
    private final NativeLong xaCardinalAtom;

    public static X11EwmhHelper open(String displayName) {
        LibX11 x11 = LibX11.INSTANCE;
        Pointer display = x11.XOpenDisplay(displayName);
        if (display == null) {
            throw new IllegalStateException("Failed to open X display: "
                    + (displayName != null ? displayName : System.getenv("DISPLAY")));
        }
        int screen = x11.XDefaultScreen(display);
        long rootWindow = x11.XRootWindow(display, screen).longValue();
        return new X11EwmhHelper(x11, display, screen, rootWindow, true);
    }

    public X11EwmhHelper(LibX11 x11, Pointer display, int screen, long rootWindow, boolean ownsDisplay) {
        this.x11 = x11;
        this.display = display;
        this.screen = screen;
        this.rootWindow = rootWindow;
        this.ownsDisplay = ownsDisplay;

        this.netClientList = x11.XInternAtom(display, "_NET_CLIENT_LIST", 0);
        this.netActiveWindow = x11.XInternAtom(display, "_NET_ACTIVE_WINDOW", 0);
        this.netCloseWindow = x11.XInternAtom(display, "_NET_CLOSE_WINDOW", 0);
        this.netWmName = x11.XInternAtom(display, "_NET_WM_NAME", 0);
        this.netWmWindowType = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE", 0);
        this.netWmWindowTypeDock = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE_DOCK", 0);
        this.netWmState = x11.XInternAtom(display, "_NET_WM_STATE", 0);
        this.netWmStateHidden = x11.XInternAtom(display, "_NET_WM_STATE_HIDDEN", 0);
        this.netWmStrut = x11.XInternAtom(display, "_NET_WM_STRUT", 0);
        this.netWmStrutPartial = x11.XInternAtom(display, "_NET_WM_STRUT_PARTIAL", 0);
        this.wmChangeState = x11.XInternAtom(display, "WM_CHANGE_STATE", 0);
        this.wmState = x11.XInternAtom(display, "WM_STATE", 0);
        this.utf8String = x11.XInternAtom(display, "UTF8_STRING", 0);
        this.amadeosThemeAtom = x11.XInternAtom(display, "_AMADEOS_THEME", 0);
        this.launchBusyAtom = x11.XInternAtom(display,
                eu.amadeos.system.base.x11.LaunchFeedback.BUSY_PROPERTY, 0);
        this.netNumberOfDesktops = x11.XInternAtom(display, "_NET_NUMBER_OF_DESKTOPS", 0);
        this.netCurrentDesktop = x11.XInternAtom(display, "_NET_CURRENT_DESKTOP", 0);
        this.netDesktopNames = x11.XInternAtom(display, "_NET_DESKTOP_NAMES", 0);
        this.netWmDesktop = x11.XInternAtom(display, "_NET_WM_DESKTOP", 0);
        this.xaWindowAtom = new NativeLong(LibX11.XA_WINDOW);
        this.xaAtomAtom = new NativeLong(LibX11.XA_ATOM);
        this.xaCardinalAtom = new NativeLong(LibX11.XA_CARDINAL);
    }

    public Pointer display() {
        return display;
    }

    public long rootWindow() {
        return rootWindow;
    }

    /**
     * The width of the root window as the server has it now. Xlib's DisplayWidth keeps the size the connection
     * was opened with, so it would miss a change made with xrandr (Settings &gt; Display).
     */
    public int screenWidth() {
        LibX11.XWindowAttributes root = rootAttributes();
        return root != null ? root.width : x11.XDisplayWidth(display, screen);
    }

    /** The height of the root window as the server has it now; see {@link #screenWidth()}. */
    public int screenHeight() {
        LibX11.XWindowAttributes root = rootAttributes();
        return root != null ? root.height : x11.XDisplayHeight(display, screen);
    }

    private LibX11.XWindowAttributes rootAttributes() {
        LibX11.XWindowAttributes attrs = new LibX11.XWindowAttributes();
        if (x11.XGetWindowAttributes(display, new NativeLong(rootWindow), attrs) == 0) return null;
        return attrs.width > 0 && attrs.height > 0 ? attrs : null;
    }

    public void selectRootPropertyEvents() {
        x11.XSelectInput(display, new NativeLong(rootWindow), new NativeLong(LibX11.PropertyChangeMask));
        x11.XFlush(display);
    }

    public List<Long> getClientList() {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(rootWindow), netClientList,
                new NativeLong(0), new NativeLong(1024), 0,
                new NativeLong(LibX11.XA_WINDOW),
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        List<Long> result = new ArrayList<>();
        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null) {
            int count = (int) nitemsRet.getValue().longValue();
            Pointer ptr = propRet.getValue();
            try {
                for (int i = 0; i < count; i++) {
                    long win = ptr.getNativeLong((long) i * NativeLong.SIZE).longValue();
                    if (win != 0) {
                        result.add(win);
                    }
                }
            } finally {
                x11.XFree(ptr);
            }
        }
        return result;
    }

    public long getActiveWindow() {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(rootWindow), netActiveWindow,
                new NativeLong(0), new NativeLong(1), 0,
                new NativeLong(LibX11.XA_WINDOW),
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            Pointer ptr = propRet.getValue();
            try {
                return ptr.getNativeLong(0).longValue();
            } finally {
                x11.XFree(ptr);
            }
        }
        return 0;
    }

    public boolean isWindowMinimized(long win) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        // 1. Check _NET_WM_STATE for _NET_WM_STATE_HIDDEN
        int status = x11.XGetWindowProperty(
                display, new NativeLong(win), netWmState,
                new NativeLong(0), new NativeLong(32), 0,
                new NativeLong(LibX11.XA_ATOM),
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            int count = (int) nitemsRet.getValue().longValue();
            Pointer ptr = propRet.getValue();
            try {
                for (int i = 0; i < count; i++) {
                    long atom = ptr.getNativeLong((long) i * NativeLong.SIZE).longValue();
                    if (atom == netWmStateHidden.longValue()) {
                        return true;
                    }
                }
            } finally {
                x11.XFree(ptr);
            }
        }

        // 2. Fall back to ICCCM WM_STATE for IconicState
        status = x11.XGetWindowProperty(
                display, new NativeLong(win), wmState,
                new NativeLong(0), new NativeLong(2), 0,
                wmState,
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            Pointer ptr = propRet.getValue();
            try {
                long state = ptr.getNativeLong(0).longValue();
                return state == LibX11.IconicState;
            } finally {
                x11.XFree(ptr);
            }
        }
        return false;
    }

    public String getWindowTitle(long win) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        // 1. Try _NET_WM_NAME (UTF8_STRING)
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

        // 2. Fall back to XFetchName
        PointerByReference nameRet = new PointerByReference();
        if (x11.XFetchName(display, new NativeLong(win), nameRet) != 0 && nameRet.getValue() != null) {
            Pointer ptr = nameRet.getValue();
            try {
                String title = ptr.getString(0);
                if (title != null && !title.isBlank()) {
                    return title;
                }
            } finally {
                x11.XFree(ptr);
            }
        }
        return "Window #" + win;
    }

    public void activateWindow(long win) {
        LibX11.XClientMessageEvent cmsg = new LibX11.XClientMessageEvent();
        cmsg.type = LibX11.ClientMessage;
        cmsg.serial = new NativeLong(0);
        cmsg.send_event = 1;
        cmsg.display = display;
        cmsg.window = new NativeLong(win);
        cmsg.message_type = netActiveWindow;
        cmsg.format = 32;
        cmsg.data = new NativeLong[5];
        cmsg.data[0] = new NativeLong(1); // source application
        cmsg.data[1] = new NativeLong(LibX11.CurrentTime);
        cmsg.data[2] = new NativeLong(0);
        cmsg.data[3] = new NativeLong(0);
        cmsg.data[4] = new NativeLong(0);
        cmsg.write();

        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask;
        x11.XSendEvent(display, new NativeLong(rootWindow), 0, new NativeLong(mask), cmsg.getPointer());
        x11.XFlush(display);
    }

    public void minimizeWindow(long win) {
        LibX11.XClientMessageEvent cmsg = new LibX11.XClientMessageEvent();
        cmsg.type = LibX11.ClientMessage;
        cmsg.serial = new NativeLong(0);
        cmsg.send_event = 1;
        cmsg.display = display;
        cmsg.window = new NativeLong(win);
        cmsg.message_type = wmChangeState;
        cmsg.format = 32;
        cmsg.data = new NativeLong[5];
        cmsg.data[0] = new NativeLong(LibX11.IconicState);
        cmsg.data[1] = new NativeLong(0);
        cmsg.data[2] = new NativeLong(0);
        cmsg.data[3] = new NativeLong(0);
        cmsg.data[4] = new NativeLong(0);
        cmsg.write();

        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask;
        x11.XSendEvent(display, new NativeLong(rootWindow), 0, new NativeLong(mask), cmsg.getPointer());
        x11.XFlush(display);
    }

    // --8<-- [start:dock]
    public void setDockProperties(long win, int screenW, int screenH, int dockH) {
        // 1. _NET_WM_WINDOW_TYPE = _NET_WM_WINDOW_TYPE_DOCK
        Memory typeMem = new Memory(NativeLong.SIZE);
        typeMem.setNativeLong(0, netWmWindowTypeDock);
        x11.XChangeProperty(display, new NativeLong(win), netWmWindowType, xaAtomAtom, 32, LibX11.PropModeReplace, typeMem, 1);

        // 2. _NET_WM_STRUT = [0, 0, 0, dockH]
        Memory strutMem = new Memory(NativeLong.SIZE * 4);
        strutMem.setNativeLong(0, new NativeLong(0)); // left
        strutMem.setNativeLong(NativeLong.SIZE, new NativeLong(0)); // right
        strutMem.setNativeLong(NativeLong.SIZE * 2, new NativeLong(0)); // top
        strutMem.setNativeLong(NativeLong.SIZE * 3, new NativeLong(dockH)); // bottom
        x11.XChangeProperty(display, new NativeLong(win), netWmStrut, xaCardinalAtom, 32, LibX11.PropModeReplace, strutMem, 4);

        // 3. _NET_WM_STRUT_PARTIAL = [0, 0, 0, dockH, 0, 0, 0, 0, 0, 0, 0, screenW]
        Memory partialMem = new Memory(NativeLong.SIZE * 12);
        for (int i = 0; i < 12; i++) {
            partialMem.setNativeLong((long) i * NativeLong.SIZE, new NativeLong(0));
        }
        partialMem.setNativeLong(NativeLong.SIZE * 3, new NativeLong(dockH)); // bottom
        partialMem.setNativeLong(NativeLong.SIZE * 10, new NativeLong(0)); // bottom_start_x
        partialMem.setNativeLong(NativeLong.SIZE * 11, new NativeLong(screenW)); // bottom_end_x
        x11.XChangeProperty(display, new NativeLong(win), netWmStrutPartial, xaCardinalAtom, 32, LibX11.PropModeReplace, partialMem, 12);

        x11.XFlush(display);
    }
    // --8<-- [end:dock]

    /**
     * Marks the window as a dock along the top edge of the screen (Horizon's top bar): the dock type and a top
     * strut of {@code barH} pixels, so that the window manager keeps windows below it.
     */
    public void setTopDockProperties(long win, int screenW, int barH) {
        Memory typeMem = new Memory(NativeLong.SIZE);
        typeMem.setNativeLong(0, netWmWindowTypeDock);
        x11.XChangeProperty(display, new NativeLong(win), netWmWindowType, xaAtomAtom, 32, LibX11.PropModeReplace, typeMem, 1);

        Memory strutMem = new Memory(NativeLong.SIZE * 4);
        for (int i = 0; i < 4; i++) strutMem.setNativeLong((long) i * NativeLong.SIZE, new NativeLong(0));
        strutMem.setNativeLong(NativeLong.SIZE * 2, new NativeLong(barH)); // top
        x11.XChangeProperty(display, new NativeLong(win), netWmStrut, xaCardinalAtom, 32, LibX11.PropModeReplace, strutMem, 4);

        // _NET_WM_STRUT_PARTIAL: left, right, top, bottom, then start and end of each; top_start_x 0, top_end_x screenW
        Memory partialMem = new Memory(NativeLong.SIZE * 12);
        for (int i = 0; i < 12; i++) partialMem.setNativeLong((long) i * NativeLong.SIZE, new NativeLong(0));
        partialMem.setNativeLong(NativeLong.SIZE * 2, new NativeLong(barH));
        partialMem.setNativeLong(NativeLong.SIZE * 8, new NativeLong(0));
        partialMem.setNativeLong(NativeLong.SIZE * 9, new NativeLong(screenW));
        x11.XChangeProperty(display, new NativeLong(win), netWmStrutPartial, xaCardinalAtom, 32, LibX11.PropModeReplace, partialMem, 12);
        x11.XFlush(display);
    }

    /** Asks the window manager to close the window, as its close button would (_NET_CLOSE_WINDOW). */
    public void closeWindow(long win) {
        LibX11.XClientMessageEvent cmsg = new LibX11.XClientMessageEvent();
        cmsg.type = LibX11.ClientMessage;
        cmsg.serial = new NativeLong(0);
        cmsg.send_event = 1;
        cmsg.display = display;
        cmsg.window = new NativeLong(win);
        cmsg.message_type = x11.XInternAtom(display, "_NET_CLOSE_WINDOW", 0);
        cmsg.format = 32;
        cmsg.data = new NativeLong[5];
        cmsg.data[0] = new NativeLong(0);
        cmsg.data[1] = new NativeLong(2); // source: pager
        cmsg.data[2] = new NativeLong(0);
        cmsg.data[3] = new NativeLong(0);
        cmsg.data[4] = new NativeLong(0);
        cmsg.write();
        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask;
        x11.XSendEvent(display, new NativeLong(rootWindow), 0, new NativeLong(mask), cmsg.getPointer());
        x11.XFlush(display);
    }

    public NativeLong amadeosThemeAtom() {
        return amadeosThemeAtom;
    }

    public void setDesktopTheme(String themeId) {
        if (themeId == null) return;
        byte[] bytes = themeId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Memory mem = new Memory(bytes.length + 1);
        mem.write(0, bytes, 0, bytes.length);
        mem.setByte(bytes.length, (byte) 0);
        x11.XChangeProperty(display, new NativeLong(rootWindow), amadeosThemeAtom, utf8String, 8, LibX11.PropModeReplace, mem, bytes.length);
        x11.XFlush(display);
    }

    public String getDesktopTheme() {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(rootWindow), amadeosThemeAtom,
                new NativeLong(0), new NativeLong(64), 0,
                new NativeLong(0) /* AnyPropertyType */,
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            Pointer ptr = propRet.getValue();
            try {
                int len = (int) nitemsRet.getValue().longValue();
                byte[] bytes = ptr.getByteArray(0, len);
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            } finally {
                x11.XFree(ptr);
            }
        }
        return null;
    }

    /** Publishes the workspace names in the root window's {@code _NET_DESKTOP_NAMES}, each ended by a NUL. */
    public void setDesktopNames(List<String> names) {
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            joined.append(name).append('\0');
        }
        byte[] bytes = joined.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Memory mem = new Memory(Math.max(1, bytes.length));
        mem.write(0, bytes, 0, bytes.length);
        x11.XChangeProperty(display, new NativeLong(rootWindow), netDesktopNames, utf8String, 8,
                LibX11.PropModeReplace, mem, bytes.length);
        x11.XFlush(display);
    }

    /** The workspace names from {@code _NET_DESKTOP_NAMES}; empty if no one has published them. */
    public List<String> getDesktopNames() {
        return readDesktopNames(x11, display, rootWindow, netDesktopNames);
    }

    /** Reads {@code _NET_DESKTOP_NAMES} of {@code root}: NUL-separated UTF-8 names. */
    public static List<String> readDesktopNames(LibX11 x11, Pointer display, long root, NativeLong netDesktopNames) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(root), netDesktopNames,
                new NativeLong(0), new NativeLong(1024), 0,
                new NativeLong(0) /* AnyPropertyType */,
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        List<String> names = new ArrayList<>();
        if (status == 0 && propRet.getValue() != null && nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0) {
            Pointer ptr = propRet.getValue();
            try {
                byte[] bytes = ptr.getByteArray(0, (int) nitemsRet.getValue().longValue());
                for (String name : new String(bytes, java.nio.charset.StandardCharsets.UTF_8).split("\0")) {
                    names.add(name);
                }
            } finally {
                x11.XFree(ptr);
            }
        }
        return names;
    }

    public NativeLong netCurrentDesktop() {
        return netCurrentDesktop;
    }

    public NativeLong netNumberOfDesktops() {
        return netNumberOfDesktops;
    }

    public NativeLong netWmDesktop() {
        return netWmDesktop;
    }

    /** Whether the window manager shows the busy cursor for an application that is starting. */
    public boolean isLaunchBusy() {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(rootWindow), launchBusyAtom,
                new NativeLong(0), new NativeLong(1), 0,
                xaCardinalAtom,
                actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);

        if (status == 0 && propRet.getValue() != null) {
            Pointer ptr = propRet.getValue();
            try {
                return nitemsRet.getValue() != null && nitemsRet.getValue().longValue() > 0
                        && ptr.getNativeLong(0).longValue() != 0;
            } finally {
                x11.XFree(ptr);
            }
        }
        return false;
    }

    public int getCurrentDesktop() {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();

        int status = x11.XGetWindowProperty(
                display, new NativeLong(rootWindow), netCurrentDesktop,
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
        return 0;
    }

    public void setCurrentDesktop(int desktopIndex) {
        LibX11.XClientMessageEvent cmsg = new LibX11.XClientMessageEvent();
        cmsg.type = LibX11.ClientMessage;
        cmsg.serial = new NativeLong(0);
        cmsg.send_event = 1;
        cmsg.display = display;
        cmsg.window = new NativeLong(rootWindow);
        cmsg.message_type = netCurrentDesktop;
        cmsg.format = 32;
        cmsg.data = new NativeLong[5];
        cmsg.data[0] = new NativeLong(desktopIndex);
        cmsg.data[1] = new NativeLong(LibX11.CurrentTime);
        cmsg.data[2] = new NativeLong(0);
        cmsg.data[3] = new NativeLong(0);
        cmsg.data[4] = new NativeLong(0);
        cmsg.write();

        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask;
        x11.XSendEvent(display, new NativeLong(rootWindow), 0, new NativeLong(mask), cmsg.getPointer());
        x11.XFlush(display);
    }

    public int getWindowDesktop(long win) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
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

    public void setWindowDesktop(long win, int desktopIndex) {
        LibX11.XClientMessageEvent cmsg = new LibX11.XClientMessageEvent();
        cmsg.type = LibX11.ClientMessage;
        cmsg.serial = new NativeLong(0);
        cmsg.send_event = 1;
        cmsg.display = display;
        cmsg.window = new NativeLong(win);
        cmsg.message_type = netWmDesktop;
        cmsg.format = 32;
        cmsg.data = new NativeLong[5];
        cmsg.data[0] = new NativeLong(desktopIndex);
        cmsg.data[1] = new NativeLong(1); // source application
        cmsg.data[2] = new NativeLong(0);
        cmsg.data[3] = new NativeLong(0);
        cmsg.data[4] = new NativeLong(0);
        cmsg.write();

        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask;
        x11.XSendEvent(display, new NativeLong(rootWindow), 0, new NativeLong(mask), cmsg.getPointer());
        x11.XFlush(display);
    }

    // --8<-- [start:window-process]
    /** The process id the window's program gave in {@code _NET_WM_PID}, or 0 if it gave none. */
    public long getWindowPid(long win) {
        return cardinal(win, x11.XInternAtom(display, "_NET_WM_PID", 0), 0);
    }

    /**
     * Whether the window manager marked the window as not responding: it did not answer
     * {@code _NET_WM_PING} within five seconds ({@code _AMADEOS_NOT_RESPONDING}).
     */
    public boolean isNotResponding(long win) {
        return cardinal(win, x11.XInternAtom(display, "_AMADEOS_NOT_RESPONDING", 0), 0) != 0;
    }

    /** The class of the window's program ({@code WM_CLASS}, second part), or "" if it has none. */
    public String getWindowClass(long win) {
        PointerByReference typeRet = new PointerByReference();
        IntByReference formatRet = new IntByReference();
        NativeLongByReference itemsRet = new NativeLongByReference();
        NativeLongByReference afterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();
        NativeLong wmClass = x11.XInternAtom(display, "WM_CLASS", 0);
        int status = x11.XGetWindowProperty(display, new NativeLong(win), wmClass, new NativeLong(0),
                new NativeLong(256), 0, new NativeLong(0), typeRet, formatRet, itemsRet, afterRet, propRet);
        Pointer ptr = propRet.getValue();
        if (status != 0 || ptr == null) return "";
        try {
            long n = itemsRet.getValue() == null ? 0 : itemsRet.getValue().longValue();
            byte[] bytes = ptr.getByteArray(0, (int) n);
            String[] parts = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).split("\0");
            return parts.length > 1 ? parts[1] : parts.length == 1 ? parts[0] : "";
        } finally {
            x11.XFree(ptr);
        }
    }

    /**
     * Whether the window is a dialog: it names a main window ({@code WM_TRANSIENT_FOR}) or has the type
     * {@code _NET_WM_WINDOW_TYPE_DIALOG}.
     */
    public boolean isDialog(long win) {
        com.sun.jna.Memory owner = new com.sun.jna.Memory(NativeLong.SIZE);
        owner.setNativeLong(0, new NativeLong(0));
        if (x11.XGetTransientForHint(display, new NativeLong(win), owner) != 0
                && owner.getNativeLong(0).longValue() != 0) {
            return true;
        }
        PointerByReference typeRet = new PointerByReference();
        IntByReference formatRet = new IntByReference();
        NativeLongByReference itemsRet = new NativeLongByReference();
        NativeLongByReference afterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();
        int status = x11.XGetWindowProperty(display, new NativeLong(win), netWmWindowType, new NativeLong(0),
                new NativeLong(16), 0, xaAtomAtom, typeRet, formatRet, itemsRet, afterRet, propRet);
        Pointer ptr = propRet.getValue();
        if (status != 0 || ptr == null) return false;
        try {
            long dialog = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE_DIALOG", 0).longValue();
            long n = itemsRet.getValue() == null ? 0 : itemsRet.getValue().longValue();
            for (int i = 0; i < n; i++) {
                if (ptr.getNativeLong((long) i * NativeLong.SIZE).longValue() == dialog) return true;
            }
            return false;
        } finally {
            x11.XFree(ptr);
        }
    }

    private long cardinal(long win, NativeLong property, long fallback) {
        if (win == 0) return fallback;
        PointerByReference typeRet = new PointerByReference();
        IntByReference formatRet = new IntByReference();
        NativeLongByReference itemsRet = new NativeLongByReference();
        NativeLongByReference afterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();
        int status = x11.XGetWindowProperty(display, new NativeLong(win), property, new NativeLong(0),
                new NativeLong(1), 0, xaCardinalAtom, typeRet, formatRet, itemsRet, afterRet, propRet);
        Pointer ptr = propRet.getValue();
        if (status != 0 || ptr == null) return fallback;
        try {
            if (itemsRet.getValue() == null || itemsRet.getValue().longValue() < 1) return fallback;
            return ptr.getNativeLong(0).longValue() & 0xFFFFFFFFL;
        } finally {
            x11.XFree(ptr);
        }
    }
    // --8<-- [end:window-process]

    // --8<-- [start:fullscreen]
    /** Whether the window's _NET_WM_STATE has _NET_WM_STATE_FULLSCREEN (a game, a video, a presentation). */
    public boolean isFullscreen(long win) {
        if (win == 0) return false;
        NativeLong fullscreen = x11.XInternAtom(display, "_NET_WM_STATE_FULLSCREEN", 0);
        for (long atom : longs(win, netWmState, xaAtomAtom, 32)) {
            if (atom == fullscreen.longValue()) return true;
        }
        return false;
    }

    /** Whether the active window is full screen. */
    public boolean isActiveWindowFullscreen() {
        return isFullscreen(getActiveWindow());
    }
    // --8<-- [end:fullscreen]

    /**
     * The space the docks reserve at the edges of the screen (the Front Panel at the bottom), the largest
     * _NET_WM_STRUT_PARTIAL or _NET_WM_STRUT of the top-level windows: left, right, top, bottom.
     */
    public int[] reservedEdges() {
        int[] edges = new int[4];
        java.util.Set<Long> windows = new java.util.LinkedHashSet<>(getClientList());
        PointerByReference rootRet = new PointerByReference();
        PointerByReference parentRet = new PointerByReference();
        PointerByReference childrenRet = new PointerByReference();
        IntByReference countRet = new IntByReference();
        if (x11.XQueryTree(display, new NativeLong(rootWindow), rootRet, parentRet, childrenRet, countRet) != 0
                && childrenRet.getValue() != null) {
            Pointer children = childrenRet.getValue();
            try {
                for (int i = 0; i < countRet.getValue(); i++) {
                    windows.add(children.getNativeLong((long) i * NativeLong.SIZE).longValue());
                }
            } finally {
                x11.XFree(children);
            }
        }
        for (long win : windows) {
            long[] strut = longs(win, netWmStrutPartial, xaCardinalAtom, 12);
            if (strut.length < 4) strut = longs(win, netWmStrut, xaCardinalAtom, 4);
            for (int i = 0; i < 4 && i < strut.length; i++) {
                edges[i] = (int) Math.max(edges[i], Math.min(strut[i], 4096));
            }
        }
        return edges;
    }

    /** A 32-bit list property of a window (atoms, cardinals), or an empty array. */
    private long[] longs(long win, NativeLong property, NativeLong type, int max) {
        PointerByReference actualTypeRet = new PointerByReference();
        IntByReference actualFormatRet = new IntByReference();
        NativeLongByReference nitemsRet = new NativeLongByReference();
        NativeLongByReference bytesAfterRet = new NativeLongByReference();
        PointerByReference propRet = new PointerByReference();
        int status = x11.XGetWindowProperty(display, new NativeLong(win), property, new NativeLong(0),
                new NativeLong(max), 0, type, actualTypeRet, actualFormatRet, nitemsRet, bytesAfterRet, propRet);
        if (status != 0 || propRet.getValue() == null) return new long[0];
        Pointer ptr = propRet.getValue();
        try {
            if (actualFormatRet.getValue() != 32 || nitemsRet.getValue() == null) return new long[0];
            int count = (int) Math.min(max, nitemsRet.getValue().longValue());
            long[] out = new long[count];
            for (int i = 0; i < count; i++) out[i] = ptr.getNativeLong((long) i * NativeLong.SIZE).longValue();
            return out;
        } finally {
            x11.XFree(ptr);
        }
    }

    @Override
    public void close() {
        if (ownsDisplay && display != null) {
            x11.XCloseDisplay(display);
        }
    }
}

