package eu.amadeos.system.users;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A person's login on the computer: an entry of {@code /etc/passwd} with a user ID from 1000 on, the groups it is
 * in, and what the Users page shows about it.
 *
 * @param fullName the first field of the GECOS entry, empty when none is set
 * @param groups   the supplementary groups from {@code /etc/group}, without the personal group
 * @param disabled true when the account cannot log in: amadeOS gives disabled accounts the shell
 *                 {@code /usr/sbin/nologin} (besides locking the password), which everyone may read
 * @param loggedIn true while the account has a session (logind)
 */
public record Account(String login, int uid, String fullName, String home, String shell, Set<String> groups,
                      boolean disabled, boolean loggedIn) {
    /** The group whose members administer the computer (sudo, polkit's auth_admin). */
    public static final String ADMIN_GROUP = "sudo";

    public Account {
        Objects.requireNonNull(login);
        fullName = fullName == null ? "" : fullName;
        groups = Set.copyOf(groups);
    }

    public boolean admin() {
        return groups.contains(ADMIN_GROUP);
    }

    /** The full name, or the login name when no full name is set. */
    public String displayName() {
        return fullName.isBlank() ? login : fullName;
    }

    /** The first letter of the name for the picture in the list. */
    public String initial() {
        String name = displayName().strip();
        return name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }

    public Account withLoggedIn(boolean value) {
        return new Account(login, uid, fullName, home, shell, groups, disabled, value);
    }
}
