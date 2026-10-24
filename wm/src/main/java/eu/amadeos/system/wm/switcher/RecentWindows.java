package eu.amadeos.system.wm.switcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

/**
 * The order in which windows were last active, the most recent first. Alt+Tab lists the windows in this
 * order, so a single Alt+Tab always goes back to the window that was active before.
 */
public final class RecentWindows {
    private final SequencedSet<Long> recent = new LinkedHashSet<>();

    /** The window just became active. */
    public void touch(long win) {
        recent.remove(win);
        recent.addFirst(win);
    }

    public void remove(long win) {
        recent.remove(win);
    }

    /**
     * The candidates in switcher order: those that were active at some point, most recent first, then the
     * ones that never were, in stacking order.
     *
     * @param topToBottom the windows the switcher may show, the topmost first
     */
    public List<Long> order(List<Long> topToBottom) {
        List<Long> result = new ArrayList<>(topToBottom.size());
        for (long win : recent) {
            if (topToBottom.contains(win)) result.add(win);
        }
        for (long win : topToBottom) {
            if (!recent.contains(win)) result.add(win);
        }
        return result;
    }
}
