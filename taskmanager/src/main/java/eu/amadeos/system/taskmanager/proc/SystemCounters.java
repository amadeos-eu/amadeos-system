package eu.amadeos.system.taskmanager.proc;

import java.util.List;

/**
 * The counters of the whole computer at one moment; rates come from two of them.
 *
 * @param cpuTotal     clock ticks of all processors together: {busy, all}
 * @param cpuCores     the same per processor
 * @param memory       the memory figures
 * @param netReceived  bytes received by all network devices except loopback, since boot
 * @param netSent      bytes sent likewise
 * @param diskRead     bytes read from all disks since boot
 * @param diskWritten  bytes written likewise
 */
public record SystemCounters(long[] cpuTotal, List<long[]> cpuCores, Memory memory, long netReceived,
                             long netSent, long diskRead, long diskWritten) {

    /**
     * Memory in bytes, from {@code /proc/meminfo}.
     *
     * @param total     MemTotal
     * @param available MemAvailable: what programs can still get without swapping
     * @param cache     page cache and reclaimable kernel memory (Buffers + Cached + SReclaimable - Shmem)
     * @param swapTotal SwapTotal
     * @param swapFree  SwapFree
     */
    public record Memory(long total, long available, long cache, long swapTotal, long swapFree) {
        public long used() {
            return Math.max(0, total - available);
        }

        public long free() {
            return Math.max(0, total - used() - cache);
        }

        public long swapUsed() {
            return Math.max(0, swapTotal - swapFree);
        }
    }
}
