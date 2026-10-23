package eu.amadeos.system.base.startup;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.base.xdg.XdgDesktopEntryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupTest {
    @TempDir
    Path dir;

    @Test
    void disabledScriptsAreKept() throws Exception {
        StartupSettings settings = new StartupSettings(dir.resolve(StartupSettings.FILE_NAME));
        assertEquals(Set.of(), settings.disabled(), "no file: everything starts");
        settings.saveDisabled(Set.of("40-amadeos-clipboard", "20-extra"));
        assertEquals(Set.of("20-extra", "40-amadeos-clipboard"), settings.disabled());
        // amadeos-session reads the line with grep and cut, so it must be one plain line.
        assertTrue(Files.readAllLines(settings.file()).contains("disabled=20-extra,40-amadeos-clipboard"));
    }

    @Test
    void servicesAreTheExecutableScripts() throws Exception {
        Path services = Files.createDirectory(dir.resolve("autostart.d"));
        for (String name : List.of("50-screen", "10-input")) {
            Path script = Files.writeString(services.resolve(name), "#!/bin/sh\n");
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        Files.writeString(services.resolve("README"), "not a script");
        assertEquals(List.of("10-input", "50-screen"), DesktopServices.in(services));
        assertEquals(List.of(), DesktopServices.in(dir.resolve("missing")));
    }

    @Test
    void programsAreAddedAndRemovedAsDesktopEntries() throws Exception {
        Path apps = Files.createDirectory(dir.resolve("applications"));
        Path pad = Files.writeString(apps.resolve("amade-pad.desktop"),
                "[Desktop Entry]\nType=Application\nName=Text Editor\nExec=amade-pad %F\n");
        XdgDesktopEntry entry = XdgDesktopEntryParser.parse(pad).orElseThrow();
        UserAutostart autostart = new UserAutostart(dir.resolve("autostart"));
        assertEquals(List.of(), autostart.entries());

        autostart.add(entry);
        List<XdgDesktopEntry> entries = autostart.entries();
        assertEquals(List.of("Text Editor"), entries.stream().map(XdgDesktopEntry::name).toList());
        assertTrue(Files.exists(pad), "the installed entry stays");

        autostart.remove(entries.get(0));
        assertEquals(List.of(), autostart.entries());
        assertTrue(Files.exists(pad));
    }

    @Test
    void hiddenEntriesAreSwitchedOff() throws Exception {
        Path autostartDir = Files.createDirectory(dir.resolve("autostart"));
        Files.writeString(autostartDir.resolve("off.desktop"), "[Desktop Entry]\nName=Off\nExec=off\nHidden=true\n");
        assertEquals(List.of(), new UserAutostart(autostartDir).entries());
    }
}
