package eu.amadeos.system.panel.cde;

import eu.amadeos.system.base.xdg.XdgCategory;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawerEntriesTest {

    private static final Set<String> FIXED = Set.of("amadeos-settings", "amadeos-style", "amadeos-sysinfo", "xterm");

    private static XdgDesktopEntry entry(String exec, boolean terminal) {
        return new XdgDesktopEntry("test.desktop", "Test", "", "", "window", exec, terminal,
                List.of("System"), List.of(), false, false, List.of(), List.of(), "", null);
    }

    @Test
    void theProgramIsTheFirstWordWithoutItsDirectory() {
        assertEquals("amadeos-sysinfo", AmadeDesktopPanel.programOf(entry("/usr/bin/amadeos-sysinfo %F", false)));
    }

    @Test
    void anEntryThatOnlyStartsAFixedProgramIsLeftOut() {
        assertTrue(AmadeDesktopPanel.startsOnly(entry("xterm", false), FIXED));
        assertTrue(AmadeDesktopPanel.startsOnly(entry("/usr/bin/amadeos-settings %U", false), FIXED));
    }

    @Test
    void aProgramOfItsOwnStays() {
        // A fixed program with arguments of its own, a script run in a terminal, any other program
        assertFalse(AmadeDesktopPanel.startsOnly(entry("xterm -title Backup -e /home/alex/bin/backup.sh", false), FIXED));
        assertFalse(AmadeDesktopPanel.startsOnly(entry("xterm", true), FIXED));
        assertFalse(AmadeDesktopPanel.startsOnly(entry("uxterm", false), FIXED));
        assertFalse(AmadeDesktopPanel.startsOnly(entry("amadeos-installer", false), FIXED));
    }

    private static XdgDesktopEntry app(String id, String... categories) {
        return new XdgDesktopEntry(id, id, "", "", "window", id, false,
                List.of(categories), List.of(), false, false, List.of(), List.of(), "", null);
    }

    @Test
    void theEditorButtonStartsTheTextEditorNotTheWordProcessor() {
        XdgDesktopEntry writepad = app("amade-writepad.desktop", "Office", "WordProcessor");
        XdgDesktopEntry pad = app("amade-pad.desktop", "Utility", "TextEditor");
        XdgDesktopEntry vim = app("vim.desktop", "Utility", "TextEditor");
        List<XdgDesktopEntry> drawer = List.of(writepad, pad, vim);
        assertEquals(pad, AmadeDesktopPanel.primaryApp(drawer, XdgCategory.TEXT_EDITOR, Optional.empty()));
        // The default program for plain text wins when it is in the drawer
        assertEquals(vim, AmadeDesktopPanel.primaryApp(drawer, XdgCategory.TEXT_EDITOR, Optional.of("vim.desktop")));
        assertEquals(pad, AmadeDesktopPanel.primaryApp(drawer, XdgCategory.TEXT_EDITOR, Optional.of("gone.desktop")));
        // Only word processors: the first one
        assertEquals(writepad, AmadeDesktopPanel.primaryApp(List.of(writepad), XdgCategory.TEXT_EDITOR, Optional.empty()));
        // Other drawers keep their first entry
        assertEquals(writepad, AmadeDesktopPanel.primaryApp(drawer, XdgCategory.MAIL, Optional.empty()));
    }
}
