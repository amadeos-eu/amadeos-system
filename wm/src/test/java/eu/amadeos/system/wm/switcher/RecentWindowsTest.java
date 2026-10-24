package eu.amadeos.system.wm.switcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecentWindowsTest {
    @Test
    void mostRecentlyActiveComeFirst() {
        RecentWindows recent = new RecentWindows();
        recent.touch(1);
        recent.touch(2);
        recent.touch(3);
        recent.touch(1);
        assertEquals(List.of(1L, 3L, 2L), recent.order(List.of(3L, 2L, 1L)));
    }

    @Test
    void neverActiveWindowsFollowInStackingOrder() {
        RecentWindows recent = new RecentWindows();
        recent.touch(5);
        assertEquals(List.of(5L, 7L, 6L), recent.order(List.of(7L, 5L, 6L)));
    }

    @Test
    void onlyCandidatesAreListed() {
        RecentWindows recent = new RecentWindows();
        recent.touch(1);
        recent.touch(2);
        recent.remove(2);
        recent.touch(9);
        // 9 is on another workspace: not a candidate.
        assertEquals(List.of(1L, 4L), recent.order(List.of(4L, 1L)));
    }
}
