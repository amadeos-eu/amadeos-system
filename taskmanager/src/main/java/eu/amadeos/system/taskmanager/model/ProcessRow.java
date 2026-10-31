package eu.amadeos.system.taskmanager.model;

import eu.amadeos.system.taskmanager.proc.ProcessInfo;

/**
 * A process with the figures of the last second.
 *
 * @param info      what {@code /proc} says about it
 * @param user      the user name
 * @param cpu       share of all processors in the last interval, 0 .. 100
 * @param diskRate  bytes per second read and written, or -1 if not known (processes of other users)
 */
public record ProcessRow(ProcessInfo info, String user, double cpu, double diskRate) {
    public int pid() {
        return info.pid();
    }

    public int parentPid() {
        return info.parentPid();
    }

    public String name() {
        return info.name();
    }

    public long memory() {
        return info.rssBytes();
    }
}
