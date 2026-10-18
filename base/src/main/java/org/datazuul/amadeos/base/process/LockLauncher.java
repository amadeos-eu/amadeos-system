package org.datazuul.amadeos.base.process;

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
    static final String WRAPPER = "/usr/bin/amadeos-lock";

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
     * The installed wrapper (package amadeos-session) restarts the lock if it crashes. Without it
     * (development in Xephyr) the lock runs directly from the current class path, which must then hold
     * the amadeos-session jar too (see wm/run-xephyr.sh).
     */
    static List<String> command() {
        if (new File(WRAPPER).canExecute()) {
            return List.of(WRAPPER);
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                "org.datazuul.amadeos.lock.ScreenLock");
    }
}
