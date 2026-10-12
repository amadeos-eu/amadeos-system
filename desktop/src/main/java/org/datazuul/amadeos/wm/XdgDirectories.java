package org.datazuul.amadeos.wm;

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
     * {@code $XDG_CONFIG_HOME} if it is set to an absolute path, otherwise {@code ~/.config}
     * (the specification says relative values must be ignored).
     */
    static Path configHome(Map<String, String> environment, String userHome) {
        String configured = environment.get("XDG_CONFIG_HOME");
        if (configured != null && configured.startsWith("/")) {
            return Path.of(configured);
        }
        return Path.of(userHome, ".config");
    }
}
