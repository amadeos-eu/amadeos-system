package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.Block}: a block device (whole disk, partition, unlocked LUKS volume). */
@DBusInterfaceName("org.freedesktop.UDisks2.Block")
@SuppressWarnings("checkstyle:MethodName")
public interface Block extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Block";

    void Format(String type, Map<String, Variant<?>> options);

    FileDescriptor OpenForBackup(Map<String, Variant<?>> options);

    FileDescriptor OpenForRestore(Map<String, Variant<?>> options);

    void AddConfigurationItem(ConfigurationItem item, Map<String, Variant<?>> options);

    void Rescan(Map<String, Variant<?>> options);
}
