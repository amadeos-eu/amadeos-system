package eu.amadeos.system.packages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link PackageService} on top of apt: reads with {@code apt-get -s}, {@code apt-cache} and {@code dpkg-query}
 * as the user, changes through {@code pkexec /usr/libexec/amadeos-packages} (directly when running as root).
 * {@code AMADEOS_PACKAGES_HELPER} in the environment replaces the helper command, for trying the desktop
 * without changing the system.
 */
public final class AptPackageService implements PackageService {
    private static final Logger LOGGER = Logger.getLogger(AptPackageService.class.getName());
    public static final String HELPER = "/usr/libexec/amadeos-packages";
    public static final String HELPER_VARIABLE = "AMADEOS_PACKAGES_HELPER";
    /** Reading the package database; a dumpavail of all Debian takes a few seconds. */
    private static final long READ_TIMEOUT_SECONDS = 120;

    /** Runs a read-only command and returns its standard output. */
    @FunctionalInterface
    public interface CommandRunner {
        String run(List<String> command) throws IOException, PackageException;
    }

    private final List<String> helper;
    private final Map<String, String> helperEnvironment;
    private final CommandRunner runner;

    public AptPackageService() {
        this(defaultHelper(), Map.of(), AptPackageService::run);
    }

    /**
     * @param helper            the helper command, to which the operation and its arguments are appended
     * @param helperEnvironment variables added to the helper's environment (tests use them for a fake apt-get)
     * @param runner            runs the read-only apt and dpkg commands
     */
    public AptPackageService(List<String> helper, Map<String, String> helperEnvironment, CommandRunner runner) {
        this.helper = List.copyOf(helper);
        this.helperEnvironment = Map.copyOf(helperEnvironment);
        this.runner = runner;
    }

    static List<String> defaultHelper() {
        String override = System.getenv(HELPER_VARIABLE);
        if (override != null && !override.isBlank()) return List.of(override.strip().split("\\s+"));
        return "root".equals(System.getProperty("user.name")) ? List.of(HELPER) : List.of("pkexec", HELPER);
    }

    @Override
    public List<Change> upgrades() throws PackageException {
        return Apt.parseSimulation(read(Apt.simulateUpgrade())).stream()
                .filter(c -> c.kind() == Change.Kind.UPGRADE).toList();
    }

    @Override
    public List<Change> simulateInstall(List<String> names) throws PackageException {
        return Apt.parseSimulation(read(Apt.simulateInstall(checked(names))));
    }

    @Override
    public List<Change> simulateRemove(List<String> names) throws PackageException {
        return Apt.parseSimulation(read(Apt.simulateRemove(checked(names))));
    }

    @Override
    public List<InstalledPackage> installed() throws PackageException {
        return Apt.parseInstalled(read(Apt.installed()), Apt.parseAutomatic(read(Apt.automatic())));
    }

    @Override
    public List<PackageInfo> available() throws PackageException {
        return Apt.parsePackages(read(Apt.dumpAvailable()));
    }

    @Override
    public Optional<PackageInfo> details(String name) throws PackageException {
        checked(List.of(name));
        try {
            return Apt.parsePackages(runner.run(Apt.show(name))).stream().findFirst();
        } catch (IOException e) {
            throw new PackageException(e.getMessage());
        } catch (PackageException e) {
            // apt-cache show fails for a name no repository has.
            LOGGER.log(Level.FINE, "No package " + name, e);
            return Optional.empty();
        }
    }

    @Override
    public List<SearchHit> search(String words) throws PackageException {
        if (words.isBlank()) return List.of();
        return Apt.parseSearch(read(Apt.search(words)));
    }

    @Override
    public AutoCloseable watch(Runnable onChange) {
        return PackageWatcher.start(PackageWatcher.DIRECTORIES, PackageWatcher.SETTLE_MILLIS, onChange);
    }

    @Override
    public Result refresh(ProgressListener listener) {
        return runHelper(List.of("refresh"), listener);
    }

    @Override
    public Result upgrade(List<String> names, boolean securityOnly, ProgressListener listener) {
        List<String> args = new ArrayList<>(List.of("upgrade"));
        if (securityOnly) args.add("--security-only");
        args.addAll(names);
        return runHelper(names, args, listener);
    }

    @Override
    public Result install(List<String> names, ProgressListener listener) {
        if (names.isEmpty()) return Result.failed("no package given");
        List<String> args = new ArrayList<>(List.of("install"));
        args.addAll(names);
        return runHelper(names, args, listener);
    }

