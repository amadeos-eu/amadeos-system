package org.datazuul.amadeos.session;

import java.util.Arrays;

import org.datazuul.amadeos.lock.ScreenLock;
import org.datazuul.amadeos.screen.ScreenSaverDaemon;

/**
 * Entry point of the amade-session jar: {@code amade-session lock} is the screen lock (started by
 * {@code /usr/bin/amadeos-lock}), {@code amade-session screen} the screen saver and idle lock (started
 * by {@code /etc/amadeos/autostart.d/50-amadeos-screen}). The session itself is the script
 * {@code /usr/bin/amadeos-session}.
 */
public final class SessionLauncher {
    private SessionLauncher() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";
        String[] rest = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : args;
        switch (mode) {
            case "lock" -> ScreenLock.main(rest);
            case "screen" -> ScreenSaverDaemon.main(rest);
            default -> {
                System.err.println("Usage: amade-session <lock|screen>");
                System.exit(2);
            }
        }
    }
}
