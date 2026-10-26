package eu.amadeos.system.panel.cde.activity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import eu.amadeos.system.panel.cde.PanelTexts;
import eu.amadeos.system.panel.cde.activity.ActivityLight.Dash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityLightTest {
    private static final String DISKSTATS = """
               7       0 loop0 120 0 2400 10 0 0 0 0 0 20 10 0 0 0 0
               8       0 sda 1000 10 20000 500 400 20 8000 300 0 700 800 0 0 0 0
               8       1 sda1 900 10 18000 450 400 20 8000 300 0 650 750 0 0 0 0
             259       0 nvme0n1 50 0 100 5 10 0 300 2 0 7 7 0 0 0 0
             259       1 nvme0n1p1 50 0 100 5 10 0 300 2 0 7 7 0 0 0 0
             253       0 dm-0 900 0 18000 450 400 0 8000 300 0 650 750 0 0 0 0
              11       0 sr0 5 0 40 1 0 0 0 0 0 1 1 0 0 0 0
            """;
    private static final String NETDEV = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 999999     100    0    0    0     0          0         0   999999     100    0    0    0     0       0          0
              eth0:1500000    1200    0    0    0     0          0         0    250000     900    0    0    0     0       0          0
            wlan0: 2000 10 0 0 0 0 0 0 500 5 0 0 0 0 0 0
            """;

    @TempDir
    Path proc;

    @BeforeEach
    @AfterEach
    void english() {
        PanelTexts.use(Locale.ENGLISH);
    }

    @Test
    void countsWholeDisksOnly() {
        // sda (20000 + 8000 sectors) and nvme0n1 (100 + 300); no partitions, loop, device mapper or CD drive
        assertEquals((20000 + 8000 + 100 + 300) * 512L, ActivityCounters.diskBytes(DISKSTATS));
    }

    @Test
    void countsAllInterfacesButLoopback() {
        assertEquals(1_500_000 + 250_000 + 2000 + 500, ActivityCounters.networkBytes(NETDEV));
    }

    @Test
    void readsTheCountersAndFindsDpkg() throws Exception {
        Files.writeString(proc.resolve("diskstats"), DISKSTATS);
        Files.createDirectories(proc.resolve("net"));
        Files.writeString(proc.resolve("net/dev"), NETDEV);
        Files.createDirectories(proc.resolve("1"));
        Files.writeString(proc.resolve("1/comm"), "systemd\n");
        ActivityCounters counters = new ActivityCounters(proc);
        assertEquals(ActivityCounters.diskBytes(DISKSTATS), counters.diskBytes());
        assertEquals(ActivityCounters.networkBytes(NETDEV), counters.networkBytes());
        assertFalse(counters.installerRunning());

        Files.createDirectories(proc.resolve("4711"));
        Files.writeString(proc.resolve("4711/comm"), "dpkg\n");
        assertTrue(counters.installerRunning());
    }

    @Test
    void findsResticOnlyWhileItBacksUp() throws Exception {
        ActivityCounters counters = new ActivityCounters(proc);
        Files.createDirectories(proc.resolve("900"));
        Files.writeString(proc.resolve("900/comm"), "restic\n");
        Files.writeString(proc.resolve("900/cmdline"), "restic\0--repo\0/media/ralf/BACKUP/amadeos-backup/home\0snapshots\0--json\0");
        assertEquals(ActivityCounters.Processes.NONE, counters.processes(), "a look at the backups is not a backup");
        Files.writeString(proc.resolve("900/cmdline"), "restic\0--repo\0/media/ralf/BACKUP/amadeos-backup/home\0backup\0--json\0/home/ralf\0");
        assertEquals(new ActivityCounters.Processes(false, true), counters.processes());
        Files.createDirectories(proc.resolve("901"));
        Files.writeString(proc.resolve("901/comm"), "dpkg\n");
        assertEquals(new ActivityCounters.Processes(true, true), counters.processes());
    }

    @Test
    void aVioletChaserRunsTheOtherWayWhileABackupRuns() {
        ActivityLight light = new ActivityLight(new ActivityCounters(proc));
        light.tick(50L << 20, 0, false, true);
        assertEquals(Dash.BACKUP, light.dashes()[ActivityLight.DASHES - 1], "comes in on the right");
        for (int step = 0; step < 4; step++) {
            long violet = Arrays.stream(light.dashes()).filter(d -> d == Dash.BACKUP).count();
            assertTrue(violet >= 1 && violet <= ActivityLight.CHASER, "a short run of violet dashes, no disk meter");
            light.tick(50L << 20, 0, false, true);
        }
        assertEquals("<html>System Activity &amp; Status<br>Backing up…</html>", light.describe());
        light.tick(0, 0, true, true);
        assertTrue(Arrays.stream(light.dashes()).noneMatch(d -> d == Dash.BACKUP), "installing goes first");
    }

    @Test
    void withoutProcEverythingReadsZero() {
        ActivityCounters counters = new ActivityCounters(proc.resolve("missing"));
        assertEquals(0, counters.diskBytes());
        assertEquals(0, counters.networkBytes());
        assertFalse(counters.installerRunning());
    }

    @Test
    void moreBytesLightMoreDashes() {
        assertEquals(0, ActivityLight.level(10_000, ActivityLight.DISK_STEPS), "journal trickle stays dark");
        assertEquals(1, ActivityLight.level(100_000, ActivityLight.DISK_STEPS));
        assertEquals(5, ActivityLight.level(200L << 20, ActivityLight.DISK_STEPS));
        assertEquals(1, ActivityLight.level(5_000, ActivityLight.NETWORK_STEPS));
        assertEquals(3, ActivityLight.level(1L << 20, ActivityLight.NETWORK_STEPS));
    }

    @Test
    void diskOnTheLeftNetworkOnTheRightAndAGlowAfterwards() {
        ActivityLight light = new ActivityLight(new ActivityCounters(proc));
        assertTrue(Arrays.stream(light.dashes()).allMatch(d -> d == Dash.OFF), "idle: dark");

        light.tick(5L << 20, 5_000, false); // disk 3 dashes, network 1
        assertArrayEquals(new Dash[] {Dash.DISK, Dash.DISK, Dash.DISK, Dash.OFF, Dash.OFF,
                Dash.OFF, Dash.OFF, Dash.OFF, Dash.OFF, Dash.NETWORK}, light.dashes());

        light.tick(0, 0, false); // the glow: one dash less per tick
        assertArrayEquals(new Dash[] {Dash.DISK, Dash.DISK, Dash.OFF, Dash.OFF, Dash.OFF,
                Dash.OFF, Dash.OFF, Dash.OFF, Dash.OFF, Dash.OFF}, light.dashes());

        light.setMeterShown(false);
        assertTrue(Arrays.stream(light.dashes()).allMatch(d -> d == Dash.OFF), "plain CDE busy light");
    }

    @Test
    void blinksYellowWhileAnApplicationStarts() {
        ActivityLight light = new ActivityLight(new ActivityCounters(proc));
        light.setInstalling(true);
        light.setLaunching(true);
        boolean on = light.dashes()[0] == Dash.BUSY;
        assertTrue(Arrays.stream(light.dashes()).allMatch(d -> d == (on ? Dash.BUSY : Dash.OFF)), "all ten together");
        light.tick(50L << 20, 0, false);
        assertTrue(Arrays.stream(light.dashes()).allMatch(d -> d == (on ? Dash.OFF : Dash.BUSY)), "the next tick: the other phase");
        assertEquals("<html>System Activity &amp; Status<br>Starting an application…</html>", light.describe());
    }

    @Test
    void anAmberChaserRunsWhileSoftwareIsInstalled() {
        ActivityLight light = new ActivityLight(new ActivityCounters(proc));
        light.tick(0, 0, true); // dpkg runs
        for (int step = 0; step < 4; step++) {
            long amber = Arrays.stream(light.dashes()).filter(d -> d == Dash.INSTALL).count();
            assertTrue(amber >= 1 && amber <= ActivityLight.CHASER, "a short run of amber dashes");
            light.tick(0, 0, true);
        }
        assertEquals("<html>System Activity &amp; Status<br>Installing software…</html>", light.describe());
        light.tick(0, 0, false);
        assertTrue(Arrays.stream(light.dashes()).allMatch(d -> d == Dash.OFF), "dpkg gone: dark again");
    }

    @Test
    void theTooltipShowsTheRates() {
        ActivityLight light = new ActivityLight(new ActivityCounters(proc));
        light.tick(12L << 20, 340L << 10, false);
        assertEquals("<html>System Activity &amp; Status<br>Disk 12 MB/s · Network 340 KB/s</html>", light.describe());
        PanelTexts.use(Locale.GERMAN);
        light.tick((long) (2.5 * (1 << 20)), 0, false);
        assertEquals("<html>Systemaktivität &amp; Status<br>Festplatte 2,5 MB/s · Netzwerk 0 KB/s</html>", light.describe());
    }
}
