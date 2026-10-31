package eu.amadeos.system.wm.hang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingTrackerTest {
    private final PingTracker tracker = new PingTracker(5000);

    @Test
    void aWindowThatAnswersInTimeIsNeverReported() {
        tracker.sent(7, 1000);
        assertFalse(tracker.shouldPing(7));
        assertEquals(List.of(), tracker.newlyNotResponding(5999));
        assertFalse(tracker.answered(7));
        assertTrue(tracker.shouldPing(7));
        assertEquals(List.of(), tracker.newlyNotResponding(20000));
    }

    @Test
    void aSilentWindowIsReportedOnceAndClearedByItsAnswer() {
        tracker.sent(7, 1000);
        tracker.sent(7, 3000); // a second ping keeps the first time
        assertEquals(List.of(7L), tracker.newlyNotResponding(6000));
        assertTrue(tracker.isNotResponding(7));
        assertEquals(List.of(), tracker.newlyNotResponding(9000));
        assertTrue(tracker.answered(7));
        assertFalse(tracker.isNotResponding(7));
    }

    @Test
    void forgottenWindowsAreNotReported() {
        tracker.sent(7, 0);
        tracker.sent(9, 0);
        tracker.forget(7);
        assertEquals(List.of(9L), tracker.newlyNotResponding(5000));
    }
}
