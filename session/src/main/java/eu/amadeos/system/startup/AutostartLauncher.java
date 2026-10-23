package eu.amadeos.system.startup;

import eu.amadeos.system.base.startup.UserAutostart;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;

import java.io.IOException;
import java.util.List;

/**
 * {@code amadeos-session autostart}: starts the user's programs of {@code ~/.config/autostart} (Settings &gt;
 * Startup) once at login, after the desktop's own services. Programs that are no longer installed are skipped.
 */
public final class AutostartLauncher {
    private AutostartLauncher() {
    }

    public static void main(String[] args) {
        int failed = start(UserAutostart.user().entries());
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Starts the entries; returns how many could not be started. */
    static int start(List<XdgDesktopEntry> entries) {
        int failed = 0;
        for (XdgDesktopEntry entry : entries) {
            if (!entry.isInstalled()) {
                System.err.println("[amadeos-autostart] not installed, skipped: " + entry.id());
                continue;
            }
            try {
                entry.launch();
                System.err.println("[amadeos-autostart] started " + entry.id());
            } catch (IOException e) {
                failed++;
                System.err.println("[amadeos-autostart] cannot start " + entry.id() + ": " + e.getMessage());
            }
        }
        return failed;
    }
}
