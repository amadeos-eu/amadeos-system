package eu.amadeos.system.base.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import eu.amadeos.system.base.x11.LaunchFeedback;
import eu.amadeos.system.base.xdg.XdgDirectories;

/**
 * Starts desktop applications detached from the panel or window manager process.
 *
 * <p>A plain {@code ProcessBuilder.start()} connects the child's stdout and stderr to pipes
 * that nobody reads: the child blocks once about 64 KB of output fill the pipe, and it is
 * killed by SIGPIPE on its next write after the launching JVM exits or restarts. Here the
 * output goes to an application log file, stdin is closed, and the child gets its own
 * session via {@code setsid} so it is independent of the launcher.
 */
public final class ProcessLauncher {
    private static final Logger LOGGER = Logger.getLogger(ProcessLauncher.class.getName());
    static final long MAX_LOG_BYTES = 5L * 1024 * 1024;

    private ProcessLauncher() {
    }

    /** Starts the command in the user's home directory. */
    public static Process start(List<String> command) throws IOException {
        return start(command, new File(System.getProperty("user.home", "/tmp")));
    }

    /**
     * Starts the command, with its output in the application's own log file (see {@link #logFile(List)}),
     * and shows the busy cursor until its window appears (see {@link LaunchFeedback}).
     */
    public static Process start(List<String> command, File workingDirectory) throws IOException {
        Process process = start(command, workingDirectory, logFile(command));
        LaunchFeedback.announce();
        return process;
    }

    static Process start(List<String> command, File workingDirectory, Path log) throws IOException {
        Objects.requireNonNull(command);
        if (command.isEmpty()) {
            throw new IOException("Cannot launch an empty command");
        }
        List<String> fullCommand = new ArrayList<>();
        if (isOnPath("setsid")) {
            fullCommand.add("setsid");
        }
        fullCommand.addAll(command);

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        if (workingDirectory != null && workingDirectory.isDirectory()) {
            pb.directory(workingDirectory);
        }
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        pb.redirectErrorStream(true);
        pb.redirectOutput(outputTarget(log));
        LOGGER.info(() -> "Launching: " + String.join(" ", command));
        return pb.start();
    }

    /**
     * Starts the first candidate whose executable is installed.
     *
     * @return the started process, or empty if none of the candidates is installed
     */
    public static Optional<Process> startFirstAvailable(List<List<String>> candidates) throws IOException {
        Optional<List<String>> command = firstAvailable(candidates);
        if (command.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(start(command.get()));
    }

    /** First candidate whose executable is on the PATH; checked in-process, without running {@code which}. */
    public static Optional<List<String>> firstAvailable(List<List<String>> candidates) {
        for (List<String> candidate : candidates) {
            if (!candidate.isEmpty() && isOnPath(candidate.get(0))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static boolean isOnPath(String executable) {
        return isOnPath(executable, System.getenv("PATH"));
    }

    static boolean isOnPath(String executable, String path) {
        if (executable == null || executable.isBlank()) {
            return false;
        }
        if (executable.contains("/")) {
            return Files.isExecutable(Path.of(executable));
        }
        String searchPath = path == null || path.isBlank() ? "/usr/local/bin:/usr/bin:/bin" : path;
        for (String dir : searchPath.split(":")) {
            if (dir.isEmpty()) {
                continue;
            }
            Path candidate = Path.of(dir, executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Appends to the log file, keeping one rotated copy ({@code .old}); discards the output if unwritable. */
    private static ProcessBuilder.Redirect outputTarget(Path log) {
        try {
            Files.createDirectories(log.getParent());
            rotateIfLarge(log, MAX_LOG_BYTES);
            return ProcessBuilder.Redirect.appendTo(log.toFile());
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Application log not writable, discarding output", e);
            return ProcessBuilder.Redirect.DISCARD;
        }
    }

    /**
     * One log per application in {@code ~/.cache/amadeos/logs}, named after the program:
     * {@code firefox-esr.log}, {@code xterm.log}, {@code amade-files.log}.
     */
    static Path logFile(List<String> command) {
        return XdgDirectories.amadeosLogDir().resolve(logName(command) + ".log");
    }

    /** The program's name without directory, reduced to letters, digits, dot, dash and underscore. */
    static String logName(List<String> command) {
        String program = command.isEmpty() ? "" : command.get(0);
        String name = program.substring(program.lastIndexOf('/') + 1)
                .replaceAll("[^A-Za-z0-9._-]", "_").replaceFirst("^[._]+", "");
        return name.isEmpty() ? "application" : name;
    }

    static void rotateIfLarge(Path log, long maxBytes) throws IOException {
        if (Files.isRegularFile(log) && Files.size(log) > maxBytes) {
            Files.move(log, log.resolveSibling(log.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
