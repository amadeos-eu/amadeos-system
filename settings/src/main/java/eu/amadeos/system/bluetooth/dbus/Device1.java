package eu.amadeos.system.bluetooth.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * A device BlueZ knows ({@code /org/bluez/hci0/dev_XX_XX_...}). The methods carry their D-Bus names because
 * Pair and Connect are called with {@code callMethodAsync}, which finds them by name.
 */
@DBusInterfaceName("org.bluez.Device1")
@SuppressWarnings("checkstyle:MethodName")
public interface Device1 extends DBusInterface {
    String NAME = "org.bluez.Device1";

    void Pair();

    void CancelPairing();

    void Connect();

    void Disconnect();
}
