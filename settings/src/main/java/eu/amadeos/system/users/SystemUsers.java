package eu.amadeos.system.users;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The real computer: reads {@code /etc/passwd} and {@code /etc/group}, asks logind ({@code loginctl}) who is
 * logged in, and changes accounts through {@code pkexec /usr/libexec/amadeos-users}. The helper prints
 * {@code @@done} or {@code @@error ID MESSAGE}; passwords go to it on standard input, never on the command line.
 * {@code AMADEOS_USERS_HELPER} in the environment replaces the helper command, for trying the desktop.
 */
public final class SystemUsers implements UserService {
    public static final String HELPER = "/usr/libexec/amadeos-users";
    public static final String HELPER_VARIABLE = "AMADEOS_USERS_HELPER";

    private final Path passwd;
    private final Path group;

    public SystemUsers() {
        this(Path.of("/etc/passwd"), Path.of("/etc/group"));
    }

    SystemUsers(Path passwd, Path group) {
        this.passwd = passwd;
        this.group = group;
    }

    @Override
    public List<Account> accounts() throws UserException {
        return UserFiles.accounts(read(passwd), read(group), loggedIn());
    }

    @Override
    public List<UserGroup> groups() throws UserException {
        return UserFiles.groups(read(passwd), read(group));
    }

    @Override
    public String self() {
        return System.getProperty("user.name", "");
    }

    @Override
    public void add(String login, String fullName, boolean admin, char[] password) throws UserException {
        helper(password, "add", login, admin ? "admin" : "standard", fullName);
    }

    @Override
    public void remove(String login, boolean deleteHome) throws UserException {
        helper(null, "remove", login, deleteHome ? "delete-home" : "keep-home");
    }

    @Override
    public void setFullName(String login, String fullName) throws UserException {
        helper(null, "name", login, fullName);
    }

    @Override
    public void setAdmin(String login, boolean admin) throws UserException {
        helper(null, "admin", login, admin ? "yes" : "no");
    }

    @Override
    public void setPassword(String login, char[] password) throws UserException {
        helper(password, "password", login);
    }

    @Override
    public void changeOwnPassword(char[] current, char[] password) throws UserException {
        // passwd reads the PAM conversation from standard input when it is not a terminal: current, new, again.
        char[] input = (new String(current) + "\n" + new String(password) + "\n" + new String(password) + "\n")
                .toCharArray();
        // In English, so that passwdFailure understands it.
        Run run = run(List.of("env", "LC_ALL=C.UTF-8", "passwd"), input);
        Arrays.fill(input, '\0');
        if (run.exit() != 0) throw passwdFailure(run.output());
    }

    /**
     * What passwd said: "Authentication token manipulation error" (or "failure") when the current password was
     * wrong, code "wrong-current"; otherwise its message, e.g. pwquality's "BAD PASSWORD: it is too short".
     */
    static UserException passwdFailure(String output) {
        if (output.contains("Authentication token manipulation error") || output.contains("Authentication failure")) {
            return new UserException(UserException.Reason.WRONG_PASSWORD, "wrong-current", "");
        }
        String message = output.lines()
                .map(l -> l.replaceAll("^(?:(?:Current|New|Retype new) password: )+", "").strip())
                .filter(l -> !l.isEmpty() && !l.startsWith("Changing password") && !l.contains("password unchanged"))
                .findFirst().orElse("passwd failed");
        return new UserException(UserException.Reason.WRONG_PASSWORD, "passwd", message.replaceFirst("^passwd: ", ""));
    }

    @Override
    public void setDisabled(String login, boolean disabled) throws UserException {
        helper(null, disabled ? "disable" : "enable", login);
    }

    @Override
    public void join(String login, String group) throws UserException {
        helper(null, "join", login, group);
    }

    @Override
    public void leave(String login, String group) throws UserException {
        helper(null, "leave", login, group);
    }

    @Override
    public void addGroup(String group) throws UserException {
        helper(null, "group-add", group);
    }

    @Override
    public void removeGroup(String group) throws UserException {
        helper(null, "group-remove", group);
    }

    private static List<String> read(Path file) throws UserException {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UserException(UserException.Reason.FAILED, "read", file + ": " + e.getMessage());
        }
    }

    /** The logins with a session: {@code loginctl list-users --no-legend} prints "UID USER …" per line. */
    private static Set<String> loggedIn() {
        Set<String> users = new HashSet<>();
        try {
            Run run = run(List.of("loginctl", "list-users", "--no-legend"), null);
            for (String line : run.output().lines().toList()) {
                String[] f = line.strip().split("\\s+");
                if (f.length >= 2) users.add(f[1]);
            }
        } catch (UserException e) {
            // no logind (a container): nobody is shown as logged in
        }
        return users;
    }

    static List<String> helperCommand() {
        String override = System.getenv(HELPER_VARIABLE);
        if (override != null && !override.isBlank()) return List.of(override.strip().split("\\s+"));
        return "root".equals(System.getProperty("user.name")) ? List.of(HELPER) : List.of("pkexec", HELPER);
    }

    // --8<-- [start:helper]
    private static void helper(char[] stdin, String... arguments) throws UserException {
        List<String> command = new ArrayList<>(helperCommand());
        command.addAll(List.of(arguments));
        char[] input = stdin == null ? null : (new String(stdin) + "\n").toCharArray();
        Run run = run(command, input);
        if (input != null) Arrays.fill(input, '\0');
        check(run.exit(), run.output());
    }

    /** Turns the helper's exit code and "@@error ID MESSAGE" line into an exception; nothing for success. */
    static void check(int exit, String output) throws UserException {
        if (exit == 0) return;
        // pkexec's own codes: 126 when the password prompt was closed, 127 when polkit refused.
        if (exit == 126) throw new UserException(UserException.Reason.DISMISSED, "dismissed", "");
        if (exit == 127) throw new UserException(UserException.Reason.NOT_AUTHORIZED, "not-authorized", "");
        String code = "";
        String message = "";
        for (String line : output.lines().toList()) {
            if (line.startsWith("@@error ")) {
                String[] f = line.substring(8).split(" ", 2);
                code = f[0];
                message = f.length > 1 ? f[1] : "";
            }
        }
        if (message.isEmpty()) message = output.lines().reduce((a, b) -> b).orElse("exit " + exit);
        throw new UserException(exit == 2 ? UserException.Reason.REFUSED
                : code.equals("password") ? UserException.Reason.WRONG_PASSWORD : UserException.Reason.FAILED,
                code, message);
    }
    // --8<-- [end:helper]

    private record Run(int exit, String output) {
    }

    private static Run run(List<String> command, char[] input) throws UserException {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (OutputStream in = process.getOutputStream()) {
                if (input != null) {
                    byte[] bytes = new String(input).getBytes(StandardCharsets.UTF_8);
                    in.write(bytes);
                    Arrays.fill(bytes, (byte) 0);
                }
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new UserException(UserException.Reason.FAILED, "timeout", command.getFirst() + " did not finish");
            }
            return new Run(process.exitValue(), output);
        } catch (IOException e) {
            throw new UserException(UserException.Reason.FAILED, "start", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UserException(UserException.Reason.FAILED, "interrupted", e.getMessage());
        }
    }
}
