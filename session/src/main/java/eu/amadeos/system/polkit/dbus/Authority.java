package eu.amadeos.system.polkit.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** The part of polkitd's authority ({@code /org/freedesktop/PolicyKit1/Authority}) an agent uses. */
@DBusInterfaceName("org.freedesktop.PolicyKit1.Authority")
public interface Authority extends DBusInterface {
    String BUS_NAME = "org.freedesktop.PolicyKit1";
    String PATH = "/org/freedesktop/PolicyKit1/Authority";

    @DBusMemberName("RegisterAuthenticationAgent")
    void registerAuthenticationAgent(Subject subject, String locale, String objectPath);

    @DBusMemberName("UnregisterAuthenticationAgent")
    void unregisterAuthenticationAgent(Subject subject, String objectPath);
}
