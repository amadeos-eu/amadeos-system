package eu.amadeos.system.packages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StampedCacheTest {
    @TempDir
    Path dir;

    @Test
    void aValueIsKeptWithItsStamp() throws Exception {
        Path source = Files.writeString(dir.resolve("status"), "");
        StampedCache cache = new StampedCache(dir.resolve("sub/value"), "v1", List.of(source, dir.resolve("missing")));
        String stamp = cache.stamp();
        assertTrue(cache.load(stamp, in -> in.readUTF()).isEmpty(), "nothing kept yet");
        cache.save(stamp, "hello " + "x".repeat(100_000), (out, v) -> StampedCache.writeString(out, v));
        assertEquals(Optional.of("hello " + "x".repeat(100_000)), cache.load(cache.stamp(), StampedCache::readString));

        Files.setLastModifiedTime(source, FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis() + 5000));
        assertTrue(cache.load(cache.stamp(), StampedCache::readString).isEmpty(), "the source changed");
        StampedCache other = new StampedCache(dir.resolve("sub/value"), "v2", List.of(source, dir.resolve("missing")));
        assertTrue(other.load(other.stamp(), StampedCache::readString).isEmpty(), "another format");
    }

    @Test
    void aBrokenFileOnlyMeansReadingAgain() throws Exception {
        Path file = Files.writeString(dir.resolve("value"), "garbage");
        StampedCache cache = new StampedCache(file, "v1", List.of());
        assertTrue(cache.load(cache.stamp(), StampedCache::readString).isEmpty());
    }

    @Test
    void theCatalogSurvivesTheRoundTrip() throws Exception {
        List<CatalogEntry> entries = List.of(
                new CatalogEntry("gimp", "GNU Image Manipulation Program", "graphics", Category.APPLICATION, "",
                        "3.0.4-1", false),
                new CatalogEntry("libc6", "GNU C Library", "libs", Category.LIBRARY, "2.41-12", "2.41-12", true));
        StampedCache cache = new StampedCache(dir.resolve("catalog"), "v1", List.of());
        cache.save(cache.stamp(), entries, AptPackageService::encodeCatalog);
        assertEquals(Optional.of(entries), cache.load(cache.stamp(), AptPackageService::decodeCatalog));
    }

    @Test
    void extendedStatesNameTheAutomaticPackages() {
        assertEquals(java.util.Set.of("libc6", "libgtk-3-0t64"), Apt.parseExtendedStates("""
                Package: libc6
                Architecture: amd64
                Auto-Installed: 1

                Package: gimp
                Architecture: amd64
                Auto-Installed: 0

                Package: libgtk-3-0t64
                Architecture: amd64
                Auto-Installed: 1
                """));
    }
}
