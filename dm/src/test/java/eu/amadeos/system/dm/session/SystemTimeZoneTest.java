package eu.amadeos.system.dm.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SystemTimeZoneTest {
    private static final String TABLE = """
            # tzdb timezone descriptions
            #country-
            #codes\tcoordinates\tTZ\tcomments
            DE,DK,NO,SE,SJ\t+5230+01322\tEurope/Berlin\tmost of Germany
            AT\t+4813+01620\tEurope/Vienna
            AR\t-3436-05827\tAmerica/Argentina/Buenos_Aires\tBuenos Aires (BA, CF)

            broken line
            """;

    @TempDir
    Path root;
    private SystemTimeZone zones;

    @BeforeEach
    void setUp() throws IOException {
        Path zoneinfo = root.resolve(SystemTimeZone.ZONEINFO);
        for (String zone : List.of("UTC", "Europe/Berlin", "Europe/Vienna", "America/Argentina/Buenos_Aires")) {
            Path file = zoneinfo.resolve(zone);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "TZif");
        }
        Files.writeString(zoneinfo.resolve(SystemTimeZone.TABLE), TABLE);
        Files.createDirectories(root.resolve("etc"));
        zones = new SystemTimeZone(root);
    }

    @Test
    void theChoiceIsUtcAndTheZonesOfTheTableInOrder() {
        assertEquals(List.of("UTC", "America/Argentina/Buenos_Aires", "Europe/Berlin", "Europe/Vienna"),
                zones.available());
        assertTrue(zones.isKnown("Europe/Berlin"));
        assertFalse(zones.isKnown("Europe"), "a directory is not a zone");
        assertFalse(zones.isKnown("Mars/Olympus"));
        assertFalse(zones.isKnown("../../etc/passwd"));
        assertFalse(zones.isKnown(null));
    }

    @Test
    void withoutTheTableTheJdkListIsUsed() throws IOException {
        Files.delete(root.resolve(SystemTimeZone.ZONEINFO).resolve(SystemTimeZone.TABLE));
        List<String> available = zones.available();
        assertEquals("UTC", available.get(0));
        assertTrue(available.contains("Europe/Berlin"));
        assertTrue(available.stream().noneMatch(z -> z.startsWith("Etc/")));
        assertTrue(available.stream().skip(1).allMatch(z -> z.contains("/")), "only Region/City names");
    }

    @Test
    void settingTheZoneLinksLocaltimeAndWritesTimezone() throws IOException {
        assertEquals(Optional.empty(), zones.current(), "no /etc/localtime yet");

        zones.set("Europe/Vienna");
        Path localtime = root.resolve("etc/localtime");
        assertTrue(Files.isSymbolicLink(localtime));
        assertEquals(Path.of("/usr/share/zoneinfo/Europe/Vienna"), Files.readSymbolicLink(localtime));
        assertEquals("Europe/Vienna\n", Files.readString(root.resolve("etc/timezone")));
        assertEquals(Optional.of("Europe/Vienna"), zones.current());

        zones.set("Europe/Berlin");
        assertEquals(Optional.of("Europe/Berlin"), zones.current(), "the link is replaced");
        assertFalse(Files.exists(root.resolve("etc/localtime.amadeos-dm")));
    }

    @Test
    void aCopiedLocaltimeFileIsReplacedByTheLink() throws IOException {
        Files.writeString(root.resolve("etc/localtime"), "TZif");
        assertEquals(Optional.empty(), zones.current(), "a copied file names no zone");
        zones.set("Europe/Berlin");
        assertEquals(Optional.of("Europe/Berlin"), zones.current());
    }

    @Test
    void relativeLinksAsSystemdWritesThemAreUnderstood() throws IOException {
        Files.createSymbolicLink(root.resolve("etc/localtime"), Path.of("../usr/share/zoneinfo/Europe/Vienna"));
        assertEquals(Optional.of("Europe/Vienna"), zones.current());
    }

    @Test
    void linksOutsideZoneinfoNameNoZone() throws IOException {
        Files.createSymbolicLink(root.resolve("etc/localtime"), Path.of("/etc/passwd"));
        assertEquals(Optional.empty(), zones.current());
    }

    @Test
    void unknownZonesAreRefused() {
        assertThrows(IOException.class, () -> zones.set("Mars/Olympus"));
        assertThrows(IOException.class, () -> zones.set("../../tmp/x"));
        assertThrows(IOException.class, () -> zones.set(""));
        assertFalse(Files.exists(root.resolve("etc/timezone")));
    }
}
