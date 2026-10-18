package eu.amadeos.base.xdg;

import java.nio.file.Path;
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
}
