package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.Encrypted}: a LUKS container. */
@DBusInterfaceName("org.freedesktop.UDisks2.Encrypted")
@SuppressWarnings("checkstyle:MethodName")
public interface Encrypted extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Encrypted";

    DBusPath Unlock(String passphrase, Map<String, Variant<?>> options);

    void Lock(Map<String, Variant<?>> options);
}
