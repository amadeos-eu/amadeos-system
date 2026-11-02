package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.Drive}. */
@DBusInterfaceName("org.freedesktop.UDisks2.Drive")
@SuppressWarnings("checkstyle:MethodName")
public interface Drive extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Drive";

    void Eject(Map<String, Variant<?>> options);

    void PowerOff(Map<String, Variant<?>> options);
}
