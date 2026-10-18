package eu.amadeos.system.wm;

public interface X11Adapter {
    void configureWindow(long windowId, WindowGeometry geometry);

    void mapWindow(long windowId);

    void unmapWindow(long windowId);

    void focusWindow(long windowId);
 
    void raiseWindow(long windowId);
 
    void lowerWindow(long windowId);

    void closeWindow(long windowId);
}

