package eu.amadeos.system.software.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaRuntimeWatchTest {

    @TempDir
    Path home;

    @Test
    void anUnchangedRuntimeIsNotReplaced() throws Exception {
        Files.createDirectories(home.resolve("lib"));
        Files.writeString(home.resolve("lib/modules"), "old");
        assertFalse(new JavaRuntimeWatch(home).replaced());
    }

    @Test
    void aFileMovedInPlaceAsDpkgDoesIsNoticed() throws Exception {
        Files.createDirectories(home.resolve("lib"));
        Files.writeString(home.resolve("lib/modules"), "old");
        JavaRuntimeWatch watch = new JavaRuntimeWatch(home);
        Path unpacked = Files.writeString(home.resolve("lib/modules.dpkg-new"), "new");
        Files.move(unpacked, home.resolve("lib/modules"), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        assertTrue(watch.replaced());
    }

    @Test
    void aRemovedRuntimeIsNoticed() throws Exception {
        Files.createDirectories(home.resolve("lib"));
        Files.writeString(home.resolve("lib/modules"), "old");
        JavaRuntimeWatch watch = new JavaRuntimeWatch(home);
        Files.delete(home.resolve("lib/modules"));
        assertTrue(watch.replaced());
    }

    @Test
    void withoutTheFileThereIsNothingToCompare() throws Exception {
        JavaRuntimeWatch watch = new JavaRuntimeWatch(home);
        Files.createDirectories(home.resolve("lib"));
        Files.writeString(home.resolve("lib/modules"), "new");
        assertFalse(watch.replaced());
    }

    @Test
    void theRunningRuntimeIsNotReplaced() {
        assertFalse(JavaRuntimeWatch.current().replaced());
    }
}
