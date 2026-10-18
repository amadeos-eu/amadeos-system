package eu.amadeos.system.dm.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The user name the greeter proposes: the user who logged in last ({@code /var/lib/amadeos-dm/last-user},
 * written by the display manager, which runs as root), else the first regular user of {@code /etc/passwd}
 * (UID 1000 and above with a login shell), which is the live user on the live image and the user created
 * by the installer on an installed system.
 */
public final class LastUser {
    public static final LastUser SYSTEM = new LastUser(Path.of("/"));
    static final String STATE_FILE = "var/lib/amadeos-dm/last-user";
    static final String PASSWD = "etc/passwd";
    private static final Logger LOGGER = Logger.getLogger(LastUser.class.getName());
    private static final Pattern NAME = Pattern.compile("[a-z_][a-z0-9_-]{0,31}\\$?");
    private static final int FIRST_UID = 1000;
    private static final int LAST_UID = 59999;

    private final Path root;

    /** @param root the directory that stands for {@code /}; tests use a temporary one */
    LastUser(Path root) {
        this.root = root;
    }

    /** The name to put into the user field; empty when nothing is known. */
    public String propose() {
        return last().or(this::firstRegularUser).orElse("");
    }

    /** The user who logged in last, if the state file exists and holds a plausible name. */
    public Optional<String> last() {
        Path file = root.resolve(STATE_FILE);
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            String name = Files.readString(file, StandardCharsets.UTF_8).strip();
            return NAME.matcher(name).matches() ? Optional.of(name) : Optional.empty();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Cannot read " + file, e);
            return Optional.empty();
        }
    }

    /** Remembers the user for the next start of the greeter; failures are logged, never thrown. */
    public void remember(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            return;
        }
        Path file = root.resolve(STATE_FILE);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, name + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot remember the last user in " + file + ": " + e.getMessage());
        }
    }

    /** The first user of {@code /etc/passwd} with a UID in the regular range and a shell that allows logins. */
    public Optional<String> firstRegularUser() {
        try {
            return firstRegularUser(Files.readString(root.resolve(PASSWD), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Cannot read " + root.resolve(PASSWD), e);
            return Optional.empty();
        }
    }

    static Optional<String> firstRegularUser(String passwd) {
        for (String line : passwd.split("\n")) {
            String[] fields = line.split(":", -1);
            if (fields.length < 7) {
                continue;
            }
            int uid;
            try {
                uid = Integer.parseInt(fields[2].strip());
            } catch (NumberFormatException e) {
                continue;
            }
            String shell = fields[6].strip();
            if (uid >= FIRST_UID && uid <= LAST_UID && !shell.endsWith("/nologin") && !shell.endsWith("/false")
                    && NAME.matcher(fields[0]).matches()) {
                return Optional.of(fields[0]);
            }
        }
        return Optional.empty();
    }
}
