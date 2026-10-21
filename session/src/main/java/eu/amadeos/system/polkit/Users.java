package eu.amadeos.system.polkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.amadeos.system.polkit.dbus.Subject;

/** The users polkit offers to authenticate as, with names from {@code /etc/passwd}. */
final class Users {
    private static final Logger LOGGER = Logger.getLogger(Users.class.getName());

    /** One account: login name and the full name of its GECOS field (the login when that is empty). */
    record User(long uid, String login, String fullName) {
        /** "Full Name (login)", or the login alone. */
        String label() {
            return fullName.equals(login) ? login : fullName + " (" + login + ")";
        }
    }

    private Users() {
    }

    /** The accounts of {@code /etc/passwd} by user id. */
    static Map<Long, User> read(Path passwd) {
        Map<Long, User> users = new HashMap<>();
        try {
            for (String line : Files.readAllLines(passwd, StandardCharsets.UTF_8)) {
                String[] fields = line.split(":", -1);
                if (fields.length < 5 || fields[0].isEmpty()) continue;
                try {
                    long uid = Long.parseLong(fields[2]);
                    String gecos = fields[4].split(",", -1)[0].trim();
                    users.putIfAbsent(uid, new User(uid, fields[0], gecos.isEmpty() ? fields[0] : gecos));
                } catch (NumberFormatException e) {
                    // not an account line
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot read " + passwd, e);
        }
        return users;
    }

    /**
     * The "unix-user" identities that exist, the one of {@code self} first (usually the user is an
     * administrator), the others by name. Other kinds are left out: polkitd expands groups to their users.
     */
    static List<User> of(List<Subject> identities, Map<Long, User> accounts, String self) {
        List<User> users = new ArrayList<>();
        for (Subject identity : identities) {
            User user = accounts.get(identity.uid());
            if (user != null && !users.contains(user)) users.add(user);
        }
        users.sort(Comparator.comparing((User u) -> !u.login().equals(self)).thenComparing(User::login));
        return users;
    }
}
