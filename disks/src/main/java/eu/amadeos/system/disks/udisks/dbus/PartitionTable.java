package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.PartitionTable}. */
@DBusInterfaceName("org.freedesktop.UDisks2.PartitionTable")
@SuppressWarnings("checkstyle:MethodName")
public interface PartitionTable extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.PartitionTable";

    DBusPath CreatePartitionAndFormat(UInt64 offset, UInt64 size, String type, String name,
            Map<String, Variant<?>> options, String formatType, Map<String, Variant<?>> formatOptions);
}
