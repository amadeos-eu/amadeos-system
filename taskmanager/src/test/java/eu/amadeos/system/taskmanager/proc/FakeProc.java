package eu.amadeos.system.taskmanager.proc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A made-up /proc and /etc/passwd for the tests. */
public final class FakeProc {
    public final Path root;

    public FakeProc(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root.resolve("proc/net"));
        Files.createDirectories(root.resolve("etc"));
        Files.writeString(root.resolve("etc/passwd"), "root:x:0:0:root:/root:/bin/bash\nralf:x:1000:1000::/home/ralf:/bin/bash\n");
        cpu(1000, 4000, new long[]{500, 2000}, new long[]{500, 2000});
        Files.writeString(root.resolve("proc/meminfo"), """
                MemTotal:       16000000 kB
                MemFree:         7000000 kB
                MemAvailable:   11000000 kB
                Buffers:          200000 kB
                Cached:          3000000 kB
                SwapTotal:       2000000 kB
                SwapFree:        2000000 kB
                Shmem:            200000 kB
                SReclaimable:     100000 kB
                """);
        net(1000, 500);
        disk(10, 20);
        Files.writeString(root.resolve("proc/uptime"), "11520.5 40000.0\n");
        Files.writeString(root.resolve("proc/loadavg"), "1.42 0.98 0.77 2/400 9999\n");
        Files.writeString(root.resolve("proc/cpuinfo"), "processor : 0\nmodel name : Test CPU 9000\n");
    }

    /** The busy and total ticks of all processors and of each one. */
    public void cpu(long busy, long total, long[]... cores) throws IOException {
        StringBuilder s = new StringBuilder(line("cpu", busy, total));
        for (int i = 0; i < cores.length; i++) s.append(line("cpu" + i, cores[i][0], cores[i][1]));
        s.append("btime 1761900000\n");
        Files.writeString(root.resolve("proc/stat"), s.toString());
    }

    /** user=busy, idle=total-busy, the other columns 0. */
    private static String line(String name, long busy, long total) {
        return name + "  " + busy + " 0 0 " + (total - busy) + " 0 0 0 0 0 0\n";
    }

    public void net(long received, long sent) throws IOException {
        Files.writeString(root.resolve("proc/net/dev"), "Inter-|   Receive\n face |bytes\n"
                + "    lo: 999999 1 0 0 0 0 0 0 999999 1 0 0 0 0 0 0\n"
                + "  eth0: " + received + " 1 0 0 0 0 0 0 " + sent + " 1 0 0 0 0 0 0\n");
    }

    /** Sectors read and written by sda; a partition and a loop device that must not count. */
    public void disk(long readSectors, long writtenSectors) throws IOException {
        Files.writeString(root.resolve("proc/diskstats"),
                "   8       0 sda 1 0 " + readSectors + " 0 1 0 " + writtenSectors + " 0 0 0 0\n"
                        + "   8       1 sda1 1 0 5000 0 1 0 5000 0 0 0 0\n"
                        + "   7       0 loop0 1 0 7000 0 1 0 7000 0 0 0 0\n");
    }

    /** A process: its stat line, status, command line and io. */
    public void process(int pid, int ppid, String comm, String cmdline, int uid, char state, long cpuTicks,
                        long rssKb, long io, int nice, long start) throws IOException {
        Path dir = root.resolve("proc/" + pid);
        Files.createDirectories(dir);
        // pid (comm) state ppid pgrp session tty tpgid flags minflt cminflt majflt cmajflt utime stime cutime
        // cstime priority nice threads itrealvalue starttime vsize rss ...
        Files.writeString(dir.resolve("stat"), pid + " (" + comm + ") " + state + " " + ppid
                + " 1 1 0 -1 0 0 0 0 0 " + cpuTicks + " 0 0 0 20 " + nice + " 3 0 " + start + " 1000 "
                + (rssKb / 4) + " 0\n");
        Files.writeString(dir.resolve("status"), "Name:\t" + comm + "\nUid:\t" + uid + "\t" + uid + "\t" + uid
                + "\t" + uid + "\nVmRSS:\t " + rssKb + " kB\n");
        Files.write(dir.resolve("cmdline"), cmdline.replace(' ', '\0').getBytes(StandardCharsets.UTF_8));
        if (io >= 0) Files.writeString(dir.resolve("io"), "rchar: 1\nread_bytes: " + io + "\nwrite_bytes: 0\n");
        else Files.deleteIfExists(dir.resolve("io"));
    }
}
