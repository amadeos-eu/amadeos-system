package eu.amadeos.system.software.appstream;

import eu.amadeos.system.packages.StampedCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppStreamCacheTest {
    @TempDir
    Path dir;

    @Test
    void theApplicationsAreReadOnceUntilTheListsChange() throws Exception {
        Path lists = Files.createDirectories(dir.resolve("lists"));
        AppData gimp = new AppData("org.gimp.GIMP", "gimp", "GIMP", "Bilder bearbeiten", "<p>Ein Programm</p>",
                List.of("Graphics", "2DGraphics"), Optional.of(Path.of("/var/lib/swcatalog/icons/x/64x64/gimp.png")),
                "https://www.gimp.org", List.of(new AppData.Screenshot("Das Fenster",
                URI.create("https://media.example/gimp.png"))));
        AppData plain = new AppData("amade-pad", "amade-pad", "Pad", "", "", List.of(), Optional.empty(), "",
                List.of());
        int[] reads = new int[1];
        AppStreamSource source = () -> {
            reads[0]++;
            return Map.of("gimp", gimp, "amade-pad", plain);
        };
        AppStreamCache cache = new AppStreamCache(source,
                new StampedCache(dir.resolve("appstream-de"), AppStreamCache.FORMAT + " de", List.of(lists)));
        Map<String, AppData> first = cache.applications();
        assertEquals(Map.of("gimp", gimp, "amade-pad", plain), cache.applications());
        assertEquals(first, cache.applications());
        assertEquals(1, reads[0]);
        Files.setLastModifiedTime(lists, FileTime.fromMillis(Files.getLastModifiedTime(lists).toMillis() + 5000));
        cache.applications();
        assertEquals(2, reads[0], "new lists: read anew");
    }
}
