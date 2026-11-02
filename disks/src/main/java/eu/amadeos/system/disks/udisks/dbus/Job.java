package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.Job}: a running operation (format, erase, self-test) with its progress. */
@DBusInterfaceName("org.freedesktop.UDisks2.Job")
@SuppressWarnings("checkstyle:MethodName")
public interface Job extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Job";

    void Cancel(Map<String, Variant<?>> options);
}
