package org.datazuul.dazeos.wm.x11;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import org.datazuul.dazeos.wm.WindowGeometry;
import org.datazuul.dazeos.wm.X11Adapter;

public final class NativeX11Adapter implements X11Adapter {
    private final LibX11 x11;
    private final Pointer display;

    public NativeX11Adapter(LibX11 x11, Pointer display) {
        this.x11 = x11;
        this.display = display;
    }

    @Override
    public void configureWindow(long windowId, WindowGeometry geometry) {
        x11.XMoveResizeWindow(display, new NativeLong(windowId), geometry.x(), geometry.y(), geometry.width(), geometry.height());
        x11.XFlush(display);
    }

    @Override
    public void mapWindow(long windowId) {
        x11.XMapWindow(display, new NativeLong(windowId));
        x11.XFlush(display);
    }

    @Override
    public void unmapWindow(long windowId) {
        x11.XUnmapWindow(display, new NativeLong(windowId));
        x11.XFlush(display);
    }

    @Override
    public void focusWindow(long windowId) {
        x11.XSetInputFocus(display, new NativeLong(windowId), LibX11.RevertToPointerRoot, new NativeLong(LibX11.CurrentTime));
        x11.XFlush(display);
    }
}

