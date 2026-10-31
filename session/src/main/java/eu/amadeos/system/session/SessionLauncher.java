package eu.amadeos.system.session;

import java.util.Arrays;

import eu.amadeos.system.clipboard.ClipboardDaemon;
import eu.amadeos.system.lock.ScreenLock;
import eu.amadeos.system.notify.NotificationDaemon;
import eu.amadeos.system.polkit.PolkitAgent;
import eu.amadeos.system.screen.ScreenSaverDaemon;
import eu.amadeos.system.startup.AutostartLauncher;

/**
 * Entry point of the amadeos-session jar: {@code amadeos-session lock} is the screen lock (started by
 * {@code /usr/bin/amadeos-lock}), {@code amadeos-session screen} the screen saver and idle lock (started
 * by {@code /etc/amadeos/autostart.d/50-amadeos-screen}), {@code amadeos-session polkit-agent} the polkit
 * authentication agent (started by {@code /etc/amadeos/autostart.d/30-amadeos-polkit-agent}),
 * {@code amadeos-session clipboard} the clipboard manager (started by
 * {@code /etc/amadeos/autostart.d/40-amadeos-clipboard}, its history shown by {@code /usr/bin/amadeos-clipboard}),
 * {@code amadeos-session notifications} the notification daemon (started by
 * {@code /etc/amadeos/autostart.d/45-amadeos-notifications}, its history shown by {@code /usr/bin/amadeos-notifications}),
 * {@code amadeos-session autostart} the user's programs of {@code ~/.config/autostart} (started by the session).
 * The session itself is the script {@code /usr/bin/amadeos-session}.
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
            case "polkit-agent" -> PolkitAgent.main(rest);
            case "clipboard" -> ClipboardDaemon.main(rest);
            case "notifications" -> NotificationDaemon.main(rest);
            case "autostart" -> AutostartLauncher.main(rest);
            default -> {
                System.err.println("Usage: amadeos-session <lock|screen|polkit-agent|clipboard|notifications|autostart>");
                System.exit(2);
            }
        }
    }
}
