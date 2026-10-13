package org.datazuul.amadeos.screen;

/**
 * Decides, from the idle time the X server reports, when the screen saver starts and stops and when the
 * screen locks. Pure logic without X11 or Swing, so it can be tested.
 *
 * <ul>
 *   <li>Saver: starts after {@code saverMinutes} without input, stops at the next input.</li>
 *   <li>Lock: starts after {@code lockMinutes} without input (also while the saver runs) and replaces
 *       the saver; the lock process itself ends only with the correct password.</li>
 *   <li>While the screen is locked nothing else happens.</li>
 * </ul>
 */
public final class IdlePolicy {

    public enum Action {NONE, START_SAVER, STOP_SAVER, LOCK}

    private boolean saverActive;
    private boolean lockRequested;
    private long lastIdle;

    /**
     * @param settings   current settings
     * @param idleMillis time since the last input, from the X server
     * @param locked     true while a screen lock process holds the lock selection
     */
    public Action next(ScreenSettings settings, long idleMillis, boolean locked) {
        boolean input = idleMillis < lastIdle;
        lastIdle = idleMillis;
        if (input) lockRequested = false;

        if (locked) {
            lockRequested = false;
            if (saverActive) {
                saverActive = false;
                return Action.STOP_SAVER;
            }
            return Action.NONE;
        }
        if (settings.lockEnabled() && !lockRequested && idleMillis >= settings.lockMillis()) {
            lockRequested = true;
            saverActive = false;
            return Action.LOCK;
        }
        if (saverActive) {
            boolean disabled = !settings.saverEnabled();
            if (input || disabled) {
                saverActive = false;
                return Action.STOP_SAVER;
            }
            return Action.NONE;
        }
        if (settings.saverEnabled() && !lockRequested && idleMillis >= settings.saverMillis()) {
            saverActive = true;
            return Action.START_SAVER;
        }
        return Action.NONE;
    }

    public boolean saverActive() {
        return saverActive;
    }
}
