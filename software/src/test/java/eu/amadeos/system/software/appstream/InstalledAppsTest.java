package eu.amadeos.system.software.appstream;

import eu.amadeos.system.packages.Categories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstalledAppsTest {
    @TempDir
    Path root;
    private Path applications;
    private Path metainfo;
    private Path icons;
    private Path pixmaps;
    private final List<Categories.Owned> owned = new ArrayList<>();

    @BeforeEach
    void layout() throws Exception {
        applications = Files.createDirectories(root.resolve("applications"));
        metainfo = Files.createDirectories(root.resolve("metainfo"));
        icons = Files.createDirectories(root.resolve("hicolor"));
        pixmaps = Files.createDirectories(root.resolve("pixmaps"));
    }

    private Path file(Path dir, String name, String text, String pkg) throws Exception {
        Path file = Files.writeString(dir.resolve(name), text);
        owned.add(new Categories.Owned(pkg, file.toString()));
        return file;
    }

    private Path icon(String size, String name) throws Exception {
        Path dir = Files.createDirectories(icons.resolve(size).resolve("apps"));
        return Files.write(dir.resolve(name + ".png"), new byte[]{1});
    }

    private Map<String, AppData> read(Locale locale) {
        InstalledApps.Owners owners = pattern -> owned.stream()
                .filter(o -> o.path().startsWith(pattern.substring(0, pattern.indexOf('*')))).toList();
        return new InstalledApps(locale, icons, pixmaps).read(owners, applications, metainfo);
    }

    @Test
    void aDesktopEntryGivesNameSummaryIconAndCategoriesInTheLanguage() throws Exception {
        file(applications, "amade-mines-game.desktop", """
                [Desktop Entry]
                Type=Application
                Name=amadeOS Mines
                Name[de]=amadeOS Minen
                Comment=Find all mines on the field without stepping on one
                Comment[de]=Alle Minen im Feld finden, ohne auf eine zu treten
                Icon=amade-mines-game
                Categories=Game;LogicGame;

                [Desktop Action New]
                Name=Other name
                """, "amade-mines-game");
        Path icon48 = icon("48x48", "amade-mines-game");
        icon("32x32", "amade-mines-game");

        AppData mines = read(Locale.GERMANY).get("amade-mines-game");
        assertEquals("amadeOS Minen", mines.name());
        assertEquals("Alle Minen im Feld finden, ohne auf eine zu treten", mines.summary());
        assertEquals(List.of("Game", "LogicGame"), mines.categories());
        assertEquals(Optional.of(icon48), mines.icon());
        assertEquals("amade-mines-game", mines.id());
        assertEquals("amadeOS Mines", read(Locale.ENGLISH).get("amade-mines-game").name());
    }

    @Test
    void hiddenEntriesAndOtherDirectoriesAreLeftOut() throws Exception {
        file(applications, "helper.desktop", "[Desktop Entry]\nType=Application\nName=Helper\nNoDisplay=true\n",
                "helper");
        file(applications, "link.desktop", "[Desktop Entry]\nType=Link\nName=Web\n", "link");
        owned.add(new Categories.Owned("elsewhere", "/usr/share/other/x.desktop"));
        assertTrue(read(Locale.ENGLISH).isEmpty());
    }

    @Test
    void iconsComeFromAbsolutePathsOrPixmapsAndNeverAsSvg() throws Exception {
        InstalledApps apps = new InstalledApps(Locale.ENGLISH, icons, pixmaps);
        Path pixmap = Files.write(pixmaps.resolve("old.png"), new byte[]{1});
        assertEquals(Optional.of(pixmap), apps.icon("old"));
        assertEquals(Optional.of(pixmap), apps.icon(pixmap.toString()));
        assertEquals(Optional.empty(), apps.icon("/usr/share/icons/x.svg"));
        assertEquals(Optional.empty(), apps.icon("missing"));
        assertEquals(Optional.empty(), apps.icon(""));
    }

    @Test
    void metainfoAddsDescriptionHomepageAndScreenshots() throws Exception {
        file(applications, "amade-pad.desktop", """
                [Desktop Entry]
                Type=Application
                Name=amadeOS Text Editor
                Comment=Edit text files
                Icon=amade-pad
                Categories=Utility;TextEditor;
                """, "amade-pad");
        Path padIcon = icon("48x48", "amade-pad");
        file(metainfo, "eu.amadeos.pad.metainfo.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <component type="desktop-application">
                  <id>eu.amadeos.pad</id>
                  <name>amadeOS Text Editor</name>
                  <summary>Edit text files with tabs</summary>
                  <summary xml:lang="de">Textdateien in Tabs bearbeiten</summary>
                  <description>
                    <p>A fast tabbed editor &amp; more.</p>
                    <p xml:lang="de">Ein schneller Editor mit Tabs &amp; mehr.</p>
                    <ul>
                      <li>Line numbers</li>
                      <li xml:lang="de">Zeilennummern</li>
                    </ul>
                  </description>
                  <url type="homepage">https://github.com/datazuul/amade-pad</url>
                  <launchable type="desktop-id">amade-pad.desktop</launchable>
                  <screenshots>
                    <screenshot>
                      <image>https://example.org/second.png</image>
                    </screenshot>
                    <screenshot type="default">
                      <caption>The editor</caption>
                      <image>https://raw.githubusercontent.com/datazuul/amade-pad/main/docs/screenshot.png</image>
                    </screenshot>
                    <screenshot>
                      <image>file:///etc/passwd</image>
                    </screenshot>
                  </screenshots>
                </component>
                """, "amade-pad");

        AppData english = read(Locale.ENGLISH).get("amade-pad");
        assertEquals("eu.amadeos.pad", english.id());
        assertEquals("Edit text files with tabs", english.summary());
        assertEquals("<p>A fast tabbed editor &amp; more.</p><ul><li>Line numbers</li></ul>", english.description());
        assertEquals("https://github.com/datazuul/amade-pad", english.homepage());
        assertEquals(List.of("Utility", "TextEditor"), english.categories());
        assertEquals(Optional.of(padIcon), english.icon());
        assertEquals(List.of(new AppData.Screenshot("The editor",
                        URI.create("https://raw.githubusercontent.com/datazuul/amade-pad/main/docs/screenshot.png")),
                new AppData.Screenshot("", URI.create("https://example.org/second.png"))), english.screenshots());

        AppData german = read(Locale.GERMAN).get("amade-pad");
        assertEquals("Textdateien in Tabs bearbeiten", german.summary());
        assertEquals("<p>Ein schneller Editor mit Tabs &amp; mehr.</p><ul><li>Zeilennummern</li></ul>",
                german.description());
    }

    @Test
    void metainfoOfOtherComponentsOrWithADoctypeIsLeftOut() throws Exception {
        file(metainfo, "font.metainfo.xml", "<component type=\"font\"><id>f</id><name>F</name></component>",
                "fonts-f");
        file(metainfo, "evil.metainfo.xml", """
                <?xml version="1.0"?>
                <!DOCTYPE component [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <component type="desktop-application"><id>e</id><name>&x;</name></component>
                """, "evil");
        Map<String, AppData> apps = read(Locale.ENGLISH);
        assertFalse(apps.containsKey("fonts-f"));
        assertFalse(apps.containsKey("evil"));
    }
}
