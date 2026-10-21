package eu.amadeos.system.polkit.dbus;

import java.util.Map;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

/**
 * polkit's subjects and identities, both {@code (sa{sv})}: a kind ("unix-session", "unix-process",
 * "unix-user", ...) and its details ("session-id", "pid", "uid", ...).
 */
public final class Subject extends Struct {
    @Position(0)
    public final String kind;
    @Position(1)
    public final Map<String, Variant<?>> details;

    public Subject(String kind, Map<String, Variant<?>> details) {
        this.kind = kind;
        this.details = details;
    }

    /** The login session the agent serves (logind's session id, {@code $XDG_SESSION_ID}). */
    public static Subject session(String id) {
        return new Subject("unix-session", Map.of("session-id", new Variant<>(id)));
    }

    /**
     * One process and its children, like {@code pkttyagent --process}; the start time (clock ticks since boot,
     * field 22 of {@code /proc/PID/stat}) tells it apart from a later process with the same id.
     */
    public static Subject process(long pid, long startTime) {
        return new Subject("unix-process", Map.of("pid", new Variant<>(new UInt32(pid)),
                "start-time", new Variant<>(new UInt64(startTime))));
    }

    /** The user id of a "unix-user" identity, or -1 for any other kind. */
    public long uid() {
        if (!"unix-user".equals(kind)) return -1;
        Variant<?> uid = details.get("uid");
        return uid != null && uid.getValue() instanceof Number number ? number.longValue() : -1;
    }
}
