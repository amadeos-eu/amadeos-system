package eu.amadeos.system.disks.udisks.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/** {@code org.freedesktop.UDisks2.NVMe.Controller}: health of NVMe drives (udisks 2.10). */
@DBusInterfaceName("org.freedesktop.UDisks2.NVMe.Controller")
@SuppressWarnings("checkstyle:MethodName")
public interface NvmeController extends DBusInterface {
    String NAME = "org.freedesktop.UDisks2.NVMe.Controller";

    void SmartUpdate(Map<String, Variant<?>> options);

    Map<String, Variant<?>> SmartGetAttributes(Map<String, Variant<?>> options);

    void SmartSelftestStart(String type, Map<String, Variant<?>> options);
}
