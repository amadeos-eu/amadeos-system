package eu.amadeos.logviewer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DesktopLogsTest {
    @TempDir
    Path directory;

    @Test
    void sessionAndWindowManagerComeFirstThenTheApplicationsByName() throws IOException {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        for (String name : List.of("xterm.log", "wm.log", "amade-files.log", "session.log", "firefox-esr.log",
                "firefox-esr.log.old", "notes.txt")) {
            Files.writeString(logs.resolve(name), name);
        }
        Path runner = directory.resolve("amadeos-session-runner.log");
        Files.writeString(runner, "runner");
        Path missing = directory.resolve("amadeos-xorg.log");

        List<DesktopLogs.Source> sources = DesktopLogs.list(logs, List.of(runner, missing));
        assertEquals(List.of("Session", "Window Manager", "amade-files", "firefox-esr", "xterm",
                "Display Manager: session runner"), sources.stream().map(DesktopLogs.Source::title).toList());
        assertEquals(logs.resolve("session.log"), sources.get(0).file());
        assertEquals("runner", sources.get(5).read());
        assertTrue(sources.get(2).label().endsWith("(" + logs.resolve("amade-files.log") + ")"));
    }

    @Test
    void aMissingDirectoryGivesAnEmptyList() {
        assertEquals(List.of(), DesktopLogs.list(directory.resolve("nowhere"), List.of()));
    }

    @Test
    void onlyTheEndOfALargeLogIsShownFromAWholeLine() throws IOException {
        Path log = directory.resolve("big.log");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            content.append("line ").append(i).append('\n');
        }
        Files.writeString(log, content.toString(), StandardCharsets.UTF_8);

        String tail = DesktopLogs.tail(log, 100);
        assertTrue(tail.startsWith("(... the first "), tail);
        String[] lines = tail.split("\n");
        assertTrue(lines[1].matches("line \\d+"), "the cut starts at a whole line: " + lines[1]);
        assertEquals("line 999", lines[lines.length - 1]);
        assertTrue(tail.length() < 200);

        assertEquals(content.toString(), DesktopLogs.tail(log, (int) Files.size(log)), "a small log is complete");
        assertTrue(DesktopLogs.tail(directory.resolve("none.log"), 100).contains("does not exist"));
    }

    @Test
    void theLabelShowsTheHomeDirectoryAsATilde() {
        Path home = Path.of(System.getProperty("user.home"));
        DesktopLogs.Source inHome = new DesktopLogs.Source("Session", home.resolve(".cache/amadeos/logs/session.log"), null);
        assertEquals("Session  (~/.cache/amadeos/logs/session.log)", inHome.label());
        DesktopLogs.Source elsewhere = new DesktopLogs.Source("X server", Path.of("/var/log/amadeos-xorg.log"), null);
        assertEquals("X server  (/var/log/amadeos-xorg.log)", elsewhere.label());
        assertEquals(elsewhere.label(), elsewhere.toString());
    }

    @Test
    void aJournalSourceHasNoFileAndReadsItsCommand() {
        DesktopLogs.Source journal = new DesktopLogs.Source("System Journal", null, List.of("sh", "-c", "echo journal"));
        assertEquals("System Journal", journal.label());
        assertEquals("journal\n", journal.read());
    }

    @Test
    void theJournalCommandsOutputIsReturned() {
        assertEquals("hello\n", DesktopLogs.journal(List.of("sh", "-c", "echo hello")));
        assertTrue(DesktopLogs.journal(List.of("amadeos-no-such-command")).startsWith("(Cannot run"));
    }
}
