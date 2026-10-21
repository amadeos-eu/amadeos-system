package eu.amadeos.system.packages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PackageWatcherTest {

    @TempDir
    Path dir;

    @Test
    void oneCallbackPerSettledChangeAndOnlyForTheStatusFileOfDpkg() throws Exception {
        Path dpkg = Files.createDirectory(dir.resolve("dpkg"));
        Path lists = Files.createDirectory(dir.resolve("lists"));
        LinkedBlockingQueue<Boolean> calls = new LinkedBlockingQueue<>();
        try (AutoCloseable watch = PackageWatcher.start(List.of(dpkg, lists), 300, () -> calls.add(true))) {
            Files.writeString(dpkg.resolve("lock-frontend"), "");
            assertNull(calls.poll(1, TimeUnit.SECONDS), "dpkg's lock is not a change of the packages");

            // apt-get update writes many lists; one callback after they settle
            for (int i = 0; i < 5; i++) Files.writeString(lists.resolve("deb.debian.org_" + i + "_Packages"), "x");
            assertNotNull(calls.poll(10, TimeUnit.SECONDS));
            assertNull(calls.poll(1, TimeUnit.SECONDS));

            // dpkg replaces its status file
            Files.writeString(dpkg.resolve("status-new"), "Package: x\n");
            Files.move(dpkg.resolve("status-new"), dpkg.resolve("status"));
            assertNotNull(calls.poll(10, TimeUnit.SECONDS));
        }
    }
}
