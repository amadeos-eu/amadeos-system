package eu.amadeos.system.taskmanager.model;

import eu.amadeos.system.taskmanager.proc.ProcFs;
import eu.amadeos.system.taskmanager.proc.ProcessInfo;
import eu.amadeos.system.taskmanager.proc.SystemCounters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Takes a {@link Sample} each time it is asked, from the difference to the previous counters, and keeps the
 * histories of the Performance tab. Called from one background thread.
 */
public final class Sampler {
    private final ProcFs proc;
    private final LongSupplier clockNanos;
    private Map<Integer, String> users;
    private SystemCounters lastCounters;
    private Map<Integer, ProcessInfo> lastProcesses = Map.of();
    private long lastNanos;

    private final History cpu = new History();
    private final List<History> cores = new ArrayList<>();
    private final History memoryUsed = new History();
    private final History memoryCache = new History();
    private final History netDown = new History();
    private final History netUp = new History();
    private final History diskRead = new History();
    private final History diskWrite = new History();

    public Sampler(ProcFs proc, LongSupplier clockNanos) {
        this.proc = proc;
        this.clockNanos = clockNanos;
    }

    public static Sampler system() {
        return new Sampler(ProcFs.system(), System::nanoTime);
    }

    public ProcFs proc() {
        return proc;
    }

    // --8<-- [start:sample]
    /** Reads the counters and returns the figures since the previous call (zeros the first time). */
    public Sample sample() throws IOException {
        if (users == null) users = proc.userNames();
        SystemCounters now = proc.counters();
        List<ProcessInfo> list = proc.processes();
        long nanos = clockNanos.getAsLong();
        SystemCounters before = lastCounters == null ? now : lastCounters;
        double seconds = lastCounters == null ? 1 : Math.max(0.001, (nanos - lastNanos) / 1e9);
        long allTicks = Math.max(0, now.cpuTotal()[1] - before.cpuTotal()[1]);

        Map<Integer, ProcessRow> rows = new LinkedHashMap<>();
        Map<Integer, ProcessInfo> byPid = new HashMap<>();
        for (ProcessInfo p : list) {
            byPid.put(p.pid(), p);
            ProcessInfo old = lastProcesses.get(p.pid());
            // A new process with the same id started later than the old one.
            if (old != null && old.startTicks() != p.startTicks()) old = null;
            double share = old == null || allTicks == 0 ? 0
                    : Math.min(100, 100.0 * Math.max(0, p.cpuTicks() - old.cpuTicks()) / allTicks);
            double disk = p.ioBytes() < 0 ? -1
                    : old == null || old.ioBytes() < 0 ? 0 : Math.max(0, p.ioBytes() - old.ioBytes()) / seconds;
            String user = users.getOrDefault(p.uid(), Integer.toString(p.uid()));
            rows.put(p.pid(), new ProcessRow(p, user, share, disk));
        }

        double cpuShare = busy(before.cpuTotal(), now.cpuTotal());
        List<Double> coreShares = new ArrayList<>();
        for (int i = 0; i < now.cpuCores().size(); i++) {
            long[] b = i < before.cpuCores().size() ? before.cpuCores().get(i) : now.cpuCores().get(i);
            coreShares.add(busy(b, now.cpuCores().get(i)));
        }
        double down = Math.max(0, now.netReceived() - before.netReceived()) / seconds;
        double up = Math.max(0, now.netSent() - before.netSent()) / seconds;
        double read = Math.max(0, now.diskRead() - before.diskRead()) / seconds;
        double write = Math.max(0, now.diskWritten() - before.diskWritten()) / seconds;

        lastCounters = now;
        lastProcesses = byPid;
        lastNanos = nanos;

        cpu.add(cpuShare);
        while (cores.size() < coreShares.size()) cores.add(new History());
        for (int i = 0; i < coreShares.size(); i++) cores.get(i).add(coreShares.get(i));
        memoryUsed.add(now.memory().used());
        memoryCache.add(now.memory().cache());
        netDown.add(down);
        netUp.add(up);
        diskRead.add(read);
        diskWrite.add(write);

        return new Sample(cpuShare, coreShares, now.memory(), down, up, read, write, rows, proc.loadAverage(),
                proc.uptimeSeconds());
    }
    // --8<-- [end:sample]

    private static double busy(long[] before, long[] now) {
        long all = now[1] - before[1];
        if (all <= 0) return 0;
        return Math.min(100, Math.max(0, 100.0 * (now[0] - before[0]) / all));
    }

    public History cpu() {
        return cpu;
    }

    public List<History> cores() {
        return List.copyOf(cores);
    }

    public History memoryUsed() {
        return memoryUsed;
    }

    public History memoryCache() {
        return memoryCache;
    }

    public History netDown() {
        return netDown;
    }

    public History netUp() {
        return netUp;
    }

    public History diskRead() {
        return diskRead;
    }

    public History diskWrite() {
        return diskWrite;
    }
}
