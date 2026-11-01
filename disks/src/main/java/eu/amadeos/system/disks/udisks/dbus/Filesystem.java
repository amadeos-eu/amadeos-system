package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.Filesystem}. */
@DBusInterfaceName("org.freedesktop.UDisks2.Filesystem")
@SuppressWarnings("checkstyle:MethodName")
public interface Filesystem extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Filesystem";

    String Mount(Map<String, Variant<?>> options);

    void Unmount(Map<String, Variant<?>> options);

    void SetLabel(String label, Map<String, Variant<?>> options);

    boolean Check(Map<String, Variant<?>> options);

    boolean Repair(Map<String, Variant<?>> options);

    void Resize(UInt64 size, Map<String, Variant<?>> options);
}
