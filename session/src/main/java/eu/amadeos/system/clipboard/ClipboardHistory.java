package eu.amadeos.system.clipboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The entries of the clipboard history, newest first, in memory only. A copy that is already in the
 * history moves to the top instead of appearing twice. The history keeps at most {@link #limit()} entries
 * and drops the oldest images first once the images together take more than the memory budget.
 *
 * <p>Thread-safe: the clipboard manager adds from its worker thread, the history window reads and removes on
 * the event dispatch thread.
 */
public final class ClipboardHistory {
    private final List<ClipEntry> entries = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int limit;
    private final long imageBudget;

    public ClipboardHistory(int limit, long imageBudget) {
        this.limit = Math.max(1, limit);
        this.imageBudget = imageBudget;
    }

    /**
     * Adds a copy at the top and returns the entry the history now holds for it: {@code entry} itself, or,
     * if it was too big for the image budget, nothing.
     */
    // --8<-- [start:add]
    public Optional<ClipEntry> add(ClipEntry entry) {
        synchronized (this) {
            entries.removeIf(entry::sameContent);
            if (entry.imageBytes() > imageBudget) return Optional.empty();
            entries.addFirst(entry);
            trim();
        }
        changed();
        return Optional.of(entry);
    }
    // --8<-- [end:add]

    public boolean remove(ClipEntry entry) {
        boolean removed;
        synchronized (this) {
            removed = entries.remove(entry);
        }
        if (removed) changed();
        return removed;
    }

    public void clear() {
        synchronized (this) {
            if (entries.isEmpty()) return;
            entries.clear();
        }
        changed();
    }

    public synchronized boolean contains(ClipEntry entry) {
        return entries.contains(entry);
    }

    /** The entries, newest first. */
    public synchronized List<ClipEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized Optional<ClipEntry> latest() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getFirst());
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        boolean trimmed;
        synchronized (this) {
            this.limit = Math.max(1, limit);
            int before = entries.size();
            trim();
            trimmed = entries.size() != before;
        }
        if (trimmed) changed();
    }

    /** Runs {@code listener} after every change, on the thread that made it. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void trim() {
        while (entries.size() > limit) entries.removeLast();
        long images = 0;
        for (ClipEntry e : entries) images += e.imageBytes();
        for (int i = entries.size() - 1; i > 0 && images > imageBudget; i--) {
            ClipEntry e = entries.get(i);
            if (e.imageBytes() > 0) {
                images -= e.imageBytes();
                entries.remove(i);
            }
        }
    }

    private void changed() {
        listeners.forEach(Runnable::run);
    }
}
