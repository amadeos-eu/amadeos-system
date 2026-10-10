package org.datazuul.dazeos.wm.panel;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.ArrayList;
import java.util.List;
import org.datazuul.dazeos.wm.x11.LibX11;

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

    public int screenWidth() {
        return x11.XDisplayWidth(display, screen);
    }

    public int screenHeight() {
        return x11.XDisplayHeight(display, screen);
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

    @Override
    public void close() {
        if (ownsDisplay && display != null) {
            x11.XCloseDisplay(display);
        }
    }
}

