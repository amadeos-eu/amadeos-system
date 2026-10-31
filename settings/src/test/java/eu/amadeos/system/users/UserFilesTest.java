package eu.amadeos.system.users;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserFilesTest {
    static final List<String> PASSWD = List.of(
            "root:x:0:0:root:/root:/bin/bash",
            "systemd-network:x:998:998:systemd Network Management:/:/usr/sbin/nologin",
            "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin",
            "ralf:x:1000:1000:Ralf Eichinger,,,:/home/ralf:/bin/bash",
            "tom:x:1001:1001::/home/tom:/bin/bash",
            "gast:x:1002:1002:Gast:/home/gast:/usr/sbin/nologin",
            "broken:x:abc");
    static final List<String> GROUP = List.of(
            "root:x:0:",
            "sudo:x:27:ralf",
            "audio:x:29:ralf,tom,gast",
            "lpadmin:x:120:ralf",
            "dip:x:30:ralf",
            "ralf:x:1000:",
            "tom:x:1001:",
            "gast:x:1002:",
            "family:x:1010:ralf,tom");

    @Test
    void accountsArePeopleWithTheirGroups() {
        List<Account> accounts = UserFiles.accounts(PASSWD, GROUP, Set.of("ralf"));
        assertEquals(List.of("gast", "ralf", "tom"), accounts.stream().map(Account::login).toList());
        Account ralf = accounts.get(1);
        assertEquals("Ralf Eichinger", ralf.fullName());
        assertTrue(ralf.admin());
        assertTrue(ralf.loggedIn());
        assertEquals(Set.of("sudo", "audio", "lpadmin", "dip", "family"), ralf.groups());
        Account tom = accounts.get(2);
        assertEquals("tom", tom.displayName());
        assertEquals("T", tom.initial());
        assertFalse(tom.admin());
        assertTrue(accounts.getFirst().disabled());
    }

    @Test
    void groupsArePermissionsInOrderThenOwnGroupsWithoutPersonalOnes() {
        List<UserGroup> groups = UserFiles.groups(PASSWD, GROUP);
        assertEquals(List.of("sudo", "lpadmin", "audio", "family"), groups.stream().map(UserGroup::name).toList());
        assertEquals(UserGroup.Kind.OWN, groups.getLast().kind());
        assertEquals(List.of("ralf", "tom"), groups.getLast().members());
    }

    @Test
    void systemUsersReadsTheFiles() throws Exception {
        Path dir = Files.createTempDirectory("users");
        Files.write(dir.resolve("passwd"), PASSWD, StandardCharsets.UTF_8);
        Files.write(dir.resolve("group"), GROUP, StandardCharsets.UTF_8);
        SystemUsers users = new SystemUsers(dir.resolve("passwd"), dir.resolve("group"));
        assertEquals(3, users.accounts().size());
        assertEquals(4, users.groups().size());
        assertThrows(UserService.UserException.class,
                () -> new SystemUsers(dir.resolve("none"), dir.resolve("group")).accounts());
    }

    @Test
    void helperOutcomesBecomeExceptions() throws Exception {
        SystemUsers.check(0, "@@done");
        UserService.UserException e = assertThrows(UserService.UserException.class,
                () -> SystemUsers.check(2, "@@error last-admin ralf is the last administrator\n"));
        assertEquals(UserService.UserException.Reason.REFUSED, e.reason());
        assertEquals("last-admin", e.code());
        assertEquals("ralf is the last administrator", e.getMessage());
        assertEquals(UserService.UserException.Reason.DISMISSED,
                assertThrows(UserService.UserException.class, () -> SystemUsers.check(126, "")).reason());
        assertEquals(UserService.UserException.Reason.NOT_AUTHORIZED,
                assertThrows(UserService.UserException.class, () -> SystemUsers.check(127, "")).reason());
        assertEquals(UserService.UserException.Reason.FAILED,
                assertThrows(UserService.UserException.class, () -> SystemUsers.check(4, "@@error failed useradd failed")).reason());
    }

    @Test
    void passwdMessagesBecomeReasons() {
        assertEquals("wrong-current", SystemUsers.passwdFailure("Current password: passwd: Authentication token "
                + "manipulation error\npasswd: password unchanged\nChanging password for tom.\n").code());
        UserService.UserException e = SystemUsers.passwdFailure("Current password: New password: BAD PASSWORD: it is "
                + "too short\npasswd: password unchanged\n");
        assertEquals("BAD PASSWORD: it is too short", e.getMessage());
    }

    @Test
    void loginNames() {
        assertTrue(LoginNames.valid("anna"));
        assertTrue(LoginNames.valid("a-b_1"));
        assertFalse(LoginNames.valid("Anna"));
        assertFalse(LoginNames.valid("1anna"));
        assertFalse(LoginNames.valid("a".repeat(33)));
        assertEquals("juergen", LoginNames.suggest("Jürgen Weiß", List.of()));
        assertEquals("juergenw", LoginNames.suggest("Jürgen Weiß", List.of("juergen")));
        assertEquals("juergen2", LoginNames.suggest("Jürgen", List.of("juergen")));
        assertEquals("francois", LoginNames.suggest("François Gérard", List.of()));
        assertEquals("", LoginNames.suggest("  ", List.of()));
        assertTrue(LoginNames.validFullName("Anna Müller"));
        assertFalse(LoginNames.validFullName("Anna:x"));
    }

    /** The helper script with AMADEOS_USERS_RUN=echo: it checks its rules and shows the commands it would run. */
    @Test
    void helperRefusesWhatItMustAndRunsTheShadowTools() throws Exception {
        Path script = Path.of("packaging/root/usr/libexec/amadeos-users").toAbsolutePath();
        Path dir = Files.createTempDirectory("users-helper");
        Files.write(dir.resolve("passwd"), PASSWD, StandardCharsets.UTF_8);
        Files.write(dir.resolve("group"), GROUP, StandardCharsets.UTF_8);

        assertEquals("2 @@error last-admin ralf is the last administrator", helper(script, dir, "", "admin", "ralf", "no"));
        assertEquals("2 @@error self you cannot remove your own account", helper(script, dir, "", "remove", "ralf", "keep-home"));
        assertEquals("2 @@error unknown no such account: root", helper(script, dir, "", "name", "root", "Root"));
        assertEquals("2 @@error exists the account tom exists", helper(script, dir, "pw\n", "add", "tom", "standard", "Tom"));
        assertEquals("2 @@error group dip is a group of the system", helper(script, dir, "", "join", "tom", "dip"));
        assertEquals("2 @@error primary ralf is the personal group of an account", helper(script, dir, "", "join", "tom", "ralf"));
        assertEquals("2 @@error invalid not a full name: a:b", helper(script, dir, "", "name", "tom", "a:b"));
        assertEquals("2 @@error password empty password", helper(script, dir, "", "password", "tom"));
        assertEquals("0 @@done", helper(script, dir, "", "admin", "tom", "yes"));
        assertEquals("0 @@done", helper(script, dir, "", "join", "tom", "family"));
        assertEquals("0 @@done", helper(script, dir, "secret\n", "add", "anna", "admin", "Anna Eichinger"));
        assertEquals("0 @@done", helper(script, dir, "", "group-remove", "family"));
        assertEquals("2 @@error group lpadmin is a group of the system", helper(script, dir, "", "group-remove", "lpadmin"));
    }

    private static String helper(Path script, Path dir, String input, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("bash", script.toString()));
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.environment().put("AMADEOS_USERS_PASSWD", dir.resolve("passwd").toString());
        pb.environment().put("AMADEOS_USERS_GROUP", dir.resolve("group").toString());
        pb.environment().put("AMADEOS_USERS_RUN", "echo");
        pb.environment().put("PKEXEC_UID", "1000");
        Process p = pb.start();
        p.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        return p.waitFor() + " " + out;
    }
}
