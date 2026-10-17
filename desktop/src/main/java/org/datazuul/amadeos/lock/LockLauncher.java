package org.datazuul.amadeos.lock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts the screen lock as its own process. Used by the window manager (Super+L), the root
 * menu and the panel's lock button, so a crash of any of them cannot unlock the screen.
 */
public final class LockLauncher {
    private static final Logger LOGGER = Logger.getLogger(LockLauncher.class.getName());
    static final String WRAPPER = "/usr/local/bin/amadeos-lock";

    private LockLauncher() {
    }

    public static void lockScreen() {
        try {
            new ProcessBuilder(command())
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start the screen lock", e);
        }
    }

    /**
     * The installed wrapper restarts the lock if it crashes. Without it (development in
     * Xephyr) the lock runs directly from the current class path.
     */
    static List<String> command() {
        if (new File(WRAPPER).canExecute()) {
            return List.of(WRAPPER);
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                "org.datazuul.amadeos.wm.AmadeOsLauncher", "lock");
    }
}
