package org.datazuul.dazeos.wm;

import java.util.logging.Logger;
import org.datazuul.dazeos.wm.x11.X11WindowManagerService;

public final class DazeWindowManagerMain {
    private static final Logger LOGGER = Logger.getLogger(DazeWindowManagerMain.class.getName());

    public static void main(String[] args) {
        String display = args.length > 0 ? args[0] : null;
        LOGGER.info(() -> "Starting DazeOS Window Manager on display: " + (display != null ? display : System.getenv("DISPLAY")));

        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Runtime.getRuntime().addShutdownHook(new Thread(wm::stop, "wm-shutdown-hook"));
            wm.startEventLoop();
        } catch (Exception e) {
            LOGGER.severe("Fatal error in DazeOS Window Manager: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

