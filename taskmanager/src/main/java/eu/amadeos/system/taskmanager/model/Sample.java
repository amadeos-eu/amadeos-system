package eu.amadeos.system.taskmanager.model;

import java.util.List;
import java.util.Map;

/**
 * Everything the window shows for one moment.
 *
 * @param cpu          busy share of all processors, 0 .. 100
 * @param cores        busy share per processor
 * @param memory       memory figures in bytes
 * @param netDown      bytes per second received
 * @param netUp        bytes per second sent
 * @param diskRead     bytes per second read from disks
 * @param diskWrite    bytes per second written to disks
 * @param processes    all processes by process id
 * @param load         the load averages of 1, 5 and 15 minutes
 * @param uptimeSeconds seconds since boot
 */
public record Sample(double cpu, List<Double> cores, eu.amadeos.system.taskmanager.proc.SystemCounters.Memory memory,
                     double netDown, double netUp, double diskRead, double diskWrite,
                     Map<Integer, ProcessRow> processes, double[] load, double uptimeSeconds) {
}
