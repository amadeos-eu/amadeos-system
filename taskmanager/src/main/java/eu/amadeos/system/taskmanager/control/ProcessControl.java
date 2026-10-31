package eu.amadeos.system.taskmanager.control;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Ends, pauses and continues processes and changes their priority, with the commands {@code kill} and
 * {@code renice}. The user's own processes are handled directly; for those of other users and of the system,
 * and to raise a priority, the command runs through {@code pkexec}, so the session's polkit agent asks for the
 * administrator's password.
 */
public final class ProcessControl {
    /** How a priority is shown: nice values from very high to very low. */
    public enum Priority {
        VERY_HIGH(-10), HIGH(-5), NORMAL(0), LOW(5), VERY_LOW(10);

        private final int nice;

        Priority(int nice) {
            this.nice = nice;
        }

        public int nice() {
            return nice;
        }

        /** The priority nearest to a nice value. */
        public static Priority of(int nice) {
            Priority best = NORMAL;
            for (Priority p : values()) {
                if (Math.abs(p.nice - nice) < Math.abs(best.nice - nice)) best = p;
            }
            return best;
        }
    }

    /** Runs a command and returns its exit code. */
    public interface Runner {
        int run(List<String> command) throws IOException, InterruptedException;

        Runner SYSTEM = command -> new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
    }

    private final Runner runner;
    private final IntPredicate own;

    /**
     * @param runner runs the commands
     * @param own    whether a process id belongs to the user running the Task Manager
     */
    public ProcessControl(Runner runner, IntPredicate own) {
        this.runner = runner;
        this.own = own;
    }

    // --8<-- [start:signals]
    /** Asks the process to quit (SIGTERM). */
    public void terminate(int pid) throws IOException {
        signal(pid, "TERM");
    }

    /** Ends the process at once (SIGKILL); it cannot save anything. */
    public void kill(int pid) throws IOException {
        signal(pid, "KILL");
    }

    /** Stops the process until {@link #resume}. */
    public void pause(int pid) throws IOException {
        signal(pid, "STOP");
    }

    public void resume(int pid) throws IOException {
        signal(pid, "CONT");
    }

    /** Sets the nice value; a higher priority than now, or another user's process, needs the administrator. */
    public void setPriority(int pid, int currentNice, Priority priority) throws IOException {
        List<String> command = List.of("renice", "-n", Integer.toString(priority.nice()), "-p",
                Integer.toString(pid));
        run(command, !own.test(pid) || priority.nice() < currentNice);
    }

    private void signal(int pid, String signal) throws IOException {
        if (pid <= 1) throw new IOException("Process " + pid + " cannot be ended");
        run(List.of("kill", "-s", signal, Integer.toString(pid)), !own.test(pid));
    }
    // --8<-- [end:signals]

    private void run(List<String> command, boolean administrator) throws IOException {
        List<String> full = new ArrayList<>();
        if (administrator) full.add("pkexec");
        full.addAll(command);
        int code;
        try {
            code = runner.run(full);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        // pkexec: 126 = the password dialog was cancelled, 127 = not authorized.
        if (code == 126 || code == 127 && administrator) throw new Cancelled();
        if (code != 0) throw new IOException(String.join(" ", command) + " failed (" + code + ")");
    }

    /** The administrator's password was not given. */
    public static final class Cancelled extends IOException {
        Cancelled() {
            super("Not authorized");
        }
    }
}
