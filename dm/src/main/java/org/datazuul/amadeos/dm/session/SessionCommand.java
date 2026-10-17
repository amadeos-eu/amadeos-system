package org.datazuul.amadeos.dm.session;

import java.util.ArrayList;
import java.util.List;

/**
 * The command line that starts a session, dropping root privileges with setpriv (util-linux).
 */
public final class SessionCommand {
    public static final String DEFAULT_SESSION_EXECUTABLE = "/usr/bin/amadeos-session";

    private SessionCommand() {
    }

    /**
     * @param asRoot whether the worker runs as root and must switch to the user; otherwise the
     *               session runs as the current user (development without root)
     */
    public static List<String> build(UserAccount account, SessionType type, String sessionExecutable,
                                     String display, boolean asRoot) {
        List<String> command = new ArrayList<>();
        if (asRoot) {
            command.addAll(dropPrivileges(account));
        }
        if (type == SessionType.FAILSAFE) {
            command.addAll(List.of("xterm", "-title", "amadeOS Failsafe Session", "-geometry", "100x30+40+40"));
        } else {
            command.add(sessionExecutable);
            command.add(display);
        }
        return command;
    }

    /** setpriv prefix: real and effective uid/gid of the user plus their supplementary groups. */
    public static List<String> dropPrivileges(UserAccount account) {
        return List.of("setpriv",
                "--reuid=" + account.uid(),
                "--regid=" + account.gid(),
                "--init-groups",
                "--");
    }
}
