package eu.amadeos.system.software.appstream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStreamReaderTest {
    /** Shaped like Debian's and Ubuntu's DEP-11 files, cut short. */
    static final String DEP11 = """
            ---
            File: DEP-11
            Version: '0.16'
            Origin: debian-trixie-main
            MediaBaseUrl: https://appstream.debian.org/media/pool
            Time: 20251020T000000
            ---
            Type: desktop-application
            ID: org.gimp.GIMP
            Package: gimp
            Name:
              C: GNU Image Manipulation Program
              de: GNU-Bildbearbeitungsprogramm
            Summary:
              C: Create images and edit photographs
              de_DE: Bilder erstellen und Fotos bearbeiten
            Description:
              C: >-
                <p>GIMP is an acronym for GNU Image Manipulation Program.</p>

                <ul><li>painting</li><li>retouching</li></ul>
              de: >-
                <p>GIMP ist ein Akronym für »GNU Image Manipulation Program«.</p>
            Categories:
            - Graphics
            - RasterGraphics
            Url:
              homepage: https://www.gimp.org/
            Icon:
              cached:
              - name: gimp_gimp.png
                width: 48
                height: 48
              - name: gimp_gimp.png
                width: 64
                height: 64
              stock: gimp
            Screenshots:
            - caption:
                C: Editing a photo
              thumbnails:
              - url: org/gimp/2.png
                width: 624
                height: 351
            - default: true
              caption:
                C: Painting in GIMP
                de: Zeichnen in GIMP
              thumbnails:
              - url: org/gimp/1_1248x702.png
                width: 1248
                height: 702
              - url: org/gimp/1_624x351.png
                width: 624
                height: 351
              - url: org/gimp/1_224x126.png
                width: 224
                height: 126
              source-image:
                url: org/gimp/1_orig.png
                width: 1920
                height: 1080
            ---
            Type: console-application
            ID: ripgrep
            Package: ripgrep
            Name:
              C: ripgrep
            ---
            Type: desktop-application
            ID: org.example.Bad
            Package: bad
            Name:
              C: Bad links
            Screenshots:
            - thumbnails:
              - url: file:///etc/passwd
                width: 624
                height: 351
            """;

    @TempDir
    Path dir;

    private List<AppData> read(Locale locale) throws Exception {
        Path icons = dir.resolve("icons");
        Files.createDirectories(icons.resolve("debian-trixie-main/64x64"));
        Files.writeString(icons.resolve("debian-trixie-main/64x64/gimp_gimp.png"), "png");
        List<AppData> apps = new ArrayList<>();
        new AppStreamReader(locale, List.of(dir.resolve("missing"), icons)).read(new StringReader(DEP11), apps::add);
        return apps;
    }

    @Test
    void desktopApplicationsWithTheirTextsIconsAndScreenshots() throws Exception {
        List<AppData> apps = read(Locale.US);
        assertEquals(List.of("gimp", "bad"), apps.stream().map(AppData::pkg).toList());
        AppData gimp = apps.getFirst();
        assertEquals("org.gimp.GIMP", gimp.id());
        assertEquals("GNU Image Manipulation Program", gimp.name());
        assertEquals("Create images and edit photographs", gimp.summary());
        assertTrue(gimp.description().startsWith("<p>GIMP is an acronym"), gimp.description());
        assertTrue(gimp.description().contains("<li>retouching</li>"));
        assertEquals(List.of("Graphics", "RasterGraphics"), gimp.categories());
        assertEquals("https://www.gimp.org/", gimp.homepage());
        assertEquals(Optional.of(dir.resolve("icons/debian-trixie-main/64x64/gimp_gimp.png")), gimp.icon());
        // The default screenshot first, as the thumbnail nearest the pane's width.
        assertEquals(List.of(new AppData.Screenshot("Painting in GIMP",
                        URI.create("https://appstream.debian.org/media/pool/org/gimp/1_624x351.png")),
                new AppData.Screenshot("Editing a photo", URI.create("https://appstream.debian.org/media/pool/org/gimp/2.png"))),
                gimp.screenshots());
        assertEquals(List.of(), apps.get(1).screenshots(), "only web images");
    }

    @Test
    void textsInTheUsersLanguage() throws Exception {
        AppData gimp = read(Locale.GERMANY).getFirst();
        assertEquals("GNU-Bildbearbeitungsprogramm", gimp.name());
        assertEquals("Bilder erstellen und Fotos bearbeiten", gimp.summary());
        assertTrue(gimp.description().startsWith("<p>GIMP ist"));
        assertEquals("Zeichnen in GIMP", gimp.screenshots().getFirst().caption());
        assertEquals("Create images and edit photographs", read(Locale.FRANCE).getFirst().summary());
    }

    @Test
    void theFilesOfTheListsDirectory() throws Exception {
        Path lists = dir.resolve("lists");
        Files.createDirectories(lists);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(
                lists.resolve("deb.debian.org_debian_dists_trixie_main_dep11_Components-amd64.yml.gz")))) {
            out.write(DEP11.getBytes(StandardCharsets.UTF_8));
        }
        Files.writeString(lists.resolve("broken_dep11_Components-amd64.yml.gz"), "not gzip");
        Map<String, AppData> apps = AppStream.read(lists, List.of(), Locale.US);
        assertEquals(java.util.Set.of("gimp", "bad"), apps.keySet());
        assertEquals(Optional.empty(), apps.get("gimp").icon(), "no icons fetched");
        assertEquals(Map.of(), AppStream.read(dir.resolve("none"), List.of(), Locale.US));
    }
}
