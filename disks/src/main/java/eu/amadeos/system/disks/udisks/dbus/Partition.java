package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.Partition}. */
@DBusInterfaceName("org.freedesktop.UDisks2.Partition")
@SuppressWarnings("checkstyle:MethodName")
public interface Partition extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Partition";

    void Delete(Map<String, Variant<?>> options);

    void Resize(UInt64 size, Map<String, Variant<?>> options);

    void SetName(String name, Map<String, Variant<?>> options);
}
