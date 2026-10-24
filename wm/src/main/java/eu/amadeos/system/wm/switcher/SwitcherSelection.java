package eu.amadeos.system.wm.switcher;

import java.util.List;

/** The windows of one Alt+Tab round and which of them is selected; moving past either end wraps around. */
public final class SwitcherSelection {
    private final List<SwitcherEntry> entries;
    private int selected;

    /**
     * Starts a round. Alt+Tab selects the second window, the one that was active before the current one;
     * Alt+Shift+Tab the last.
     */
    public SwitcherSelection(List<SwitcherEntry> entries, boolean backwards) {
        if (entries.isEmpty()) throw new IllegalArgumentException("no windows to switch between");
        this.entries = List.copyOf(entries);
        this.selected = 0;
        move(backwards ? -1 : 1);
    }

    public List<SwitcherEntry> entries() {
        return entries;
    }

    public int selectedIndex() {
        return selected;
    }

    public SwitcherEntry selected() {
        return entries.get(selected);
    }

    /** Moves the selection by {@code delta} entries, wrapping around. */
    public void move(int delta) {
        selected = Math.floorMod(selected + delta, entries.size());
    }

    /** Selects an entry directly, as a mouse click or hover does; out-of-range indexes are ignored. */
    public void select(int index) {
        if (index >= 0 && index < entries.size()) selected = index;
    }
}
