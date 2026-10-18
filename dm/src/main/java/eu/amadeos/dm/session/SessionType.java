package eu.amadeos.dm.session;

/**
 * The kinds of session the greeter offers.
 */
public enum SessionType {
    DESKTOP("desktop", "amadeOS CDE Desktop"),
    FAILSAFE("failsafe", "Failsafe Terminal");

    private final String id;
    private final String label;

    SessionType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Maps a greeter label or protocol id to a session type; anything unknown is the desktop. */
    public static SessionType from(String value) {
        for (SessionType type : values()) {
            if (type.id.equalsIgnoreCase(value) || type.label.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return DESKTOP;
    }
}
