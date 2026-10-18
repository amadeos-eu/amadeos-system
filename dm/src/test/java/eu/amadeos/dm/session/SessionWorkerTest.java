package eu.amadeos.dm.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import eu.amadeos.base.x11.LibC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Runs the worker end to end in development mode (not root, mock PAM): the session is started
 * as the current user with a fresh environment and the worker returns its exit code.
 */
class SessionWorkerTest {
    @TempDir Path directory;

    @AfterEach
    void clearProperties() {
        System.clearProperty("amadeos.mock.pam");
        System.clearProperty("amadeos.session.executable");
    }

    @Test
    void startsSessionAndReturnsItsExitCode() throws Exception {
        assumeFalse(LibC.INSTANCE.getuid() == 0, "development mode needs a non-root user");
        Path report = directory.resolve("session.txt");
        Path script = directory.resolve("session.sh");
        Files.writeString(script, "#!/bin/sh\n{ echo \"ARGS=$*\"; env; } > '" + report + "'\nexit 3\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        System.setProperty("amadeos.mock.pam", "true");
        System.setProperty("amadeos.session.executable", script.toString());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = SessionWorker.run(new String[]{":42"},
                new ByteArrayInputStream("amadeos\namadeos\ndesktop\n".getBytes(StandardCharsets.UTF_8)), out);

        assertEquals("OK", out.toString(StandardCharsets.UTF_8).trim());
        assertEquals(3, exit);
        String seen = Files.readString(report);
        assertTrue(seen.contains("ARGS=:42"));
        assertTrue(seen.contains("DISPLAY=:42"));
        assertTrue(seen.contains("AMADEOS_SESSION=desktop"));
        assertTrue(seen.contains("USER=" + System.getProperty("user.name")));
    }

    @Test
    void wrongPasswordIsReportedAndNoSessionStarts() throws Exception {
        assumeFalse(LibC.INSTANCE.getuid() == 0, "development mode needs a non-root user");
        Path marker = directory.resolve("started");
        Path script = directory.resolve("session.sh");
        Files.writeString(script, "#!/bin/sh\ntouch '" + marker + "'\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        System.setProperty("amadeos.mock.pam", "true");
        System.setProperty("amadeos.session.executable", script.toString());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = SessionWorker.run(new String[]{":42"},
                new ByteArrayInputStream("amadeos\nwrong\ndesktop\n".getBytes(StandardCharsets.UTF_8)), out);

        assertEquals(1, exit);
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("FAIL"));
        assertFalse(Files.exists(marker));
    }
}
