package org.datazuul.amadeos.dm.session;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the environment of a user session from scratch, so nothing of the root display
 * manager's environment leaks into it.
 */
public final class SessionEnvironment {
    static final String DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin:/usr/local/games:/usr/games";

    private SessionEnvironment() {
    }

    /** Variables for pam_systemd, set in the PAM environment before the session is opened. */
    public static Map<String, String> logindVariables(String display, int vt) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("XDG_SEAT", "seat0");
        vars.put("XDG_VTNR", Integer.toString(vt));
        vars.put("XDG_SESSION_TYPE", "x11");
        vars.put("XDG_SESSION_CLASS", "user");
        vars.put("XDG_SESSION_DESKTOP", "amadeOS");
        vars.put("DISPLAY", display);
        return vars;
    }

    /**
     * @param pamEnvironment variables from pam_getenvlist (XDG_RUNTIME_DIR, XDG_SESSION_ID, locale from pam_env, ...)
     * @param xauthority     the user's X authority file, or null
     */
    public static Map<String, String> build(Map<String, String> pamEnvironment, UserAccount account,
                                            String display, String xauthority, SessionType type) {
        Map<String, String> env = new LinkedHashMap<>(pamEnvironment);
        env.put("HOME", account.home());
        env.put("USER", account.name());
        env.put("LOGNAME", account.name());
        env.put("SHELL", account.shell());
        env.putIfAbsent("PATH", DEFAULT_PATH);
        env.put("DISPLAY", display);
        if (xauthority != null) {
            env.put("XAUTHORITY", xauthority);
        }
        env.put("XDG_SESSION_TYPE", "x11");
        env.put("XDG_CURRENT_DESKTOP", "amadeOS");
        env.put("XDG_SESSION_DESKTOP", "amadeOS");
        env.put("AMADEOS_SESSION", type.id());
        return env;
    }
}
