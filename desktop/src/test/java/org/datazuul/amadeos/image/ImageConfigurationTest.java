package org.datazuul.amadeos.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks on the live-build configuration that are easy to break by hand.
 */
class ImageConfigurationTest {
    private static final Path HOOKS = Path.of("config/hooks");

    /**
     * live-build 3.x (Ubuntu hosts) runs config/hooks/*.chroot, current Debian live-build runs
     * config/hooks/normal/*.chroot. Both copies must stay identical, or the image depends on
     * which live-build version built it.
     */
    @Test
    void hookCopiesForBothLiveBuildLayoutsAreIdentical() throws IOException {
        List<Path> topLevel = chrootHooks(HOOKS);
        List<Path> normal = chrootHooks(HOOKS.resolve("normal"));
        assertFalse(topLevel.isEmpty(), "expected hooks in " + HOOKS);
        assertEquals(names(topLevel), names(normal), "both hook directories must contain the same hooks");
        for (Path hook : topLevel) {
            Path twin = HOOKS.resolve("normal").resolve(hook.getFileName());
            assertEquals(Files.readString(hook), Files.readString(twin),
                    hook.getFileName() + " differs between config/hooks and config/hooks/normal");
        }
    }

    @Test
    void installedScriptsAreExecutable() throws IOException {
        try (Stream<Path> scripts = Files.list(Path.of("config/includes.chroot/usr/local/bin"))) {
            for (Path script : scripts.toList()) {
                assertTrue(Files.isExecutable(script), script + " must be executable (git update-index --chmod=+x)");
            }
        }
    }

    private static List<Path> chrootHooks(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".chroot")).sorted().toList();
        }
    }

    private static List<String> names(List<Path> paths) {
        return paths.stream().map(p -> p.getFileName().toString()).toList();
    }
}
