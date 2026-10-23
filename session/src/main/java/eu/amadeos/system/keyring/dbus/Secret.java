package eu.amadeos.system.keyring.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

/** A secret of the Secret Service API, {@code (oayays)}: the session, its parameters, the value and its type. */
public final class Secret extends Struct {
    @Position(0)
    public final DBusPath session;
    @Position(1)
    public final byte[] parameters;
    @Position(2)
    public final byte[] value;
    @Position(3)
    public final String contentType;

    public Secret(DBusPath session, byte[] parameters, byte[] value, String contentType) {
        this.session = session;
        this.parameters = parameters;
        this.value = value;
        this.contentType = contentType;
    }

    /** A password in a session opened with the "plain" algorithm, which the bus between two local processes carries. */
    public static Secret password(DBusPath session, byte[] utf8) {
        return new Secret(session, new byte[0], utf8, "text/plain; charset=utf8");
    }
}
