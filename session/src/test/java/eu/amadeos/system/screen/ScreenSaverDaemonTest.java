package eu.amadeos.system.screen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ScreenSaverDaemonTest {
    @TempDir
    Path dir;

    /** Idle time and lock state set by the test instead of the X server. */
    private static final class FakeMonitor implements IdleMonitor {
        long idle;
        boolean locked;

        @Override
        public long idleMillis() {
            return idle;
        }

        @Override
        public boolean locked() {
            return locked;
        }

        @Override
        public void close() {
        }
    }

    @Test
    void showsSaverLocksAndFollowsSettingChanges() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ScreenSettingsStore store = new ScreenSettingsStore(dir.resolve("screen.conf"));
        store.save(new ScreenSettings(true, 1, true, 2, "blank"));
        FakeMonitor monitor = new FakeMonitor();
        AtomicInteger locks = new AtomicInteger();
        ScreenSaverDaemon daemon = new ScreenSaverDaemon(monitor, store, locks::incrementAndGet);

        monitor.idle = 30_000;
        daemon.poll();
        assertFalse(daemon.saverVisible());

        monitor.idle = 60_000;
        daemon.poll();
        assertTrue(daemon.saverVisible());

        monitor.idle = 50; // a key was pressed
        daemon.poll();
        assertFalse(daemon.saverVisible());

        monitor.idle = 120_000;
        daemon.poll();
        assertEquals(1, locks.get());
        assertFalse(daemon.saverVisible());

        // The Settings switch the lock off; the daemon reads the new file without a restart.
        monitor.idle = 10;
        daemon.poll();
        store.save(new ScreenSettings(true, 1, false, 2, "blank"));
        assertTrue(dir.resolve("screen.conf").toFile().setLastModified(System.currentTimeMillis() + 5000));
        monitor.idle = 180_000;
        daemon.poll();
        assertEquals(1, locks.get());
        assertTrue(daemon.saverVisible());
        monitor.idle = 0;
        daemon.poll();
        assertFalse(daemon.saverVisible());
    }
}
