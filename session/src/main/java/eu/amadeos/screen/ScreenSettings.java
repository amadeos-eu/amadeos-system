package eu.amadeos.screen;

/**
 * The "Screen" settings of the Style Manager, as in CDE: a screen saver that starts after some minutes
 * without input and a screen lock that starts after some (usually more) minutes.
 *
 * @param saverEnabled     start the screen saver when the desktop is idle
 * @param saverMinutes     minutes without input before the saver starts
 * @param lockEnabled      lock the screen when the desktop is idle
 * @param lockMinutes      minutes without input before the screen locks
 * @param module           id of the saver module, or {@link #RANDOM}
 */
public record ScreenSettings(boolean saverEnabled, int saverMinutes, boolean lockEnabled, int lockMinutes,
                             String module) {

    public static final String RANDOM = "random";
    public static final String DEFAULT_MODULE = "qix";
    public static final int MAX_MINUTES = 240;
    public static final ScreenSettings DEFAULTS = new ScreenSettings(true, 10, true, 15, DEFAULT_MODULE);

    public ScreenSettings {
        saverMinutes = clamp(saverMinutes);
        lockMinutes = clamp(lockMinutes);
        module = module == null || module.isBlank() ? DEFAULT_MODULE : module.strip();
    }

    private static int clamp(int minutes) {
        return Math.max(1, Math.min(MAX_MINUTES, minutes));
    }

    public long saverMillis() {
        return saverMinutes * 60_000L;
    }

    public long lockMillis() {
        return lockMinutes * 60_000L;
    }

    public ScreenSettings withModule(String id) {
        return new ScreenSettings(saverEnabled, saverMinutes, lockEnabled, lockMinutes, id);
    }
}
