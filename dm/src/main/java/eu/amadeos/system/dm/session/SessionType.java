package eu.amadeos.system.dm.session;

/**
 * The kinds of session the greeter offers, in its order. {@link #DESKTOP} keeps the desktop look of the last
 * session; {@link #CLASSIC} and {@link #HORIZON} set the look first: amadeos-session writes it to
 * {@code ~/.config/amadeos/look.conf} (environment variable {@code AMADEOS_SESSION}), as Settings &gt; Desktop
 * look does, so it stays for later logins.
 */
public enum SessionType {
    DESKTOP("desktop", "amadeOS Desktop"),
    CLASSIC("classic", "amadeOS Classic (CDE)"),
    HORIZON("horizon", "amadeOS Horizon"),
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

    /** The greeter's labels, in the order of the session field. */
    public static String[] labels() {
        return java.util.Arrays.stream(values()).map(SessionType::label).toArray(String[]::new);
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
