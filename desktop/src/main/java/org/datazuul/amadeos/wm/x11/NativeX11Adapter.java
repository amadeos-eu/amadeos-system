package org.datazuul.amadeos.wm.x11;

import org.datazuul.amadeos.base.x11.LibX11;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.util.function.Function;
import org.datazuul.amadeos.wm.WindowGeometry;
import org.datazuul.amadeos.wm.X11Adapter;
import org.datazuul.amadeos.wm.decoration.FrameWindow;

public final class NativeX11Adapter implements X11Adapter {
    private final LibX11 x11;
    private final Pointer display;
    private final Function<Long, FrameWindow> frameLookup;
    private final Pointer gc;
    private final int screen;

    public NativeX11Adapter(LibX11 x11, Pointer display) {
        this(x11, display, null, null, 0);
    }

    public NativeX11Adapter(
        LibX11 x11,
        Pointer display,
        Function<Long, FrameWindow> frameLookup,
        Pointer gc,
        int screen
    ) {
        this.x11 = x11;
        this.display = display;
        this.frameLookup = frameLookup;
        this.gc = gc;
        this.screen = screen;
    }

    @Override
    public void configureWindow(long windowId, WindowGeometry geometry) {
        FrameWindow frame = (frameLookup != null) ? frameLookup.apply(windowId) : null;
        if (frame != null) {
            frame.setBounds(geometry.x(), geometry.y(), geometry.width(), geometry.height());
            x11.XMoveResizeWindow(display, frame.frameWindow(), geometry.x(), geometry.y(), geometry.width(), geometry.height());
            x11.XMoveResizeWindow(display, frame.clientWindow(), frame.clientX(), frame.clientY(), frame.clientWidth(), frame.clientHeight());
            frame.render();
            if (gc != null) {
                frame.blit(display, gc, screen);
            }
            sendSyntheticConfigureNotify(frame);
        } else {
            x11.XMoveResizeWindow(display, new NativeLong(windowId), geometry.x(), geometry.y(), geometry.width(), geometry.height());
        }
        x11.XFlush(display);
    }

    @Override
    public void mapWindow(long windowId) {
        FrameWindow frame = (frameLookup != null) ? frameLookup.apply(windowId) : null;
        if (frame != null) {
            x11.XMapWindow(display, frame.frameWindow());
            x11.XMapWindow(display, frame.clientWindow());
        } else {
            x11.XMapWindow(display, new NativeLong(windowId));
        }
        x11.XFlush(display);
    }

    @Override
    public void unmapWindow(long windowId) {
        FrameWindow frame = (frameLookup != null) ? frameLookup.apply(windowId) : null;
        if (frame != null) {
            x11.XUnmapWindow(display, frame.frameWindow());
            x11.XUnmapWindow(display, frame.clientWindow());
        } else {
            x11.XUnmapWindow(display, new NativeLong(windowId));
        }
        x11.XFlush(display);
    }

    @Override
    public void focusWindow(long windowId) {
        x11.XSetInputFocus(display, new NativeLong(windowId), LibX11.RevertToPointerRoot, new NativeLong(LibX11.CurrentTime));

        NativeLong wmProtocols = x11.XInternAtom(display, "WM_PROTOCOLS", 0);
        NativeLong wmTakeFocus = x11.XInternAtom(display, "WM_TAKE_FOCUS", 0);

        if (hasProtocol(windowId, wmTakeFocus)) {
            LibX11.XClientMessageEvent msg = new LibX11.XClientMessageEvent();
            msg.type = LibX11.ClientMessage;
            msg.window = new NativeLong(windowId);
            msg.message_type = wmProtocols;
            msg.format = 32;
            msg.data[0] = wmTakeFocus;
            msg.data[1] = new NativeLong(LibX11.CurrentTime);
            msg.write();

            x11.XSendEvent(display, new NativeLong(windowId), 0, new NativeLong(0), msg.getPointer());
        }
        x11.XFlush(display);
    }

    @Override
    public void raiseWindow(long windowId) {
        FrameWindow frame = (frameLookup != null) ? frameLookup.apply(windowId) : null;
        if (frame != null) {
            x11.XRaiseWindow(display, frame.frameWindow());
            x11.XRaiseWindow(display, frame.clientWindow());
        } else {
            x11.XRaiseWindow(display, new NativeLong(windowId));
        }
        x11.XFlush(display);
    }

    @Override
    public void lowerWindow(long windowId) {
        FrameWindow frame = (frameLookup != null) ? frameLookup.apply(windowId) : null;
        if (frame != null) {
            x11.XLowerWindow(display, frame.frameWindow());
            x11.XLowerWindow(display, frame.clientWindow());
        } else {
            x11.XLowerWindow(display, new NativeLong(windowId));
        }
        x11.XFlush(display);
    }

    @Override
    public void closeWindow(long windowId) {
        NativeLong wmProtocols = x11.XInternAtom(display, "WM_PROTOCOLS", 0);
        NativeLong wmDeleteWindow = x11.XInternAtom(display, "WM_DELETE_WINDOW", 0);

        if (hasProtocol(windowId, wmDeleteWindow)) {
            LibX11.XClientMessageEvent msg = new LibX11.XClientMessageEvent();
            msg.type = LibX11.ClientMessage;
            msg.window = new NativeLong(windowId);
            msg.message_type = wmProtocols;
            msg.format = 32;
            msg.data[0] = wmDeleteWindow;
            msg.data[1] = new NativeLong(LibX11.CurrentTime);
            msg.write();

            x11.XSendEvent(display, new NativeLong(windowId), 0, new NativeLong(0), msg.getPointer());
        } else {
            x11.XKillClient(display, new NativeLong(windowId));
        }
        x11.XFlush(display);
    }

    public boolean hasProtocol(long windowId, NativeLong protocolAtom) {
        com.sun.jna.ptr.PointerByReference protocolsReturn = new com.sun.jna.ptr.PointerByReference();
        com.sun.jna.ptr.IntByReference countReturn = new com.sun.jna.ptr.IntByReference();
        if (x11.XGetWMProtocols(display, new NativeLong(windowId), protocolsReturn, countReturn) != 0) {
            int count = countReturn.getValue();
            Pointer ptr = protocolsReturn.getValue();
            if (ptr != null && count > 0) {
                try {
                    for (int i = 0; i < count; i++) {
                        NativeLong atom = ptr.getNativeLong((long) i * NativeLong.SIZE);
                        if (atom != null && atom.longValue() == protocolAtom.longValue()) {
                            return true;
                        }
                    }
                } finally {
                    x11.XFree(ptr);
                }
            }
        }
        return false;
    }

    private void sendSyntheticConfigureNotify(FrameWindow frame) {
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
    }
}
