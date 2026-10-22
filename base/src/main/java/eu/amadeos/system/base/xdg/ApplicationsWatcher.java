package eu.amadeos.system.base.xdg;

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
 * Watches the directories of the desktop entries ({@link XdgApplicationManager#getApplicationDirectories()}), so
 * that the front panel's drawers follow programs that are installed or removed. A package installation writes
 * several entries, so the callback comes once nothing has changed for the settle time.
 */
public final class ApplicationsWatcher implements Runnable, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ApplicationsWatcher.class.getName());
    /** dpkg and update-desktop-database touch the directory several times in a row. */
    public static final long SETTLE_MILLIS = 2_000;

    private final long settleMillis;
    private final Runnable onChange;
    private final WatchService watcher;

    private ApplicationsWatcher(List<Path> directories, long settleMillis, Runnable onChange) throws IOException {
        this.settleMillis = settleMillis;
        this.onChange = onChange;
        this.watcher = FileSystems.getDefault().newWatchService();
        for (Path dir : directories) {
            // A directory that does not exist yet (~/.local/share/applications) is left out.
            if (!Files.isDirectory(dir)) continue;
            dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        }
    }

    /** Starts watching on a daemon thread; {@code onChange} is called on that thread. */
    public static AutoCloseable start(List<Path> directories, long settleMillis, Runnable onChange) {
        try {
            ApplicationsWatcher watcher = new ApplicationsWatcher(directories, settleMillis, onChange);
            Thread thread = new Thread(watcher, "applications-watch");
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
                take(watcher.take());
                // Wait until the changes have settled.
                WatchKey next;
                while ((next = watcher.poll(settleMillis, TimeUnit.MILLISECONDS)) != null) {
                    take(next);
                }
                try {
                    onChange.run();
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Following the desktop entries failed", e);
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // closed
        }
    }

    private static void take(WatchKey key) {
        key.pollEvents();
        key.reset();
    }

    @Override
    public void close() {
        try {
            watcher.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Closing the watch of the desktop entries failed", e);
        }
    }
}
