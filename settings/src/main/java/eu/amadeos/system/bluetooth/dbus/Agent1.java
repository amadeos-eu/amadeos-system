package eu.amadeos.system.bluetooth.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;

/**
 * The interface bluetoothd calls on a registered pairing agent. A refusal is
 * {@link org.bluez.Error.Rejected}, a user who gave up {@link org.bluez.Error.Canceled}.
 */
@DBusInterfaceName("org.bluez.Agent1")
public interface Agent1 extends DBusInterface {
    @DBusMemberName("Release")
    void release();

    @DBusMemberName("RequestPinCode")
    String requestPinCode(DBusPath device);

    @DBusMemberName("DisplayPinCode")
    void displayPinCode(DBusPath device, String pinCode);

    @DBusMemberName("RequestPasskey")
    UInt32 requestPasskey(DBusPath device);

    @DBusMemberName("DisplayPasskey")
    void displayPasskey(DBusPath device, UInt32 passkey, UInt16 entered);

    @DBusMemberName("RequestConfirmation")
    void requestConfirmation(DBusPath device, UInt32 passkey);

    @DBusMemberName("RequestAuthorization")
    void requestAuthorization(DBusPath device);

    @DBusMemberName("AuthorizeService")
    void authorizeService(DBusPath device, String uuid);

    @DBusMemberName("Cancel")
    void cancel();
}
