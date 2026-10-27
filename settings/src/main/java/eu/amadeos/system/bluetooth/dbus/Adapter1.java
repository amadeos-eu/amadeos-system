package eu.amadeos.system.bluetooth.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** The methods of a BlueZ adapter ({@code /org/bluez/hci0}) the Settings call; properties go through Properties. */
@DBusInterfaceName("org.bluez.Adapter1")
public interface Adapter1 extends DBusInterface {
    String NAME = "org.bluez.Adapter1";

    @DBusMemberName("StartDiscovery")
    void startDiscovery();

    @DBusMemberName("StopDiscovery")
    void stopDiscovery();

    @DBusMemberName("RemoveDevice")
    void removeDevice(DBusPath device);
}
