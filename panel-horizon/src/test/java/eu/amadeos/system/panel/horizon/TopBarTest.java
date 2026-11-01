package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

class TopBarTest {
    private static XdgDesktopEntry entry(String id, String name, String exec) {
        return new XdgDesktopEntry(id, name, "", "", "icon", exec, false, List.of("Utility"), List.of(),
                false, false, List.of(), List.of(), "", Path.of("/usr/share/applications/" + id));
    }

    private final List<XdgDesktopEntry> entries = List.of(
            entry("amade-pad.desktop", "Pad", "/usr/bin/amade-pad %F"),
            entry("amade-writepad.desktop", "WritePad", "/usr/bin/amade-writepad %F"),
            entry("firefox-esr.desktop", "Firefox ESR", "/usr/lib/firefox-esr/firefox-esr %u"));

    @Test
    void programNameComesFromTheDesktopEntry() {
        assertEquals("Firefox ESR", ProgramNames.nameOf("Firefox-esr", "Some page", entries));
        assertEquals("Pad", ProgramNames.nameOf("amade-pad", "notes.txt", entries));
        assertEquals("WritePad", ProgramNames.nameOf("eu-amadeos-apps-writepad-WritePadApplication", "Letter", entries));
        assertEquals("Xterm", ProgramNames.nameOf("xterm", "bash", entries));
        assertEquals("System Information",
                ProgramNames.nameOf("eu-amadeos-system-sysinfo-bootstrap-AmadeSysinfoApplication", "System Information", entries));
        assertEquals("A title", ProgramNames.nameOf("", "A title", entries));
    }

    @Test
    void clockShowsWeekdayDateAndTime() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 1, 10, 15);
        assertEquals("Sun 1 Nov   10:15 AM", HorizonTopBar.clockText(t, Locale.US).replace(' ', ' '));
        assertTrue(HorizonTopBar.clockText(t, Locale.GERMANY).endsWith("10:15"));
    }

    @Test
    void everyLanguageHasEveryText() {
        ResourceBundle en = ResourceBundle.getBundle(TopBarTexts.BUNDLE, Locale.ROOT);
        for (String lang : new String[]{"de", "fr", "es", "it"}) {
            ResourceBundle other = ResourceBundle.getBundle(TopBarTexts.BUNDLE, Locale.of(lang));
            for (String key : en.keySet()) {
                assertTrue(other.containsKey(key) && !other.getString(key).equals(en.getString(key))
                        || key.equals("system.software"), lang + " " + key);
            }
        }
    }
}
