package eu.amadeos.system.wm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * The dialogs that come up with a window when it is raised: its shown dialogs (WM_TRANSIENT_FOR) and their
 * dialogs. Without this, a click on a window raised it over its own modal dialog: the dialog was hidden and the
 * program looked frozen, for example the "imported" message of Contacts.
 */
public final class DialogStack {
    private DialogStack() {
    }

    /**
     * @param bottomToTop the windows in stacking order, the bottom one first
     * @param win         the window that was raised
     * @param owners      the window each dialog belongs to
     * @param shown       whether a window is on the screen (mapped, not minimized, on this workspace)
     * @return the dialogs to raise, in this order, so that they keep their order among themselves
     */
    public static List<Long> above(List<Long> bottomToTop, long win, Map<Long, Long> owners, LongPredicate shown) {
        Set<Long> family = new HashSet<>();
        family.add(win);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (long id : bottomToTop) {
                Long owner = owners.get(id);
                if (owner != null && family.contains(owner) && !family.contains(id) && shown.test(id)) {
                    family.add(id);
                    grew = true;
                }
            }
        }
        List<Long> result = new ArrayList<>();
        for (long id : bottomToTop) {
            if (id != win && family.contains(id)) result.add(id);
        }
        return result;
    }
}
