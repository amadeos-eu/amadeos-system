package eu.amadeos.system.base.power;

/**
 * What the front panel shows about the battery: the charge of all system batteries together, whether they
 * charge or run the computer, and how long that takes at the present rate.
 *
 * @param percent     charge 0-100
 * @param state       what the batteries are doing
 * @param minutesLeft minutes until empty (discharging) or full (charging), or -1 when the kernel reports no rate
 */
public record BatteryStatus(int percent, State state, int minutesLeft) {

    public enum State {
        /** The computer runs on the battery. */
        DISCHARGING,
        CHARGING,
        /** Plugged in and full. */
        FULL,
        /** Plugged in, but the battery does not charge (e.g. a charge limit). */
        NOT_CHARGING
    }

    public BatteryStatus {
        percent = Math.max(0, Math.min(100, percent));
        if (state == null) state = State.DISCHARGING;
    }

    /** Whether the computer runs on the battery rather than on mains power. */
    public boolean onBattery() {
        return state == State.DISCHARGING;
    }
}
