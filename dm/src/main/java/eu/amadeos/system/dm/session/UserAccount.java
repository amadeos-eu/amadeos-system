package eu.amadeos.system.dm.session;

import java.util.Objects;
import java.util.Optional;
import eu.amadeos.system.base.x11.LibC;

/**
 * The passwd entry of the user a session runs as.
 */
public record UserAccount(String name, int uid, int gid, String home, String shell) {
    public UserAccount {
        Objects.requireNonNull(name);
        home = home == null || home.isBlank() ? "/" : home;
        shell = shell == null || shell.isBlank() ? "/bin/sh" : shell;
    }

    public static Optional<UserAccount> lookup(String name) {
        LibC.Passwd pwd = LibC.INSTANCE.getpwnam(name);
        if (pwd == null) {
            return Optional.empty();
        }
        return Optional.of(new UserAccount(name, pwd.pw_uid, pwd.pw_gid, pwd.pw_dir, pwd.pw_shell));
    }
}
