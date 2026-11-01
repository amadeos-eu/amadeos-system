package eu.amadeos.system.taskmanager.model;

import eu.amadeos.system.taskmanager.x.WindowInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One program window of the Programs tab, with the figures of its process and all processes it started.
 *
 * @param window   the window
 * @param cpu      share of all processors, 0 .. 100
 * @param memory   resident memory in bytes
 * @param diskRate bytes per second read and written
 * @param known    whether the window named its process ({@code _NET_WM_PID}); without, the figures are unknown
 */
public record ProgramRow(WindowInfo window, double cpu, long memory, double diskRate, boolean known) {

    // --8<-- [start:programs]
    /** The rows of the Programs tab: one per window, the program's processes added up. */
    public static List<ProgramRow> of(List<WindowInfo> windows, Map<Integer, ProcessRow> processes) {
        Collection<ProcessRow> all = processes.values();
        List<ProgramRow> result = new ArrayList<>();
        for (WindowInfo w : windows) {
            double cpu = 0;
            long memory = 0;
            double disk = 0;
            boolean known = w.pid() > 0 && processes.containsKey((int) w.pid());
            if (known) {
                Set<Integer> tree = ProcessTree.subtree((int) w.pid(), all);
                for (int pid : tree) {
                    ProcessRow r = processes.get(pid);
                    if (r == null) continue;
                    cpu += r.cpu();
                    memory += r.memory();
                    if (r.diskRate() > 0) disk += r.diskRate();
                }
            }
            result.add(new ProgramRow(w, Math.min(100, cpu), memory, disk, known));
        }
        return result;
    }
    // --8<-- [end:programs]
}
