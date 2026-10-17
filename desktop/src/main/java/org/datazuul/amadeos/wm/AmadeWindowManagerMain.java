package org.datazuul.amadeos.wm;

import java.util.logging.Logger;
import org.datazuul.amadeos.wm.x11.X11WindowManagerService;

public final class AmadeWindowManagerMain {
    private static final Logger LOGGER = Logger.getLogger(AmadeWindowManagerMain.class.getName());

    public static void main(String[] args) {
        String display = args.length > 0 ? args[0] : null;
        LOGGER.info(() -> "Starting amadeOS Window Manager on display: " + (display != null ? display : System.getenv("DISPLAY")));

        try (X11WindowManagerService wm = X11WindowManagerService.open(display)) {
            Runtime.getRuntime().addShutdownHook(new Thread(wm::stop, "wm-shutdown-hook"));
            wm.startEventLoop();
        } catch (Exception e) {
            LOGGER.severe("Fatal error in amadeOS Window Manager: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

