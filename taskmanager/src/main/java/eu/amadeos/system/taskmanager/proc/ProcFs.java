package eu.amadeos.system.taskmanager.proc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the kernel's figures from {@code /proc} (and {@code /etc/passwd}) under a root directory, so tests can
 * give a made-up tree. Every method reads the files anew; processes that end while they are read are left out.
 */
public final class ProcFs {
    /** Clock ticks per second of {@code /proc} (USER_HZ), the same on every Linux system. */
    public static final int TICKS_PER_SECOND = 100;
    /** Whole disks: sda, vdb, nvme0n1, mmcblk0, xvda (no partitions, no loop devices). */
    private static final Pattern DISK = Pattern.compile("(sd|vd|xvd|hd)[a-z]+|nvme\\d+n\\d+|mmcblk\\d+");

    private final Path proc;
    private final Path passwd;

    public ProcFs(Path root) {
        this.proc = root.resolve("proc");
        this.passwd = root.resolve("etc/passwd");
    }

    public static ProcFs system() {
        return new ProcFs(Path.of("/"));
    }

    // --8<-- [start:counters]
    /** CPU, memory, network and disk counters of the whole computer. */
    public SystemCounters counters() throws IOException {
        long[] total = {0, 0};
        List<long[]> cores = new ArrayList<>();
        for (String line : Files.readAllLines(proc.resolve("stat"))) {
            if (!line.startsWith("cpu")) continue;
            String[] f = line.trim().split("\\s+");
            long all = 0;
            // user nice system idle iowait irq softirq steal (guest time is already in user)
            for (int i = 1; i < Math.min(f.length, 9); i++) all += Long.parseLong(f[i]);
            long idle = Long.parseLong(f[4]) + (f.length > 5 ? Long.parseLong(f[5]) : 0);
            long[] ticks = {all - idle, all};
            if (f[0].equals("cpu")) total = ticks; else cores.add(ticks);
        }
        return new SystemCounters(total, cores, memory(), net(0), net(8), disk(2), disk(6));
    }
    // --8<-- [end:counters]

