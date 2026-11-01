package eu.amadeos.system.disks.udisks.dbus;

import java.util.List;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.Drive.Ata}: SMART of SATA drives. */
@DBusInterfaceName("org.freedesktop.UDisks2.Drive.Ata")
@SuppressWarnings("checkstyle:MethodName")
public interface DriveAta extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.Drive.Ata";

    void SmartUpdate(Map<String, Variant<?>> options);

    List<AtaAttribute> SmartGetAttributes(Map<String, Variant<?>> options);

    void SmartSelftestStart(String type, Map<String, Variant<?>> options);
}
