package eu.amadeos.system.base.xdg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class XdgDesktopEntryTest {

    @Test
    void testParseValidDesktopEntry() throws IOException {
        String content = """
                [Desktop Entry]
                Type=Application
                Name=AmadePad Text Editor
                GenericName=Text Editor
                Comment=Edit plain text files with Motif style
                Exec=amade-pad %F
                Icon=accessories-text-editor
                Terminal=false
                Categories=Utility;TextEditor;
                MimeType=text/plain;text/x-java;
                """;

        Optional<XdgDesktopEntry> opt = XdgDesktopEntryParser.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "amade-pad.desktop",
                null
        );

        assertTrue(opt.isPresent());
        XdgDesktopEntry entry = opt.get();
        assertEquals("amade-pad.desktop", entry.id());
        assertEquals("AmadePad Text Editor", entry.name());
        assertEquals("Text Editor", entry.genericName());
        assertEquals("accessories-text-editor", entry.icon());
        assertEquals("amade-pad %F", entry.exec());
        assertFalse(entry.terminal());
        assertTrue(entry.isVisible());

        assertTrue(entry.hasCategory("TextEditor"));
        assertTrue(entry.hasCategory("utility"));
        assertFalse(entry.hasCategory("Games"));

        assertTrue(entry.matchesCategory(XdgCategory.TEXT_EDITOR));

        List<String> cmd = entry.buildCommandLine("/tmp/notes.txt");
        assertEquals(List.of("amade-pad", "/tmp/notes.txt"), cmd);
    }

    @Test
    void testTerminalApplicationWrapping() throws IOException {
        String content = """
                [Desktop Entry]
                Type=Application
                Name=Disk Usage Analyzer
                Exec=ncdu /
                Terminal=true
                Categories=System;Monitor;
                """;

        Optional<XdgDesktopEntry> opt = XdgDesktopEntryParser.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "ncdu.desktop",
                null
        );

        assertTrue(opt.isPresent());
        XdgDesktopEntry entry = opt.get();
        assertTrue(entry.terminal());
        assertTrue(entry.matchesCategory(XdgCategory.MONITOR));

        List<String> cmd = entry.buildCommandLine();
        assertTrue(cmd.size() >= 5);
        assertEquals("xterm", cmd.get(0));
        assertEquals("-title", cmd.get(1));
        assertEquals("Disk Usage Analyzer", cmd.get(2));
        assertEquals("-e", cmd.get(3));
        assertEquals("ncdu", cmd.get(4));
        assertEquals("/", cmd.get(5));
    }

    @Test
    void testHiddenAndNoDisplayEntries() throws IOException {
        String content = """
                [Desktop Entry]
                Type=Application
                Name=Secret App
                Exec=secret
                NoDisplay=true
                """;

        Optional<XdgDesktopEntry> opt = XdgDesktopEntryParser.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "secret.desktop",
                null
        );

        assertTrue(opt.isPresent());
        assertFalse(opt.get().isVisible());
    }

    @Test
    void testEntryOfMissingProgramIsNotInstalled() throws IOException {
        String content = """
                [Desktop Entry]
                Type=Application
                Name=Vim
                Exec=amadeos-test-no-such-program %F
                Terminal=true
                """;

        Optional<XdgDesktopEntry> opt = XdgDesktopEntryParser.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "vim.desktop",
                null
        );

        assertTrue(opt.isPresent());
        assertFalse(opt.get().isInstalled());
    }

    @Test
    void testTryExecDecidesWhetherInstalled() throws IOException {
        String content = """
                [Desktop Entry]
                Type=Application
                Name=Shell Wrapper
                TryExec=/bin/sh
                Exec=amadeos-test-no-such-program
                """;

        Optional<XdgDesktopEntry> opt = XdgDesktopEntryParser.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "wrapper.desktop",
                null
        );

        assertTrue(opt.isPresent());
        assertEquals("/bin/sh", opt.get().tryExec());
        assertTrue(opt.get().isInstalled());
    }

    @Test
    void testNonApplicationTypeIgnored() throws IOException {
        String content = """
                [Desktop Entry]
                Type=Link
                URL=https://github.com/datazuul/amadeos
                Name=amadeOS Web
                """;

        Optional<XdgDesktopEntry> opt = XdgDesktopEntryParser.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "web.desktop",
                null
        );

        assertTrue(opt.isEmpty());
    }

    @Test
    void testCommandTokenization() {
        List<String> tokens = XdgDesktopEntry.tokenize("gedit --new-window \"my document.txt\" 'another file.md'");
        assertEquals(List.of("gedit", "--new-window", "my document.txt", "another file.md"), tokens);
    }

    @Test
    void testManagerDirectoryScanning(@TempDir Path tempDir) throws IOException {
        Path appsDir = tempDir.resolve("applications");
        Files.createDirectories(appsDir);

        Path app1 = appsDir.resolve("amade-calc.desktop");
        Files.writeString(app1, """
                [Desktop Entry]
                Type=Application
                Name=amadeOS Calculator
                Exec=amade-calc
                Icon=accessories-calculator
                Categories=Utility;Calculator;
                """);

        Path app2 = appsDir.resolve("amade-term.desktop");
        Files.writeString(app2, """
                [Desktop Entry]
                Type=Application
                Name=Console
                Exec=xterm
                Icon=utilities-terminal
                Categories=System;TerminalEmulator;
                """);

        Optional<XdgDesktopEntry> p1 = XdgDesktopEntryParser.parse(app1);
        Optional<XdgDesktopEntry> p2 = XdgDesktopEntryParser.parse(app2);

        assertTrue(p1.isPresent());
        assertTrue(p2.isPresent());
        assertTrue(p1.get().matchesCategory(XdgCategory.UTILITY));
        assertTrue(p2.get().matchesCategory(XdgCategory.TERMINAL));
    }
}

