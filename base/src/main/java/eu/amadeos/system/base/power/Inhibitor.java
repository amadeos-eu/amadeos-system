package eu.amadeos.system.base.power;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * A systemd-logind inhibitor lock, held by a {@code systemd-inhibit … cat} child process: as long as it runs,
 * logind leaves the lid switch and the power button to the desktop ({@code block}) or waits a moment before
 * the computer sleeps ({@code delay}). The lock ends when {@link #close()} closes the child's input, and also
 * when this process dies, because then the pipe closes too.
 */
public final class Inhibitor implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(Inhibitor.class.getName());

    private final Process process;

    private Inhibitor(Process process) {
        this.process = process;
    }

    /**
     * @param what logind's lock types joined by ':', e.g. {@code handle-lid-switch:handle-power-key} or {@code sleep}
     * @param mode {@code block} or {@code delay}
     * @param why  the reason {@code systemd-inhibit --list} shows
     */
    public static Inhibitor take(String what, String mode, String why) throws IOException {
        Process process = new ProcessBuilder(command(what, mode, why))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        LOGGER.fine(() -> "Inhibitor " + what + " (" + mode + ") taken");
        return new Inhibitor(process);
    }

    /** The command line, package visible for tests. */
    static List<String> command(String what, String mode, String why) {
        return List.of("systemd-inhibit", "--what=" + what, "--who=amadeOS", "--why=" + why, "--mode=" + mode, "cat");
    }

    public boolean held() {
        return process.isAlive();
    }

    @Override
    public void close() {
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // The child has gone already.
        }
        process.destroy();
    }
}
