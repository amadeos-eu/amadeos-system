package eu.amadeos.system.base.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProcessLauncherTest {
    @TempDir Path directory;

    @Test
    void childWithLotsOfOutputDoesNotBlock() throws Exception {
        // ~200 KB of output: with an unread pipe the child would block after ~64 KB.
        Path log = directory.resolve("apps.log");
        Process process = ProcessLauncher.start(
                List.of("sh", "-c", "head -c 200000 /dev/zero | tr '\\0' x"), directory.toFile(), log);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "child must not block on its output");
        assertEquals(0, process.exitValue());
        assertEquals(200_000, Files.size(log));
    }

    @Test
    void argumentsAreNotSplitAndStdinIsClosed() throws Exception {
        Path log = directory.resolve("apps.log");
        Process process = ProcessLauncher.start(
                List.of("sh", "-c", "printf '[%s]' \"$1\"; cat", "sh", "my home dir"), directory.toFile(), log);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "cat must see end of input instead of waiting");
        assertEquals("[my home dir]", Files.readString(log));
    }

    @Test
    void runsInTheGivenWorkingDirectory() throws Exception {
        Path log = directory.resolve("apps.log");
        File workDir = Files.createDirectory(directory.resolve("work")).toFile();
        Process process = ProcessLauncher.start(List.of("pwd"), workDir, log);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(workDir.getCanonicalPath(), Files.readString(log).trim());
    }

    @Test
    void emptyCommandIsRejected() {
        assertThrows(IOException.class, () -> ProcessLauncher.start(List.of(), directory.toFile(), directory.resolve("x.log")));
    }

    @Test
    void findsExecutablesOnTheGivenPathOnly() throws IOException {
        Path bin = Files.createDirectory(directory.resolve("bin"));
        Path tool = Files.createFile(bin.resolve("mytool"));
        Files.createFile(bin.resolve("notexecutable"));
        assertTrue(tool.toFile().setExecutable(true));
        String path = "/nonexistent:" + bin;
        assertTrue(ProcessLauncher.isOnPath("mytool", path));
        assertFalse(ProcessLauncher.isOnPath("notexecutable", path));
        assertFalse(ProcessLauncher.isOnPath("missing", path));
        assertTrue(ProcessLauncher.isOnPath(tool.toString(), "/nonexistent"));
        assertFalse(ProcessLauncher.isOnPath("", path));
    }

    @Test
    void firstAvailableSkipsMissingPrograms() {
        assertEquals(List.of("sh", "-c", "true"), ProcessLauncher.firstAvailable(
                List.of(List.of("amadeos-no-such-program"), List.of("sh", "-c", "true"))).orElseThrow());
        assertTrue(ProcessLauncher.firstAvailable(List.of(List.of("amadeos-no-such-program"))).isEmpty());
    }

    @Test
    void eachProgramHasItsOwnLogNamedAfterIt() {
        assertEquals("firefox-esr", ProcessLauncher.logName(List.of("firefox-esr", "https://example.org")));
        assertEquals("xterm", ProcessLauncher.logName(List.of("/usr/bin/xterm", "-e", "sudo", "sh")));
        assertEquals("amade-files", ProcessLauncher.logName(List.of("amade-files")));
        assertEquals("my_tool_v2.0", ProcessLauncher.logName(List.of("my tool v2.0")));
        assertEquals("application", ProcessLauncher.logName(List.of("...")));
        assertEquals("application", ProcessLauncher.logName(List.of()));
        assertTrue(ProcessLauncher.logFile(List.of("xterm")).endsWith(Path.of("amadeos", "logs", "xterm.log")));
    }

    @Test
    void largeLogIsRotated() throws IOException {
        Path log = directory.resolve("apps.log");
        Files.write(log, new byte[2048]);
        ProcessLauncher.rotateIfLarge(log, 1024);
        assertFalse(Files.exists(log));
        assertEquals(2048, Files.size(directory.resolve("apps.log.old")));
        Files.write(log, new byte[10]);
        ProcessLauncher.rotateIfLarge(log, 1024);
        assertTrue(Files.exists(log));
    }
}
