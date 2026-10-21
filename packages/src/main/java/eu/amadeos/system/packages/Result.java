package eu.amadeos.system.packages;

/**
 * How an operation of the helper ended.
 *
 * @param status         the outcome
 * @param message        the reason when it did not succeed (apt's last error, …), empty otherwise
 * @param rebootRequired an upgraded package asks for a restart ({@code /run/reboot-required})
 */
public record Result(Status status, String message, boolean rebootRequired) {

    public enum Status {
        /** Done; for {@code upgrade --security-only} possibly with nothing to do. */
        DONE,
        /** Another package operation (unattended-upgrades, apt in a terminal) holds the dpkg lock. */
        LOCKED,
        /** The authentication was dismissed or polkit refused it. */
        NOT_AUTHORIZED,
        /** apt failed, or the helper could not be started. */
        FAILED
    }

    public boolean success() {
        return status == Status.DONE;
    }

    static Result failed(String message) {
        return new Result(Status.FAILED, message, false);
    }
}
