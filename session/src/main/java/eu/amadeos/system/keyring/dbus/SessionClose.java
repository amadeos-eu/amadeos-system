package eu.amadeos.system.keyring.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** A session of the Secret Service, closed when it is no longer needed. */
@DBusInterfaceName("org.freedesktop.Secret.Session")
public interface SessionClose extends DBusInterface {
    @DBusMemberName("Close")
    void close();
}
