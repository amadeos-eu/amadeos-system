package eu.amadeos.system.polkit.dbus;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * The interface polkitd calls on a registered agent. {@code beginAuthentication} returns once the user has
 * authenticated through polkit's helper (polkitd then checks the cookie itself) and throws
 * {@link org.freedesktop.PolicyKit1.Error.Cancelled} when the user gave up.
 */
@DBusInterfaceName("org.freedesktop.PolicyKit1.AuthenticationAgent")
public interface AuthenticationAgent extends DBusInterface {
    @DBusMemberName("BeginAuthentication")
    void beginAuthentication(String actionId, String message, String iconName, Map<String, String> details,
                             String cookie, List<Subject> identities);

    @DBusMemberName("CancelAuthentication")
    void cancelAuthentication(String cookie);
}
