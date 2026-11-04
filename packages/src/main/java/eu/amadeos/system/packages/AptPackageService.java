package eu.amadeos.system.packages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    /**
     * Touched when the package lists are fetched: the lists directory (when a list changed) and the stamp of
     * Debian's apt-daily service (after every successful run).
     */
    private static final List<Path> REFRESH_MARKERS = List.of(Path.of("/var/lib/apt/lists"),
            Path.of("/var/lib/apt/periodic/update-success-stamp"));
    /** Reading the package database; a dumpavail of all Debian takes a few seconds. */
    private static final long READ_TIMEOUT_SECONDS = 120;

    /** Runs a read-only command and returns its standard output. */
    @FunctionalInterface
    public interface CommandRunner {
        String run(List<String> command) throws IOException, PackageException;

        /** Hands the output to {@code sink} line by line, for output too large to keep. */
        default void lines(List<String> command, Consumer<String> sink) throws IOException, PackageException {
            run(command).lines().forEach(sink);
        }
    }

    /** Runs the commands as processes. */
    private static final CommandRunner PROCESSES = new CommandRunner() {
        @Override
        public String run(List<String> command) throws IOException, PackageException {
            return AptPackageService.run(command);
        }

        @Override
        public void lines(List<String> command, Consumer<String> sink) throws IOException, PackageException {
            AptPackageService.lines(command, sink);
        }
    };
    /** Where apt keeps the package lists and the AppStream metadata (DEP-11) fetched with them. */
    private static final Path LISTS = Path.of("/var/lib/apt/lists");
    /** apt's record of the automatically installed packages. */
    private static final Path EXTENDED_STATES = Path.of("/var/lib/apt/extended_states");
    /** Changes with every installation and removal. */
    private static final Path DPKG_STATUS = Path.of("/var/lib/dpkg/status");
    /** Names the layout of the catalog cache; raise it when CatalogEntry or the way it is read changes. */
    private static final String CATALOG_FORMAT = "amadeos-catalog 1";
    /** Installed programs in these directories make a package a command-line package. */
    private static final List<String> PROGRAMS = List.of("/usr/bin/*", "/usr/sbin/*", "/bin/*", "/sbin/*");

    private final List<String> helper;
    private final Map<String, String> helperEnvironment;
    private final CommandRunner runner;
    private final Path lists;
    /** apt's extended_states, read directly; null to ask {@code apt-mark showauto} through the runner. */
    private final Path extendedStates;
    /** Keeps the catalog between starts; null for none. */
    private final StampedCache catalogCache;

    public AptPackageService() {
        this(defaultHelper(), Map.of(), PROCESSES, LISTS, EXTENDED_STATES, defaultCatalogCache());
    }

    /**
     * @param helper            the helper command, to which the operation and its arguments are appended
     * @param helperEnvironment variables added to the helper's environment (tests use them for a fake apt-get)
     * @param runner            runs the read-only apt and dpkg commands
     */
    public AptPackageService(List<String> helper, Map<String, String> helperEnvironment, CommandRunner runner) {
        this(helper, helperEnvironment, runner, LISTS);
    }

    AptPackageService(List<String> helper, Map<String, String> helperEnvironment, CommandRunner runner,
                      Path lists) {
        this(helper, helperEnvironment, runner, lists, null, null);
    }

    AptPackageService(List<String> helper, Map<String, String> helperEnvironment, CommandRunner runner,
                      Path lists, Path extendedStates, StampedCache catalogCache) {
        this.helper = List.copyOf(helper);
        this.helperEnvironment = Map.copyOf(helperEnvironment);
        this.runner = runner;
        this.lists = lists;
        this.extendedStates = extendedStates;
        this.catalogCache = catalogCache;
    }

    /**
     * {@code ~/.cache/amadeos-packages/catalog}, stale once apt's lists, the sources, the installed packages,
     * the menu entries or the program itself change.
     */
    static StampedCache defaultCatalogCache() {
        List<Path> sources = new ArrayList<>(List.of(LISTS, DPKG_STATUS, EXTENDED_STATES,
                Path.of("/etc/apt/sources.list"), Path.of("/etc/apt/sources.list.d"),
                Path.of("/usr/share/applications")));
        StampedCache.codeOf(AptPackageService.class).ifPresent(sources::add);
        return new StampedCache(StampedCache.userCache().resolve("amadeos-packages").resolve("catalog"),
                CATALOG_FORMAT, sources);
    }

    // --8<-- [start:helper]
    static List<String> defaultHelper() {
        String override = System.getenv(HELPER_VARIABLE);
        if (override != null && !override.isBlank()) return List.of(override.strip().split("\\s+"));
        return "root".equals(System.getProperty("user.name")) ? List.of(HELPER) : List.of("pkexec", HELPER);
    }
    // --8<-- [end:helper]

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
    public List<Change> simulate(ChangeSet changes) throws PackageException {
        checked(changes.names());
        return Apt.parseSimulation(read(Apt.simulateApply(changes)));
    }

    @Override
    public List<InstalledPackage> installed() throws PackageException {
        return Apt.parseInstalled(read(Apt.installed()), automatic());
    }

    /** The automatically installed packages: from apt's file when there is one, else from apt-mark. */
    private Set<String> automatic() throws PackageException {
        if (extendedStates != null) {
            try {
                return Apt.parseExtendedStates(Files.readString(extendedStates, StandardCharsets.UTF_8));
            } catch (IOException e) {
                // No such file on a system that never installed anything automatically; apt-mark knows.
                LOGGER.log(Level.FINE, "Cannot read " + extendedStates, e);
            }
        }
        return Apt.parseAutomatic(read(Apt.automatic()));
    }

    @Override
    public List<PackageInfo> available() throws PackageException {
        return Apt.parsePackages(read(Apt.dumpAvailable()));
    }

    // --8<-- [start:catalog]
    /**
     * The catalog from the cache while nothing it was read from changed; otherwise read anew (the index, the
     * installed packages and the hints at the same time) and kept for the next start.
     */
    @Override
    public List<CatalogEntry> catalog() throws PackageException {
        if (catalogCache == null) return readCatalog();
        String stamp = catalogCache.stamp();
        Optional<List<CatalogEntry>> kept = catalogCache.load(stamp, AptPackageService::decodeCatalog);
        if (kept.isPresent()) return kept.get();
        List<CatalogEntry> read = readCatalog();
        catalogCache.save(stamp, read, AptPackageService::encodeCatalog);
        return read;
    }

    private List<CatalogEntry> readCatalog() throws PackageException {
        CompletableFuture<List<InstalledPackage>> installed = onOwnThread("catalog-installed", this::installed);
        CompletableFuture<Categories.Hints> hints = onOwnThread("catalog-hints", this::hints);
        Apt.IndexReader index = new Apt.IndexReader();
        try {
            runner.lines(Apt.dumpAvailable(), index);
        } catch (IOException e) {
            throw new PackageException("apt-cache: " + e.getMessage());
        }
        return merge(index.entries(), result(installed), result(hints));
    }
    // --8<-- [end:catalog]

    private static <T> T result(CompletableFuture<T> future) throws PackageException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PackageException("interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PackageException p) throw p;
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new PackageException(String.valueOf(e.getCause().getMessage()));
        }
    }

    static void encodeCatalog(java.io.DataOutputStream out, List<CatalogEntry> entries) throws IOException {
        out.writeInt(entries.size());
        for (CatalogEntry e : entries) {
            out.writeUTF(e.name());
            out.writeUTF(e.summary());
            out.writeUTF(e.section());
            out.writeUTF(e.category().name());
            out.writeUTF(e.installedVersion());
            out.writeUTF(e.availableVersion());
            out.writeBoolean(e.automatic());
        }
    }

    static List<CatalogEntry> decodeCatalog(java.io.DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) throw new IOException("bad count " + count);
        List<CatalogEntry> entries = new ArrayList<>(Math.min(count, 1 << 18));
        for (int i = 0; i < count; i++) {
            entries.add(new CatalogEntry(in.readUTF(), in.readUTF(), in.readUTF(), Category.valueOf(in.readUTF()),
                    in.readUTF(), in.readUTF(), in.readBoolean()));
        }
        return List.copyOf(entries);
    }

    /** The index entries and the installed packages as one list sorted by name. */
    static List<CatalogEntry> merge(List<Apt.IndexEntry> index, List<InstalledPackage> installed,
                                    Categories.Hints hints) {
        Map<String, InstalledPackage> byName = new HashMap<>();
        for (InstalledPackage p : installed) {
            String plain = p.name().contains(":") ? p.name().substring(0, p.name().indexOf(':')) : p.name();
            byName.putIfAbsent(plain, p);
        }
        List<CatalogEntry> entries = new ArrayList<>();
        for (Apt.IndexEntry e : index) {
            InstalledPackage p = byName.remove(e.name());
            entries.add(new CatalogEntry(e.name(), e.summary(), e.section(),
                    Categories.of(e.name(), e.section(), e.tags(), hints), p == null ? "" : p.version(), e.version(),
                    p != null && p.automatic()));
        }
        // Installed, but no repository has it (any more).
        for (Map.Entry<String, InstalledPackage> left : byName.entrySet()) {
            InstalledPackage p = left.getValue();
            String section = p.section().substring(p.section().indexOf('/') + 1);
            entries.add(new CatalogEntry(left.getKey(), p.summary(), section,
                    Categories.of(left.getKey(), section, List.of(), hints), p.version(), "", p.automatic()));
        }
        entries.sort(Comparator.comparing(CatalogEntry::name));
        return entries;
    }

    /** What AppStream and the files of the installed packages say about the kind of each package. */
    Categories.Hints hints() {
        Categories.Dep11Reader dep11 = new Categories.Dep11Reader();
        boolean appstream = false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(lists, "*_Components-*.yml*")) {
            for (Path file : files) {
                try {
                    runner.lines(Apt.catFile(file.toString()), dep11);
                    appstream = true;
                } catch (IOException | PackageException e) {
                    LOGGER.log(Level.FINE, "Cannot read " + file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Cannot list " + lists, e);
        }
        Set<String> desktop = new HashSet<>(dep11.desktop());
        Set<String> console = new HashSet<>(dep11.console());
        for (Categories.Owned entry : owners("/usr/share/applications/*.desktop")) {
            switch (menuEntry(Path.of(entry.path()))) {
                case APPLICATION -> desktop.add(entry.pkg());
                case COMMAND_LINE -> console.add(entry.pkg());
                default -> {
                }
            }
        }
        for (String pattern : PROGRAMS) {
            for (Categories.Owned entry : owners(pattern)) console.add(entry.pkg());
        }
        return new Categories.Hints(appstream, desktop, console);
    }

    private List<Categories.Owned> owners(String pattern) {
        try {
            return Categories.parseOwners(runner.run(Apt.owners(pattern)));
        } catch (IOException | PackageException e) {
            // dpkg-query fails when nothing matches, /sbin/* on a merged /usr for one.
            return List.of();
        }
    }

    /**
     * What a menu entry shows: an application, a terminal program ({@code Terminal=true}), or nothing
     * ({@code NoDisplay} or {@code Hidden}, which Debian uses for helpers and MIME handlers).
     */
    static Category menuEntry(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Category.OTHER;
        }
        boolean main = false;
        boolean terminal = false;
        for (String line : lines) {
            String l = line.strip();
            if (l.startsWith("[")) {
                main = l.equals("[Desktop Entry]");
            } else if (main) {
                if (l.equals("NoDisplay=true") || l.equals("Hidden=true") || l.equals("Type=Link")) {
                    return Category.OTHER;
                }
                if (l.equals("Terminal=true")) terminal = true;
            }
        }
        return terminal ? Category.COMMAND_LINE : Category.APPLICATION;
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
    public Optional<Instant> lastRefresh() {
        return hasLists(lists) ? lastModified(REFRESH_MARKERS) : Optional.empty();
    }

    /** Whether apt has fetched package lists; the image is built without them (apt-get clean and rm). */
    static boolean hasLists(Path lists) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(lists, "*_Packages*")) {
            return files.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    /** The newest modification time of the paths that exist. */
    static Optional<Instant> lastModified(List<Path> paths) {
        Instant newest = null;
        for (Path path : paths) {
            try {
                Instant time = Files.getLastModifiedTime(path).toInstant();
                if (newest == null || time.isAfter(newest)) newest = time;
            } catch (IOException e) {
                // not there
            }
        }
        return Optional.ofNullable(newest);
    }

    @Override
    public boolean automaticSecurityUpdates() throws PackageException {
        return Apt.parseUnattendedUpgrade(read(Apt.unattendedUpgrade()));
    }

    @Override
    public Result setAutomaticSecurityUpdates(boolean on, ProgressListener listener) {
        return runHelper(List.of("automatic-security", on ? "on" : "off"), listener);
    }

    @Override
    public Result setAptProxy(String httpUrl, String httpsUrl, ProgressListener listener) {
        // On standard input, so that a password in the URLs never shows in the process list.
        StringBuilder input = new StringBuilder();
        if (httpUrl != null && !httpUrl.isBlank()) input.append("http ").append(httpUrl.strip()).append('\n');
        if (httpsUrl != null && !httpsUrl.isBlank()) input.append("https ").append(httpsUrl.strip()).append('\n');
        return runHelper(List.of("proxy"), input.toString(), listener);
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

    @Override
    public Result apply(ChangeSet changes, ProgressListener listener) {
        if (changes.isEmpty()) return Result.failed("no package given");
        return runHelper(changes.names(), changes.helperArguments(), listener);
    }

    private Result runHelper(List<String> names, List<String> args, ProgressListener listener) {
        for (String name : names) {
            if (!Apt.validName(name)) return Result.failed("not a package name: " + name);
        }
        return runHelper(args, listener);
    }

    // --8<-- [start:run-helper]
    /**
     * Runs the helper and follows it: status lines become {@link Progress}, apt's messages (its standard error)
     * go to the listener line by line. The listener is called from this thread and a reader thread, one call
     * at a time.
     */
    private Result runHelper(List<String> args, ProgressListener listener) {
        return runHelper(args, "", listener);
    }

    /** @param input written to the helper's standard input, which is closed after it */
    private Result runHelper(List<String> args, String input, ProgressListener listener) {
        List<String> command = new ArrayList<>(helper);
        command.addAll(args);
        LOGGER.info("Starting " + String.join(" ", command));
        Object lock = new Object();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(helperEnvironment);
            Process process = builder.start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            CompletableFuture<Void> messages = onOwnThread("helper-messages", () -> {
                forEachLine(process.getErrorStream(), line -> {
                    synchronized (lock) {
                        listener.message(line);
                    }
                });
                return null;
            });
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
    // --8<-- [end:run-helper]

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
        StringBuilder out = new StringBuilder();
        lines(command, line -> out.append(line).append('\n'));
        return out.toString();
    }

    /** apt's last "E: " line without the prefix, or the fallback. */
    static String errorMessage(String stderr, String fallback) {
        return stderr.lines().filter(l -> l.startsWith("E: ")).reduce((a, b) -> b).map(l -> l.substring(3).strip())
                .orElse(fallback);
    }

    /** Runs a read-only command and hands its output over line by line. */
    static void lines(List<String> command, Consumer<String> sink) throws IOException, PackageException {
        lines(command, sink, Duration.ofSeconds(READ_TIMEOUT_SECONDS));
    }

    // --8<-- [start:lines]
    /**
     * Runs a read-only command and hands its output over line by line, on a thread of its own, so that the time
     * limit also holds for a command that hangs without printing or closing its output. Both outputs are read
     * on threads of their own, never in the common pool: on a machine with one or two processors that pool has
     * a single worker, which the standard error reader holds until the command ends, so the standard output
     * would never be read, the command would block on a full pipe and the read would only end at the time limit.
     */
    static void lines(List<String> command, Consumer<String> sink, Duration timeout)
            throws IOException, PackageException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("LC_ALL", "C.UTF-8");
        Process process = builder.start();
        process.getOutputStream().close();
        CompletableFuture<String> stderr = onOwnThread("apt-stderr", () -> readAll(process.getErrorStream()));
        CompletableFuture<Void> stdout = onOwnThread("apt-stdout", () -> {
            try (BufferedReader out = reader(process.getInputStream())) {
                String line;
                while ((line = out.readLine()) != null) sink.accept(line);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        });
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException();
            }
            stdout.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            process.destroyForcibly();
            throw new PackageException(command.getFirst() + " did not answer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new PackageException("interrupted");
        } catch (ExecutionException e) {
            process.destroyForcibly();
            if (e.getCause() instanceof UncheckedIOException io) throw io.getCause();
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IOException(e.getCause());
        }
        if (process.exitValue() != 0) {
            throw new PackageException(errorMessage(stderr.join(), command.getFirst() + " failed"));
        }
    }

    /** Runs {@code task} on a new daemon thread, for reads that block until a process ends. */
    static <T> CompletableFuture<T> onOwnThread(String name, Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Thread.ofPlatform().name(name).daemon().start(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }
    // --8<-- [end:lines]

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
