package eu.amadeos.system.disks.service;

/**
 * How far a long step is: a udisks job (formatting with overwrite, a self-test) or an image being written.
 *
 * @param fraction 0 to 1, or -1 when not known
 * @param done     bytes done, or -1
 * @param total    bytes in all, or -1
 * @param rate     bytes per second, or -1
 */
public record Progress(double fraction, long done, long total, long rate) {
    public static final Progress UNKNOWN = new Progress(-1, -1, -1, -1);

    /** Seconds still to go at the current rate, or -1. */
    public long secondsLeft() {
        if (rate <= 0 || total < 0 || done < 0) return -1;
        return (total - done) / rate;
    }
}
