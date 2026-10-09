package org.datazuul.dazeos.wm;

import java.util.Map;

public final class WindowManagerCore {
    private final WindowRegistry registry;
    private final X11Adapter x11;
    private final FloatingLayoutPolicy layoutPolicy;

    public WindowManagerCore(WindowRegistry registry, X11Adapter x11, FloatingLayoutPolicy layoutPolicy) {
        this.registry = registry;
        this.x11 = x11;
        this.layoutPolicy = layoutPolicy;
    }

    public void manage(long windowId, WindowGeometry initialGeometry) {
        registry.add(windowId, initialGeometry);
        x11.mapWindow(windowId);
    }

    public void unmanage(long windowId) {
        if (registry.remove(windowId)) {
            x11.unmapWindow(windowId);
        }
    }

    public void arrange(int screenWidth, int screenHeight) {
        Map<Long, WindowGeometry> placements = layoutPolicy.arrange(registry.snapshot(), screenWidth, screenHeight);
        placements.forEach((id, geometry) -> {
            registry.updateGeometry(id, geometry);
            x11.configureWindow(id, geometry);
        });
    }

    public void focus(long windowId) {
        if (registry.find(windowId).isEmpty()) {
            throw new IllegalArgumentException("Window is not managed: " + windowId);
        }
        x11.focusWindow(windowId);
    }

    public WindowRegistry registry() {
        return registry;
    }
}

