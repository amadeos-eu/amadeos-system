package eu.amadeos.system.polkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import eu.amadeos.system.polkit.Users.User;
import eu.amadeos.system.polkit.dbus.Subject;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsersTest {
    @TempDir
    Path dir;

    static Subject user(long uid) {
        return new Subject("unix-user", Map.of("uid", new Variant<>(new UInt32(uid))));
    }

    @Test
    void accountsComeFromPasswdWithTheirFullNames() throws Exception {
        Path passwd = Files.writeString(dir.resolve("passwd"), """
                root:x:0:0:root:/root:/bin/bash
                amadeos:x:1000:1000:amadeOS Live User,,,:/home/amadeos:/bin/bash
                ralf:x:1001:1001::/home/ralf:/bin/bash
                broken line
                """);
        Map<Long, User> accounts = Users.read(passwd);
        assertEquals(new User(1000, "amadeos", "amadeOS Live User"), accounts.get(1000L));
        assertEquals("amadeOS Live User (amadeos)", accounts.get(1000L).label());
        assertEquals("ralf", accounts.get(1001L).label());
        assertEquals(3, accounts.size());
    }

    @Test
    void theSessionsOwnUserComesFirstAndUnknownOnesAreLeftOut() {
        Map<Long, User> accounts = Map.of(0L, new User(0, "root", "root"), 1000L, new User(1000, "zoe", "Zoe"),
                1001L, new User(1001, "anna", "Anna"));
        List<Subject> identities = List.of(user(1001), user(0), user(1000), user(4242),
                new Subject("unix-group", Map.of("gid", new Variant<>(new UInt32(27)))));
        assertEquals(List.of("zoe", "anna", "root"),
                Users.of(identities, accounts, "zoe").stream().map(User::login).toList());
    }
}
