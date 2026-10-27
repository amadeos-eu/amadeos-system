package eu.amadeos.system.bluetooth.dbus;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** BlueZ's registry of pairing agents, at {@code /org/bluez}. */
@DBusInterfaceName("org.bluez.AgentManager1")
public interface AgentManager1 extends DBusInterface {
    String PATH = "/org/bluez";

    /** @param capability what the agent can do; "KeyboardDisplay" shows numbers and asks for them */
    @DBusMemberName("RegisterAgent")
    void registerAgent(DBusPath agent, String capability);

    @DBusMemberName("UnregisterAgent")
    void unregisterAgent(DBusPath agent);

    @DBusMemberName("RequestDefaultAgent")
    void requestDefaultAgent(DBusPath agent);
}
