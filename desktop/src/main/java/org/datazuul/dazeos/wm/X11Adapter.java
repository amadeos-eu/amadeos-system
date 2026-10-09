package org.datazuul.dazeos.wm;

public interface X11Adapter {
    void configureWindow(long windowId, WindowGeometry geometry);

    void mapWindow(long windowId);

    void unmapWindow(long windowId);

    void focusWindow(long windowId);
}

