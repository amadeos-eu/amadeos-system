package eu.amadeos.system.base.xdg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApplicationsWatcherTest {

    @TempDir
    Path dir;

    @Test
    void oneCallbackOnceAnInstallationHasSettled() throws Exception {
        Path applications = Files.createDirectory(dir.resolve("applications"));
        LinkedBlockingQueue<Boolean> calls = new LinkedBlockingQueue<>();
        try (AutoCloseable watch = ApplicationsWatcher.start(List.of(applications, dir.resolve("missing")), 300,
                () -> calls.add(true))) {
            // A package brings a few entries at once: one callback after they settle
            for (int i = 0; i < 3; i++) {
                Files.writeString(applications.resolve("app" + i + ".desktop"), "[Desktop Entry]\n");
            }
            assertNotNull(calls.poll(10, TimeUnit.SECONDS));
            assertNull(calls.poll(1, TimeUnit.SECONDS));

            // Removing one is a change too
            Files.delete(applications.resolve("app1.desktop"));
            assertNotNull(calls.poll(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void aDirectoryCreatedLaterIsWatchedToo() throws Exception {
        // A new user has no ~/.local/share/applications yet; mkdir -p makes it, then a desktop entry goes in
        Path applications = dir.resolve("home/.local/share/applications");
        Files.createDirectory(dir.resolve("home"));
        LinkedBlockingQueue<Boolean> calls = new LinkedBlockingQueue<>();
        try (AutoCloseable watch = ApplicationsWatcher.start(List.of(applications), 300, () -> calls.add(true))) {
            // Something else in the home folder is no change of the entries
            Files.writeString(dir.resolve("home/notes.txt"), "x");
            assertNull(calls.poll(1, TimeUnit.SECONDS));

            Files.createDirectories(applications);
            assertNotNull(calls.poll(10, TimeUnit.SECONDS));
            Files.writeString(applications.resolve("backup.desktop"), "[Desktop Entry]\n");
            assertNotNull(calls.poll(10, TimeUnit.SECONDS));
        }
    }
}
