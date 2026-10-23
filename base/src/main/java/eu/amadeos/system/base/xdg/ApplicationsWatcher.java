package eu.amadeos.system.base.xdg;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the directories of the desktop entries ({@link XdgApplicationManager#getApplicationDirectories()}), so
 * that the front panel's drawers follow programs that are installed or removed. A package installation writes
 * several entries, so the callback comes once nothing has changed for the settle time. A directory that does not
 * exist yet ({@code ~/.local/share/applications} of a new user) is watched for through its nearest existing
 * parent, and from the moment it is created like the others.
 */
public final class ApplicationsWatcher implements Runnable, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ApplicationsWatcher.class.getName());
    /** dpkg and update-desktop-database touch the directory several times in a row. */
    public static final long SETTLE_MILLIS = 2_000;

    private final long settleMillis;
    private final Runnable onChange;
    private final WatchService watcher;
    /** The directories that do not exist yet. */
    private final List<Path> missing = new ArrayList<>();
    /** The parents watched for them, which report only that something was created in them. */
    private final Map<WatchKey, Path> parents = new HashMap<>();
    /** Parent keys given up, whose last events may still be queued. */
    private final Set<WatchKey> retired = new HashSet<>();

    private ApplicationsWatcher(List<Path> directories, long settleMillis, Runnable onChange) throws IOException {
        this.settleMillis = settleMillis;
        this.onChange = onChange;
        this.watcher = FileSystems.getDefault().newWatchService();
        for (Path dir : directories) {
            if (Files.isDirectory(dir)) {
                register(dir);
            } else {
                missing.add(dir.toAbsolutePath().normalize());
            }
        }
        watchParents();
    }

    private void register(Path dir) throws IOException {
        dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
    }

    /**
     * Registers the directories of {@link #missing} that exist by now, and watches the nearest existing parent of
     * each of the others. Returns whether a directory appeared.
     */
    private boolean watchParents() throws IOException {
        boolean appeared = false;
        parents.keySet().forEach(WatchKey::cancel);
        retired.addAll(parents.keySet());
        parents.clear();
        for (var it = missing.iterator(); it.hasNext(); ) {
            Path dir = it.next();
            // A level created while the parent was being registered would go unnoticed: look again until the
            // nearest existing parent stays the same once it is watched.
            Path watched = null;
            Path parent;
            while (!Files.isDirectory(dir) && (parent = nearestExisting(dir)) != null && !parent.equals(watched)) {
                if (!parents.containsValue(parent)) {
                    parents.put(parent.register(watcher, StandardWatchEventKinds.ENTRY_CREATE), parent);
                }
                watched = parent;
            }
            if (Files.isDirectory(dir)) {
                register(dir);
                it.remove();
                appeared = true;
            }
        }
        return appeared;
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
                if (!take(watcher.take())) continue;
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

    private static Path nearestExisting(Path dir) {
        Path parent = dir.getParent();
        while (parent != null && !Files.isDirectory(parent)) {
            parent = parent.getParent();
        }
        return parent;
    }

    /**
     * Takes the events of a key; true when the desktop entries may have changed: an event in a watched directory,
     * or a missing directory that appeared. Something new in a parent (a file in the home folder) is not.
     */
    private boolean take(WatchKey key) {
        key.pollEvents();
        key.reset();
        if (retired.remove(key)) {
            return false;
        }
        if (!parents.containsKey(key)) {
            return true;
        }
        try {
            return watchParents();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot watch " + missing, e);
            return false;
        }
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
