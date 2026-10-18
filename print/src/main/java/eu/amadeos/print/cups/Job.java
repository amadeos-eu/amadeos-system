package eu.amadeos.print.cups;

import java.time.Instant;
import java.util.Optional;

/**
 * A print job.
 *
 * @param printer the queue name, from {@code job-printer-uri}
 * @param sizeKb  {@code job-k-octets}, 0 if unknown
 * @param created when it was submitted, empty if unknown
 */
public record Job(int id, String name, String user, String printer, State state, int sizeKb,
                  Optional<Instant> created) {

    /** {@code job-state}, RFC 8011 5.3.7. */
    public enum State {
        PENDING, HELD, PROCESSING, STOPPED, CANCELED, ABORTED, COMPLETED, UNKNOWN;

        public static State of(int ippValue) {
            return switch (ippValue) {
                case 3 -> PENDING;
                case 4 -> HELD;
                case 5 -> PROCESSING;
                case 6 -> STOPPED;
                case 7 -> CANCELED;
                case 8 -> ABORTED;
                case 9 -> COMPLETED;
                default -> UNKNOWN;
            };
        }

        /** Canceled, aborted and completed jobs are history: nothing can be done with them any more. */
        public boolean finished() {
            return this == CANCELED || this == ABORTED || this == COMPLETED;
        }
    }

    public boolean cancellable() {
        return !state.finished();
    }

    public boolean holdable() {
        return state == State.PENDING;
    }

    public boolean releasable() {
        return state == State.HELD;
    }
}
