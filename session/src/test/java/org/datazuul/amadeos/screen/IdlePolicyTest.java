package org.datazuul.amadeos.screen;

import org.datazuul.amadeos.screen.IdlePolicy.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdlePolicyTest {
    private static final long MIN = 60_000;
    private static final ScreenSettings SAVER_5_LOCK_10 = new ScreenSettings(true, 5, true, 10, "qix");

    @Test
    void saverStartsAfterIdleTimeAndStopsOnInput() {
        IdlePolicy policy = new IdlePolicy();
        assertEquals(Action.NONE, policy.next(SAVER_5_LOCK_10, 4 * MIN, false));
        assertEquals(Action.START_SAVER, policy.next(SAVER_5_LOCK_10, 5 * MIN, false));
        assertTrue(policy.saverActive());
        assertEquals(Action.NONE, policy.next(SAVER_5_LOCK_10, 5 * MIN + 1000, false));
        assertEquals(Action.STOP_SAVER, policy.next(SAVER_5_LOCK_10, 200, false), "idle time dropped: input");
        assertFalse(policy.saverActive());
        assertEquals(Action.NONE, policy.next(SAVER_5_LOCK_10, 1200, false));
    }

    @Test
    void lockReplacesTheSaverOnceUntilInput() {
        IdlePolicy policy = new IdlePolicy();
        assertEquals(Action.START_SAVER, policy.next(SAVER_5_LOCK_10, 5 * MIN, false));
        assertEquals(Action.LOCK, policy.next(SAVER_5_LOCK_10, 10 * MIN, false));
        assertFalse(policy.saverActive());
        // The lock process may need a moment to take the selection: no second LOCK, no new saver.
        assertEquals(Action.NONE, policy.next(SAVER_5_LOCK_10, 10 * MIN + 1000, false));
        assertEquals(Action.NONE, policy.next(SAVER_5_LOCK_10, 10 * MIN + 2000, true));
        // Unlocked by typing the password: idle time small again, the cycle starts over.
        assertEquals(Action.NONE, policy.next(SAVER_5_LOCK_10, 500, false));
        assertEquals(Action.START_SAVER, policy.next(SAVER_5_LOCK_10, 5 * MIN, false));
    }

    @Test
    void nothingHappensWhileLockedAndARunningSaverStops() {
        IdlePolicy policy = new IdlePolicy();
        assertEquals(Action.START_SAVER, policy.next(SAVER_5_LOCK_10, 6 * MIN, false));
        assertEquals(Action.STOP_SAVER, policy.next(SAVER_5_LOCK_10, 7 * MIN, true), "locked from the panel");
        assertEquals(Action.NONE, policy.next(SAVER_5_LOCK_10, 30 * MIN, true));
    }

    @Test
    void disabledFeaturesDoNothing() {
        ScreenSettings off = new ScreenSettings(false, 5, false, 10, "qix");
        IdlePolicy policy = new IdlePolicy();
        assertEquals(Action.NONE, policy.next(off, 60 * MIN, false));

        ScreenSettings lockOnly = new ScreenSettings(false, 5, true, 10, "qix");
        assertEquals(Action.NONE, new IdlePolicy().next(lockOnly, 9 * MIN, false));
        assertEquals(Action.LOCK, new IdlePolicy().next(lockOnly, 10 * MIN, false));
    }

    @Test
    void switchingTheSaverOffStopsIt() {
        IdlePolicy policy = new IdlePolicy();
        assertEquals(Action.START_SAVER, policy.next(SAVER_5_LOCK_10, 5 * MIN, false));
        ScreenSettings saverOff = new ScreenSettings(false, 5, true, 10, "qix");
        assertEquals(Action.STOP_SAVER, policy.next(saverOff, 5 * MIN + 1000, false));
    }

    @Test
    void lockEarlierThanSaverLocksWithoutSaver() {
        ScreenSettings lockFirst = new ScreenSettings(true, 10, true, 5, "qix");
        IdlePolicy policy = new IdlePolicy();
        assertEquals(Action.LOCK, policy.next(lockFirst, 5 * MIN, false));
        assertEquals(Action.NONE, policy.next(lockFirst, 10 * MIN, false), "no saver behind a pending lock");
    }
}
