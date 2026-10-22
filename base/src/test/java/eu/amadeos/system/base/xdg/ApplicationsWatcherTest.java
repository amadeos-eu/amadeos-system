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
}
