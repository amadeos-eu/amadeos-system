package eu.amadeos.system.base.xdg;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-user configuration location following the XDG Base Directory specification: the session
 * script already reads $XDG_CONFIG_HOME/amadeos/session.conf, the Java desktop uses the same place.
 */
public final class XdgDirectories {
    private XdgDirectories() {
    }

    /** {@code $XDG_CONFIG_HOME/amadeos}, by default {@code ~/.config/amadeos}. */
    public static Path amadeosConfigDir() {
        return configHome(System.getenv(), System.getProperty("user.home", "/tmp")).resolve("amadeos");
    }

    /** {@code $XDG_CONFIG_HOME/autostart}, by default {@code ~/.config/autostart}: the user's programs started at login. */
    public static Path autostartDir() {
        return configHome(System.getenv(), System.getProperty("user.home", "/tmp")).resolve("autostart");
    }

    /**
     * {@code $XDG_CACHE_HOME/amadeos/logs}, by default {@code ~/.cache/amadeos/logs}: the session and
     * window manager logs written by {@code amadeos-session}, and one log per application started from
     * the desktop. The Tools drawer's log viewer lists this directory.
     */
    public static Path amadeosLogDir() {
        return cacheHome(System.getenv(), System.getProperty("user.home", "/tmp")).resolve("amadeos").resolve("logs");
    }

    /**
     * {@code $XDG_CONFIG_HOME} if it is set to an absolute path, otherwise {@code ~/.config}
     * (the specification says relative values must be ignored).
     */
    static Path configHome(Map<String, String> environment, String userHome) {
        return baseDir(environment, "XDG_CONFIG_HOME", userHome, ".config");
    }

    /** {@code $XDG_CACHE_HOME} if it is set to an absolute path, otherwise {@code ~/.cache}. */
    static Path cacheHome(Map<String, String> environment, String userHome) {
        return baseDir(environment, "XDG_CACHE_HOME", userHome, ".cache");
    }

    private static Path baseDir(Map<String, String> environment, String variable, String userHome, String fallback) {
        String configured = environment.get(variable);
        if (configured != null && configured.startsWith("/")) {
            return Path.of(configured);
        }
        return Path.of(userHome, fallback);
    }

    // --8<-- [start:datadirs]
    /**
     * The system data folders: {@code $XDG_DATA_DIRS} (by default {@code /usr/local/share} and
     * {@code /usr/share}), then the export folders of Flatpak and the desktop folder of snapd, if they are not
     * already listed. Their scripts in {@code /etc/profile.d} add them only to login shells, which the amadeOS
     * session does not start, so Firefox from a snap, for example, would be missing from the drawers.
     */
    public static List<Path> systemDataDirs() {
        return systemDataDirs(System.getenv(), System.getProperty("user.home", "/tmp"));
    }

    static List<Path> systemDataDirs(Map<String, String> environment, String userHome) {
        List<Path> dirs = new ArrayList<>();
        String configured = environment.get("XDG_DATA_DIRS");
        if (configured != null && !configured.isBlank()) {
            for (String d : configured.split(":")) {
                if (d.startsWith("/")) add(dirs, Path.of(d));
            }
        }
        if (dirs.isEmpty()) {
            dirs.add(Path.of("/usr/local/share"));
            dirs.add(Path.of("/usr/share"));
        }
        add(dirs, dataHome(environment, userHome).resolve("flatpak/exports/share"));
        add(dirs, Path.of("/var/lib/flatpak/exports/share"));
        add(dirs, Path.of("/var/lib/snapd/desktop"));
        return dirs;
    }

    /** {@code $XDG_DATA_HOME} if it is set to an absolute path, otherwise {@code ~/.local/share}. */
    static Path dataHome(Map<String, String> environment, String userHome) {
        return baseDir(environment, "XDG_DATA_HOME", userHome, ".local/share");
    }
    // --8<-- [end:datadirs]

    private static void add(List<Path> dirs, Path dir) {
        Path normalized = dir.normalize();
        for (Path d : dirs) {
            if (d.normalize().equals(normalized)) return;
        }
        dirs.add(normalized);
    }
}
