package org.datazuul.amadeos.wm;

import java.util.logging.Logger;

/** Safe placeholder for the native X11 boundary; it performs no native calls. */
public final class LoggingX11Adapter implements X11Adapter {
    private static final Logger LOGGER = Logger.getLogger(LoggingX11Adapter.class.getName());

    @Override
    public void configureWindow(long windowId, WindowGeometry geometry) {
        LOGGER.info(() -> "configure window " + windowId + " to " + geometry);
    }

    @Override
    public void mapWindow(long windowId) {
        LOGGER.info(() -> "map window " + windowId);
    }

    @Override
    public void unmapWindow(long windowId) {
        LOGGER.info(() -> "unmap window " + windowId);
    }

    @Override
    public void focusWindow(long windowId) {
        LOGGER.info(() -> "focus window " + windowId);
    }

    @Override
    public void raiseWindow(long windowId) {
        LOGGER.info(() -> "raise window " + windowId);
    }

    @Override
    public void lowerWindow(long windowId) {
        LOGGER.info(() -> "lower window " + windowId);
    }

    @Override
    public void closeWindow(long windowId) {
        LOGGER.info(() -> "close window " + windowId);
    }
}

