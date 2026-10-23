package eu.amadeos.system.panel.cde;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import java.util.List;
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
}
