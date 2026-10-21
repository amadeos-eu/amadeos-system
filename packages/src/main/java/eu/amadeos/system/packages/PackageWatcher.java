package eu.amadeos.system.packages;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the directories in which dpkg keeps the installed packages ({@code /var/lib/dpkg/status}) and apt the
 * package lists ({@code /var/lib/apt/lists}). An apt run touches them many times, so the callback comes once
 * nothing has changed for {@link #SETTLE_MILLIS}.
 */
final class PackageWatcher implements Runnable, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(PackageWatcher.class.getName());
    static final List<Path> DIRECTORIES = List.of(Path.of("/var/lib/dpkg"), Path.of("/var/lib/apt/lists"));
    static final long SETTLE_MILLIS = 5_000;

    private final List<Path> directories;
    private final long settleMillis;
    private final Runnable onChange;
    private final WatchService watcher;

    PackageWatcher(List<Path> directories, long settleMillis, Runnable onChange) throws IOException {
        this.directories = List.copyOf(directories);
        this.settleMillis = settleMillis;
        this.onChange = onChange;
        this.watcher = FileSystems.getDefault().newWatchService();
        for (Path dir : this.directories) {
            if (!Files.isDirectory(dir)) continue;
            dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        }
    }

    /** Starts watching on a daemon thread. */
    static AutoCloseable start(List<Path> directories, long settleMillis, Runnable onChange) {
        try {
            PackageWatcher watcher = new PackageWatcher(directories, settleMillis, onChange);
            Thread thread = new Thread(watcher, "package-watch");
            thread.setDaemon(true);
            thread.start();
            return watcher;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot watch " + directories, e);
            return () -> {};
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                WatchKey key = watcher.take();
                if (!relevant(key)) continue;
                // Wait until the changes have settled.
                WatchKey next;
                while ((next = watcher.poll(settleMillis, TimeUnit.MILLISECONDS)) != null) {
                    relevant(next);
                }
                onChange.run();
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // closed
        }
    }

    /**
     * Takes the events of a key; in /var/lib/dpkg only the status file counts (dpkg also writes its lock and
     * the updates directory there).
     */
    private boolean relevant(WatchKey key) {
        Path dir = (Path) key.watchable();
        boolean relevant = false;
        for (var event : key.pollEvents()) {
            if (!(event.context() instanceof Path name)) {
                relevant = true;
            } else if (!dir.getFileName().toString().equals("dpkg") || name.toString().equals("status")) {
                relevant = true;
            }
        }
        key.reset();
        return relevant;
    }

    @Override
    public void close() {
        try {
            watcher.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Closing the package watch failed", e);
        }
    }
}