    SystemCounters.Memory memory() throws IOException {
        Map<String, Long> kb = new HashMap<>();
        for (String line : Files.readAllLines(proc.resolve("meminfo"))) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String[] v = line.substring(colon + 1).trim().split("\\s+");
            try {
                kb.put(line.substring(0, colon), Long.parseLong(v[0]));
            } catch (NumberFormatException e) {
                // not a number: ignored
            }
        }
        long total = kb.getOrDefault("MemTotal", 0L);
        long free = kb.getOrDefault("MemFree", 0L);
        long cache = kb.getOrDefault("Buffers", 0L) + kb.getOrDefault("Cached", 0L)
                + kb.getOrDefault("SReclaimable", 0L) - kb.getOrDefault("Shmem", 0L);
        long available = kb.getOrDefault("MemAvailable", free + Math.max(0, cache));
        return new SystemCounters.Memory(total * 1024, available * 1024, Math.max(0, cache) * 1024,
                kb.getOrDefault("SwapTotal", 0L) * 1024, kb.getOrDefault("SwapFree", 0L) * 1024);
    }

    /** Sum of one column of /proc/net/dev (0 received bytes, 8 sent bytes) over all devices but lo. */
    private long net(int column) throws IOException {
        Path file = proc.resolve("net/dev");
        if (!Files.exists(file)) return 0;
        long sum = 0;
        for (String line : Files.readAllLines(file)) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String device = line.substring(0, colon).trim();
            if (device.equals("lo")) continue;
            String[] f = line.substring(colon + 1).trim().split("\\s+");
            if (f.length > column) sum += Long.parseLong(f[column]);
        }
        return sum;
    }

    /** Sum of sectors read (index 2 after the name) or written (6) of the whole disks, in bytes. */
    private long disk(int column) throws IOException {
        Path file = proc.resolve("diskstats");
        if (!Files.exists(file)) return 0;
        long sectors = 0;
        for (String line : Files.readAllLines(file)) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 10 || !DISK.matcher(f[2]).matches()) continue;
            sectors += Long.parseLong(f[3 + column]);
        }
        return sectors * 512;
    }

    /** The names of the network devices that are up, without lo (for the Performance tab). */
    public List<String> networkDevices() {
        Path net = proc.getParent().resolve("sys/class/net");
        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(net)) {
            for (Path d : dirs) {
                String name = d.getFileName().toString();
                if (name.equals("lo")) continue;
                try {
                    if (Files.readString(d.resolve("operstate")).trim().equals("up")) result.add(name);
                } catch (IOException e) {
                    // no state: left out
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        result.sort(null);
        return result;
    }

    // --8<-- [start:processes]
    /** All processes; those that end while they are read are left out. */
    public List<ProcessInfo> processes() throws IOException {
        List<ProcessInfo> result = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(proc, p -> isNumber(p.getFileName().toString()))) {
            for (Path dir : dirs) {
                process(dir).ifPresent(result::add);
            }
        }
        return result;
    }

    /** One process, or empty if it is gone. */
    public Optional<ProcessInfo> process(int pid) {
        return process(proc.resolve(Integer.toString(pid)));
    }

    private Optional<ProcessInfo> process(Path dir) {
        try {
            String stat = Files.readString(dir.resolve("stat"));
            int open = stat.indexOf('(');
            int close = stat.lastIndexOf(')');
            if (open < 0 || close < open) return Optional.empty();
            int pid = Integer.parseInt(stat.substring(0, open).trim());
            String comm = stat.substring(open + 1, close);
            // Field n of proc(5) is at index n - 3 after the closing parenthesis.
            String[] f = stat.substring(close + 2).trim().split(" ");
            char state = f[0].charAt(0);
            int ppid = Integer.parseInt(f[1]);
            long cpu = Long.parseLong(f[11]) + Long.parseLong(f[12]);
            int nice = Integer.parseInt(f[16]);
            int threads = Integer.parseInt(f[17]);
            long start = Long.parseLong(f[19]);

            int uid = -1;
            long rss = Long.parseLong(f[21]) * 4096;
            for (String line : Files.readAllLines(dir.resolve("status"))) {
                if (line.startsWith("Uid:")) {
                    uid = Integer.parseInt(line.substring(4).trim().split("\\s+")[0]);
                } else if (line.startsWith("VmRSS:")) {
                    rss = Long.parseLong(line.substring(6).trim().split("\\s+")[0]) * 1024;
                }
            }
            List<String> argv = cmdline(dir);
            boolean kernel = argv.isEmpty() && (ppid == 2 || pid == 2);
            return Optional.of(new ProcessInfo(pid, ppid, ProcessInfo.displayName(comm, argv),
                    String.join(" ", argv), uid, state, cpu, rss, io(dir), nice, threads, start, kernel));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
    // --8<-- [end:processes]

    private static List<String> cmdline(Path dir) {
        try {
            byte[] bytes = Files.readAllBytes(dir.resolve("cmdline"));
            if (bytes.length == 0) return List.of();
            String s = new String(bytes, StandardCharsets.UTF_8);
            if (s.endsWith("\0")) s = s.substring(0, s.length() - 1);
            return Arrays.asList(s.split("\0", -1));
        } catch (IOException e) {
            return List.of();
        }
    }

    /** read_bytes + write_bytes of /proc/pid/io, or -1 if it cannot be read (processes of other users). */
    private static long io(Path dir) {
        try {
            long sum = 0;
            for (String line : Files.readAllLines(dir.resolve("io"))) {
                if (line.startsWith("read_bytes:") || line.startsWith("write_bytes:")) {
                    sum += Long.parseLong(line.substring(line.indexOf(':') + 1).trim());
                }
            }
            return sum;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** Seconds since boot ({@code /proc/uptime}). */
    public double uptimeSeconds() {
        try {
            return Double.parseDouble(Files.readString(proc.resolve("uptime")).trim().split("\\s+")[0]);
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** The three load averages ({@code /proc/loadavg}). */
    public double[] loadAverage() {
        try {
            String[] f = Files.readString(proc.resolve("loadavg")).trim().split("\\s+");
            return new double[]{Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2])};
        } catch (IOException | RuntimeException e) {
            return new double[]{0, 0, 0};
        }
    }

    /** The processor's model name, or "". */
    public String cpuModel() {
        try {
            for (String line : Files.readAllLines(proc.resolve("cpuinfo"))) {
                if (line.startsWith("model name")) return line.substring(line.indexOf(':') + 1).trim();
            }
        } catch (IOException e) {
            // unknown
        }
        return "";
    }

    /** The user names of {@code /etc/passwd}, by user id. */
    public Map<Integer, String> userNames() {
        Map<Integer, String> names = new HashMap<>();
        try {
            for (String line : Files.readAllLines(passwd)) {
                String[] f = line.split(":");
                if (f.length > 2) {
                    try {
                        names.put(Integer.parseInt(f[2]), f[0]);
                    } catch (NumberFormatException e) {
                        // a broken line
                    }
                }
            }
        } catch (IOException e) {
            // no names: the ids are shown
        }
        return names;
    }

    /** The details of the Properties dialog: executable, working folder, open files, control group. */
    public Details details(int pid) {
        Path dir = proc.resolve(Integer.toString(pid));
        return new Details(link(dir.resolve("exe")), link(dir.resolve("cwd")), count(dir.resolve("fd")),
                cgroup(dir));
    }

    /** What {@link #details} finds; "" or -1 for what cannot be read (processes of other users). */
    public record Details(String executable, String workingDirectory, int openFiles, String cgroup) {
    }

    private static String link(Path p) {
        try {
            return Files.readSymbolicLink(p).toString();
        } catch (IOException | UnsupportedOperationException e) {
            return "";
        }
    }

    private static int count(Path dir) {
        try (var s = Files.list(dir)) {
            return (int) s.count();
        } catch (IOException e) {
            return -1;
        }
    }

    private static String cgroup(Path dir) {
        try {
            for (String line : Files.readAllLines(dir.resolve("cgroup"))) {
                if (line.startsWith("0::")) return line.substring(3);
            }
        } catch (IOException e) {
            // none
        }
        return "";
    }

    /** The boot time in seconds since the epoch ({@code btime} of {@code /proc/stat}). */
    public long bootTimeSeconds() {
        try {
            for (String line : Files.readAllLines(proc.resolve("stat"))) {
                if (line.startsWith("btime ")) return Long.parseLong(line.substring(6).trim());
            }
        } catch (IOException | RuntimeException e) {
            // unknown
        }
        return 0;
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
