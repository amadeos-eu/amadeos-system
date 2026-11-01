package eu.amadeos.system.base.xdg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MimeAppsTest {
    @TempDir
    Path dir;

    private MimeApps apps() throws Exception {
        Path system = dir.resolve("etc-mimeapps.list");
        Files.writeString(system, """
                # amadeOS defaults
                [Default Applications]
                x-scheme-handler/http=firefox-esr.desktop
                image/png=missing.desktop;amade-imageviewer.desktop;
                application/pdf=amade-pdfviewer.desktop
                """, StandardCharsets.UTF_8);
        Set<String> installed = Set.of("firefox-esr.desktop", "amade-imageviewer.desktop", "amade-pdfviewer.desktop",
                "amade-imageeditor.desktop", "chromium.desktop");
        return new MimeApps(dir.resolve("home/.config/mimeapps.list"), List.of(dir.resolve("none.list"), system),
                installed::contains);
    }

    @Test
    void theSystemFileGivesTheDefaultsAndSkipsMissingPrograms() throws Exception {
        MimeApps apps = apps();
        assertEquals(Optional.of("firefox-esr.desktop"), apps.defaultFor("x-scheme-handler/http"));
        assertEquals(Optional.of("amade-imageviewer.desktop"), apps.defaultFor("image/png"));
        assertEquals(Optional.empty(), apps.defaultFor("text/plain"));
        assertFalse(apps.userChoice("image/png"));
    }

    @Test
    void theUsersChoiceWinsAndResetRemovesIt() throws Exception {
        MimeApps apps = apps();
        apps.setDefault("amade-imageeditor.desktop", List.of("image/png", "image/jpeg"));
        assertEquals(Optional.of("amade-imageeditor.desktop"), apps.defaultFor("image/png"));
        assertEquals(Optional.of("amade-imageviewer.desktop"), apps.systemDefaultFor("image/png"));
        assertTrue(apps.userChoice("image/jpeg"));
        apps.reset(List.of("image/png", "image/jpeg"));
        assertEquals(Optional.of("amade-imageviewer.desktop"), apps.defaultFor("image/png"));
        assertEquals("[Default Applications]\n", Files.readString(apps.userFile()));
    }

    @Test
    void keepsTheOtherSectionsAndLinesOfTheUsersFile() throws Exception {
        MimeApps apps = apps();
        Files.createDirectories(apps.userFile().getParent());
        Files.writeString(apps.userFile(), """
                [Added Associations]
                text/plain=amade-pad.desktop;

                [Default Applications]
                x-scheme-handler/http=firefox-esr.desktop
                text/calendar=amade-calendar.desktop

                [Removed Associations]
                image/png=gimp.desktop;
                """, StandardCharsets.UTF_8);
        apps.setDefault("chromium.desktop", List.of("x-scheme-handler/http", "x-scheme-handler/https"));
        assertEquals("""
                [Added Associations]
                text/plain=amade-pad.desktop;

                [Default Applications]
                x-scheme-handler/http=chromium.desktop;
                text/calendar=amade-calendar.desktop
                x-scheme-handler/https=chromium.desktop;

                [Removed Associations]
                image/png=gimp.desktop;
                """, Files.readString(apps.userFile()));
        assertEquals(Optional.of("chromium.desktop"), apps.defaultFor("x-scheme-handler/https"));
    }
}
