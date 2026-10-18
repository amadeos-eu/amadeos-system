package eu.amadeos.dm.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LastUserTest {
    private static final String PASSWD = """
            root:x:0:0:root:/root:/bin/bash
            daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin
            nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin
            service:x:1000:1000::/var/lib/service:/usr/sbin/nologin
            ralf:x:1001:1001:Ralf Eichinger,,,:/home/ralf:/bin/bash
            anna:x:1002:1002::/home/anna:/bin/bash
            """;

    @Test
    void picksTheFirstRegularUserWithALoginShell() {
        assertEquals(Optional.of("ralf"), LastUser.firstRegularUser(PASSWD));
        assertEquals(Optional.empty(), LastUser.firstRegularUser("root:x:0:0:root:/root:/bin/bash\n"));
        assertEquals(Optional.empty(), LastUser.firstRegularUser("broken line\n"));
    }

    @Test
    void proposesTheLastUserBeforeThePasswdEntry(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("etc"));
        Files.writeString(root.resolve(LastUser.PASSWD), PASSWD);
        LastUser users = new LastUser(root);
        assertEquals("ralf", users.propose());
        users.remember("anna");
        assertEquals("anna", users.propose());
        assertEquals(Optional.of("anna"), users.last());
        // Garbage in the state file is ignored.
        Files.writeString(root.resolve(LastUser.STATE_FILE), "../x\n");
        assertEquals("ralf", users.propose());
        // Only plausible names are remembered.
        users.remember("Not A User");
        assertEquals("ralf", users.propose());
    }

    @Test
    void proposesNothingWithoutAnyUser(@TempDir Path root) {
        assertEquals("", new LastUser(root).propose());
    }
}
