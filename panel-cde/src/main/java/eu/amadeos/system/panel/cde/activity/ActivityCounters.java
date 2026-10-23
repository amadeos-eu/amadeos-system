package eu.amadeos.system.panel.cde.activity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The counters behind the activity light, read from {@code /proc}: the bytes moved by the disks since boot
 * ({@code /proc/diskstats}), the bytes moved by the network ({@code /proc/net/dev}) and whether dpkg is running.
 * Only the change between two readings matters, so the counters need no units beyond bytes. Without
 * {@code /proc} (or a file in it) the counter reads 0 and the light stays dark.
 */
public final class ActivityCounters {
    /** /proc/diskstats counts in sectors of 512 bytes, whatever the disk's own sector size. */
    static final int SECTOR = 512;
    /** Whole disks only: their partitions, loop and RAM devices, device mapper and optical drives would count twice or not at all. */
    private static final Pattern WHOLE_DISK = Pattern.compile("(?:[shv]d|xvd)[a-z]+|nvme\\d+n\\d+|mmcblk\\d+");
    private static final Pattern PID = Pattern.compile("\\d+");

    private final Path proc;

    public ActivityCounters(Path proc) {
        this.proc = proc;
    }

    public static ActivityCounters system() {
        return new ActivityCounters(Path.of("/proc"));
    }

    /** Bytes read and written by all whole disks since boot. */
    public long diskBytes() {
        return diskBytes(read(proc.resolve("diskstats")));
    }

    /** Bytes received and sent on all interfaces but the loopback since boot. */
    public long networkBytes() {
        return networkBytes(read(proc.resolve("net/dev")));
    }

    /** Whether a dpkg process runs: software is being unpacked, installed or removed, by whatever program. */
    public boolean installerRunning() {
        try (DirectoryStream<Path> processes = Files.newDirectoryStream(proc,
                p -> PID.matcher(p.getFileName().toString()).matches())) {
            for (Path process : processes) {
                if ("dpkg".equals(read(process.resolve("comm")).strip())) return true;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return false;
    }

    /** Sums sectors read (field 6) and written (field 10) of the whole disks in {@code /proc/diskstats}. */
    static long diskBytes(String diskstats) {
        long sectors = 0;
        for (String line : diskstats.split("\n")) {
            String[] f = line.strip().split("\\s+");
            if (f.length < 10 || !WHOLE_DISK.matcher(f[2]).matches()) continue;
            sectors += number(f[5]) + number(f[9]);
        }
        return sectors * SECTOR;
    }

    /** Sums the received (first) and sent (ninth) byte counts of every interface but {@code lo}. */
    static long networkBytes(String netdev) {
        long bytes = 0;
        for (String line : netdev.split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            if (line.substring(0, colon).strip().equals("lo")) continue;
            String[] f = line.substring(colon + 1).strip().split("\\s+");
            if (f.length < 9) continue;
            bytes += number(f[0]) + number(f[8]);
        }
        return bytes;
    }

    private static long number(String field) {
        try {
            return Long.parseLong(field);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.US_ASCII);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