    @Override
    public Result remove(List<String> names, ProgressListener listener) {
        if (names.isEmpty()) return Result.failed("no package given");
        List<String> args = new ArrayList<>(List.of("remove"));
        args.addAll(names);
        return runHelper(names, args, listener);
    }

    private Result runHelper(List<String> names, List<String> args, ProgressListener listener) {
        for (String name : names) {
            if (!Apt.validName(name)) return Result.failed("not a package name: " + name);
        }
        return runHelper(args, listener);
    }

    /**
     * Runs the helper and follows it: status lines become {@link Progress}, apt's messages (its standard error)
     * go to the listener line by line. The listener is called from this thread and a reader thread, one call
     * at a time.
     */
    private Result runHelper(List<String> args, ProgressListener listener) {
        List<String> command = new ArrayList<>(helper);
        command.addAll(args);
        LOGGER.info("Starting " + String.join(" ", command));
        Object lock = new Object();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(helperEnvironment);
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<Void> messages = CompletableFuture.runAsync(() -> forEachLine(process.getErrorStream(),
                    line -> {
                        synchronized (lock) {
                            listener.message(line);
                        }
                    }));
            boolean done = false;
            boolean reboot = false;
            String error = null;
            String errorId = null;
            try (BufferedReader out = reader(process.getInputStream())) {
                String line;
                while ((line = out.readLine()) != null) {
                    switch (HelperOutput.parse(line)) {
                        case HelperOutput.Status status -> {
                            synchronized (lock) {
                                listener.progress(status.progress());
                            }
                        }
                        case HelperOutput.Failure failure -> {
                            error = failure.item() + ": " + failure.message();
                            String text = "E: " + error;
                            synchronized (lock) {
                                listener.message(text);
                            }
                        }
                        case HelperOutput.Error e -> {
                            errorId = e.id();
                            error = e.message();
                        }
                        case HelperOutput.RebootRequired ignored -> reboot = true;
                        case HelperOutput.Done ignored -> done = true;
                        case HelperOutput.Nothing ignored -> {
                        }
                        case HelperOutput.Text text -> {
                            synchronized (lock) {
                                listener.message(text.text());
                            }
                        }
                    }
                }
            }
            int exit = process.waitFor();
            messages.join();
            if (exit == 0 && done) return new Result(Result.Status.DONE, "", reboot);
            if ("locked".equals(errorId) || exit == 3) {
                return new Result(Result.Status.LOCKED, error != null ? error : "another package operation is running",
                        false);
            }
            // pkexec's own codes: 126 when the authentication was dismissed, 127 when polkit refused it.
            if (exit == 126 || exit == 127) {
                return new Result(Result.Status.NOT_AUTHORIZED,
                        exit == 126 ? "authentication dismissed" : "not authorized", false);
            }
            return new Result(Result.Status.FAILED, error != null ? error : "exit code " + exit, reboot);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot start " + command.getFirst(), e);
            return Result.failed(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("interrupted");
        }
    }

    private String read(List<String> command) throws PackageException {
        try {
            return runner.run(command);
        } catch (IOException e) {
            throw new PackageException(command.getFirst() + ": " + e.getMessage());
        }
    }

    private static List<String> checked(List<String> names) throws PackageException {
        if (names.isEmpty()) throw new PackageException("no package given");
        for (String name : names) {
            if (!Apt.validName(name)) throw new PackageException("not a package name: " + name);
        }
        return names;
    }

    /** Runs a read-only command; a non-zero exit becomes a PackageException with apt's last error. */
    static String run(List<String> command) throws IOException, PackageException {
        ProcessBuilder builder = new ProcessBuilder(command);
        // Untranslated output for the parsers, UTF-8 descriptions left intact
        builder.environment().put("LC_ALL", "C.UTF-8");
        Process process = builder.start();
        process.getOutputStream().close();
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        String stdout = readAll(process.getInputStream());
        try {
            if (!process.waitFor(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new PackageException(command.getFirst() + " did not answer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new PackageException("interrupted");
        }
        if (process.exitValue() != 0) {
            throw new PackageException(errorMessage(stderr.join(), command.getFirst() + " failed"));
        }
        return stdout;
    }

    /** apt's last "E: " line without the prefix, or the fallback. */
    static String errorMessage(String stderr, String fallback) {
        return stderr.lines().filter(l -> l.startsWith("E: ")).reduce((a, b) -> b).map(l -> l.substring(3).strip())
                .orElse(fallback);
    }

    private static BufferedReader reader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static void forEachLine(InputStream in, Consumer<String> consumer) {
        try (BufferedReader reader = reader(in)) {
            String line;
            while ((line = reader.readLine()) != null) consumer.accept(line);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Reading the helper's messages failed", e);
        }
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
