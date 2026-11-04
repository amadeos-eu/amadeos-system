package eu.amadeos.system.software.ui;

import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.ChangeSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the user marked to install, update or remove, across all tabs of the window: the check boxes of the
 * Updates tab and of the catalog tabs show and change the same marks, and "Apply changes" does them all in
 * one apt run. Waiting updates are marked when they first appear; one the user unmarks stays unmarked while
 * it waits. Used on the event dispatch thread only.
 */
final class Marks {

    enum Mark { INSTALL, UPDATE, REMOVE }

    private final Map<String, Mark> marks = new LinkedHashMap<>();
    /** The waiting updates seen so far, so that one the user unmarked is not marked again. */
    private final Set<String> knownUpdates = new HashSet<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** Called after every change of the marks. */
    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    Optional<Mark> get(String name) {
        return Optional.ofNullable(marks.get(name));
    }

    boolean marked(String name) {
        return marks.containsKey(name);
    }

    void mark(String name, Mark mark) {
        if (marks.put(name, mark) != mark) changed();
    }

    void unmark(String name) {
        if (marks.remove(name) != null) changed();
    }

    /** Marks or unmarks several packages with one notice to the listeners. */
    void markAll(Collection<String> names, Mark mark) {
        boolean changed = false;
        for (String name : names) {
            if (mark == null) {
                changed |= marks.remove(name) != null;
            } else {
                changed |= marks.put(name, mark) != mark;
            }
        }
        if (changed) changed();
    }

    void clear() {
        if (marks.isEmpty()) return;
        marks.clear();
        changed();
    }

    /** Forgets the marks of these packages, after they were applied. */
    void forget(Collection<String> names) {
        boolean changed = false;
        for (String name : names) changed |= marks.remove(name) != null;
        if (changed) changed();
    }

    /**
     * The waiting updates, read again: new ones are marked, marks for updates that no longer wait go (the
     * package was updated elsewhere).
     */
    void updatesRead(Collection<String> updates) {
        boolean changed = false;
        for (String name : updates) {
            if (knownUpdates.add(name) && !marks.containsKey(name)) {
                marks.put(name, Mark.UPDATE);
                changed = true;
            }
        }
        Set<String> gone = new HashSet<>(knownUpdates);
        gone.removeAll(updates);
        knownUpdates.removeAll(gone);
        changed |= marks.entrySet().removeIf(e -> e.getValue() == Mark.UPDATE && gone.contains(e.getKey()));
        if (changed) changed();
    }

    /**
     * The catalog, read again: marks that no longer fit the package's state go (installed meanwhile, removed
     * meanwhile, already updated). Packages the catalog does not know keep their marks.
     */
    void catalogRead(Collection<CatalogEntry> catalog) {
        boolean changed = false;
        for (CatalogEntry e : catalog) {
            Mark mark = marks.get(e.name());
            if (mark != null && !allowed(e).contains(mark)) {
                marks.remove(e.name());
                changed = true;
            }
        }
        if (changed) changed();
    }

    /** The marks a package can take in its state. */
    static Set<Mark> allowed(CatalogEntry e) {
        if (!e.installed()) {
            return e.availableVersion().isEmpty() ? Set.of() : Set.of(Mark.INSTALL);
        }
        return e.upgradable() ? Set.of(Mark.UPDATE, Mark.REMOVE) : Set.of(Mark.REMOVE);
    }

    /** What ticking the check box of a package means: install it, update it, or else remove it. */
    static Optional<Mark> defaultMark(CatalogEntry e) {
        if (!e.installed()) return e.availableVersion().isEmpty() ? Optional.empty() : Optional.of(Mark.INSTALL);
        return Optional.of(e.upgradable() ? Mark.UPDATE : Mark.REMOVE);
    }

    List<String> names(Mark mark) {
        return marks.entrySet().stream().filter(e -> e.getValue() == mark).map(Map.Entry::getKey).toList();
    }

    int count(Mark mark) {
        return (int) marks.values().stream().filter(m -> m == mark).count();
    }

    int size() {
        return marks.size();
    }

    boolean isEmpty() {
        return marks.isEmpty();
    }

    /** All marks as one change for apt, each group sorted by name. */
    ChangeSet changeSet() {
        return new ChangeSet(sorted(Mark.INSTALL), sorted(Mark.UPDATE), sorted(Mark.REMOVE));
    }

    private List<String> sorted(Mark mark) {
        return names(mark).stream().sorted().toList();
    }

    private void changed() {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
}
