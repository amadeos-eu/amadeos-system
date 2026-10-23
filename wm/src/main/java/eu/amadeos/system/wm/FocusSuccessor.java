package eu.amadeos.system.wm;

import java.util.List;
import java.util.function.LongPredicate;

/**
 * Which window becomes active when the active one goes away: the window it was a dialog of, if that can take
 * the focus, else the topmost window that can. Taking the bottom window instead would raise it over the
 * dialog's owner: closing a date picker in a contact editor would then hide the editor behind the main window.
 */
public final class FocusSuccessor {
    private FocusSuccessor() {
    }

    /**
     * @param bottomToTop the remaining windows in stacking order, the bottom one first
     * @param owner       the window the closed one was a dialog of (WM_TRANSIENT_FOR), 0 if none
     * @param eligible    whether a window can take the focus (mapped, not minimized, on this workspace)
     * @return the window to activate, 0 if there is none
     */
    public static long choose(List<Long> bottomToTop, long owner, LongPredicate eligible) {
        if (owner != 0 && bottomToTop.contains(owner) && eligible.test(owner)) return owner;
        for (int i = bottomToTop.size() - 1; i >= 0; i--) {
            long id = bottomToTop.get(i);
            if (eligible.test(id)) return id;
        }
        return 0;
    }
}
