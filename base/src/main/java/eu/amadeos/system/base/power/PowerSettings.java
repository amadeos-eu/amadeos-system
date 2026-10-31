package eu.amadeos.system.base.power;

/**
 * The Settings' "Power" page: when the screen goes dark and the computer suspends while nobody uses it, on
 * battery and when plugged in, what closing the lid and the power button do, and what happens when the
 * battery is nearly empty. The session's screen and power daemon carries them out.
 *
 * @param battery        what happens while the computer runs on its battery
 * @param mains          what happens while it is plugged in (and on computers without a battery)
 * @param powerKey       what the power button does
 * @param lidIgnoreDocked closing the lid does nothing while an external screen is connected
 * @param lockOnWake     lock the screen before the computer sleeps, so that it wakes up locked
 * @param warnPercent    battery charge at which a warning appears, 0 for none
 * @param lowPercent     battery charge at which {@code lowAction} happens
 * @param lowAction      what happens when the battery is nearly empty
 */
public record PowerSettings(Profile battery, Profile mains, PowerAction powerKey, boolean lidIgnoreDocked,
                            boolean lockOnWake, int warnPercent, int lowPercent, PowerAction lowAction) {

    /** Minutes for "never". */
    public static final int NEVER = 0;
    /** The minutes the page offers for the screen and for suspending. */
    public static final int[] MINUTES = {1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, NEVER};

    /**
     * @param screenOffMinutes minutes without input before the screen turns off, or {@link #NEVER}
     * @param suspendMinutes   minutes without input before the computer suspends, or {@link #NEVER}
     * @param lid              what closing the lid does
     */
    public record Profile(int screenOffMinutes, int suspendMinutes, PowerAction lid) {
        public Profile {
            screenOffMinutes = minutes(screenOffMinutes);
            suspendMinutes = minutes(suspendMinutes);
            lid = choose(lid, PowerAction.LID, PowerAction.SUSPEND);
        }

        public long screenOffMillis() {
            return screenOffMinutes * 60_000L;
        }

        public long suspendMillis() {
            return suspendMinutes * 60_000L;
        }
    }

    public static final Profile BATTERY_DEFAULTS = new Profile(5, 15, PowerAction.SUSPEND);
    public static final Profile MAINS_DEFAULTS = new Profile(15, NEVER, PowerAction.SUSPEND);
    public static final PowerSettings DEFAULTS = new PowerSettings(BATTERY_DEFAULTS, MAINS_DEFAULTS, PowerAction.ASK,
            true, true, 10, 5, PowerAction.HIBERNATE);

    public PowerSettings {
        if (battery == null) battery = BATTERY_DEFAULTS;
        if (mains == null) mains = MAINS_DEFAULTS;
        powerKey = choose(powerKey, PowerAction.POWER_KEY, PowerAction.ASK);
        lowAction = choose(lowAction, PowerAction.LOW_BATTERY, PowerAction.HIBERNATE);
        lowPercent = Math.max(1, Math.min(30, lowPercent));
        warnPercent = warnPercent <= 0 ? 0 : Math.max(lowPercent + 1, Math.min(50, warnPercent));
    }

    /** The profile for the power source: the battery's while the computer runs on it. */
    public Profile profile(boolean onBattery) {
        return onBattery ? battery : mains;
    }

    public PowerSettings withBattery(Profile profile) {
        return new PowerSettings(profile, mains, powerKey, lidIgnoreDocked, lockOnWake, warnPercent, lowPercent, lowAction);
    }

    public PowerSettings withMains(Profile profile) {
        return new PowerSettings(battery, profile, powerKey, lidIgnoreDocked, lockOnWake, warnPercent, lowPercent, lowAction);
    }

    private static int minutes(int minutes) {
        return minutes <= 0 ? NEVER : Math.min(minutes, 24 * 60);
    }

    private static PowerAction choose(PowerAction action, PowerAction[] allowed, PowerAction fallback) {
        if (action == null) return fallback;
        for (PowerAction a : allowed) {
            if (a == action) return action;
        }
        return fallback;
    }
}
