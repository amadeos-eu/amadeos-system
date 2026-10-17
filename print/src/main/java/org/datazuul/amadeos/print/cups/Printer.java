package org.datazuul.amadeos.print.cups;

import java.util.List;

/**
 * A CUPS printer or class.
 *
 * @param name          the queue name, e.g. {@code HP_LaserJet}
 * @param info          the description the administrator gave it; may be empty
 * @param reasons       {@code printer-state-reasons} except "none", e.g. {@code media-empty-error}
 * @param stateMessage  what the printer or its driver says, e.g. "Waiting for printer to become available."
 */
public record Printer(String name, String info, String location, String makeAndModel, State state,
                      String stateMessage, List<String> reasons, boolean accepting, String uri, int jobCount,
                      boolean isDefault) {
    public Printer {
        reasons = List.copyOf(reasons);
    }

    /** {@code printer-state}: 3 idle, 4 processing, 5 stopped. */
    public enum State {
        IDLE, PROCESSING, STOPPED, UNKNOWN;

        public static State of(int ippValue) {
            return switch (ippValue) {
                case 3 -> IDLE;
                case 4 -> PROCESSING;
                case 5 -> STOPPED;
                default -> UNKNOWN;
            };
        }
    }

    /** The name to show: the description if there is one, else the queue name. */
    public String title() {
        return info.isBlank() ? name : info;
    }

    public Printer withDefault(boolean value) {
        return new Printer(name, info, location, makeAndModel, state, stateMessage, reasons, accepting, uri, jobCount,
                value);
    }
}
