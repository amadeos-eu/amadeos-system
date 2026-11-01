package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockTest {
    private static XdgDesktopEntry entry(String id, String name, String exec) {
        return new XdgDesktopEntry(id, name, "", "", "icon", exec, false, List.of("Utility"), List.of(),
                false, false, List.of(), List.of(), "", Path.of("/usr/share/applications/" + id));
    }

    private final List<XdgDesktopEntry> entries = List.of(
            entry("amade-files.desktop", "Files", "/usr/bin/amade-files %F"),
            entry("amade-writepad.desktop", "WritePad", "/usr/bin/amade-writepad %F"),
            entry("firefox-esr.desktop", "Firefox ESR", "/usr/lib/firefox-esr/firefox-esr %u"));

    @Test
    void favouritesComeFirstThenTheOtherRunningPrograms() {
        List<DockModel.Window> windows = List.of(
                new DockModel.Window(1, "xterm", "bash", false),
                new DockModel.Window(2, "eu-amadeos-apps-writepad-WritePadApplication", "Letter", false),
                new DockModel.Window(3, "eu-amadeos-apps-writepad-WritePadApplication", "Notes", true),
                new DockModel.Window(4, "xterm", "top", true));
        List<DockModel.Program> programs = DockModel.programs(
                List.of("amade-files.desktop", "amade-writepad.desktop", "gone.desktop"), entries, windows);

        assertEquals(List.of("amade-files.desktop", "amade-writepad.desktop", "class:xterm"),
                programs.stream().map(DockModel.Program::key).toList(), "a favourite that is not installed is left out");
        DockModel.Program files = programs.get(0);
        assertTrue(files.favourite());
        assertFalse(files.running());
        DockModel.Program writepad = programs.get(1);
        assertEquals(2, writepad.windows().size());
        assertFalse(writepad.allMinimized());
        DockModel.Program xterm = programs.get(2);
        assertFalse(xterm.favourite());
        assertEquals("Xterm", xterm.name());
        assertNull(xterm.entry());
    }

    @Test
    void clickingGoesThroughTheWindowsOfAProgram() {
        List<DockModel.Window> windows = List.of(
                new DockModel.Window(2, "amade-writepad", "Letter", false),
                new DockModel.Window(3, "amade-writepad", "Notes", false));
        DockModel.Program p = DockModel.programs(List.of(), entries, windows).get(0);
        assertEquals(3, DockModel.next(p, 2).id());
        assertEquals(2, DockModel.next(p, 3).id());
        assertEquals(2, DockModel.next(p, 99).id());
    }

    @Test
    void folderDrawerShowsTheNewestFilesWithoutHiddenOnes(@TempDir Path dir) throws Exception {
        Path old = Files.writeString(dir.resolve("old.txt"), "x");
        Path recent = Files.writeString(dir.resolve("recent.pdf"), "x");
        Files.writeString(dir.resolve(".hidden"), "x");
        Files.setLastModifiedTime(old, FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(recent, FileTime.fromMillis(2_000_000));
        assertEquals(List.of(recent, old), HorizonDock.newestFiles(dir, 10));
        assertEquals(List.of(recent), HorizonDock.newestFiles(dir, 1));
        assertEquals(List.of(), HorizonDock.newestFiles(dir.resolve("missing"), 10));
    }

    @Test
    void drawerTilesDropTheAmadeosPrefix() {
        assertEquals("<html><center>Image<br>Viewer</center></html>", HorizonDock.shortName("amadeOS Image Viewer"));
        assertEquals("<html><center>Files</center></html>", HorizonDock.shortName("Files"));
        assertTrue(HorizonDock.shortName("R&D <tool>").contains("R&amp;D &lt;tool&gt;"));
    }
}
