package eu.amadeos.system.base.power.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

/**
 * The methods of systemd-logind's manager ({@code /org/freedesktop/login1}) the desktop calls. The boolean of
 * Suspend, Hibernate and PowerOff is "interactive": polkit may ask for a password (when other users are
 * logged in); the desktop's polkit agent shows that question.
 */
@DBusInterfaceName("org.freedesktop.login1.Manager")
public interface LoginManager extends DBusInterface {
    String NAME = "org.freedesktop.login1.Manager";
    String BUS_NAME = "org.freedesktop.login1";
    String PATH = "/org/freedesktop/login1";

    @DBusMemberName("Suspend")
    void suspend(boolean interactive);

    @DBusMemberName("Hibernate")
    void hibernate(boolean interactive);

    @DBusMemberName("PowerOff")
    void powerOff(boolean interactive);

    /** "yes", "no", "na" (not supported here), "challenge" (needs a password). */
    @DBusMemberName("CanSuspend")
    String canSuspend();

    @DBusMemberName("CanHibernate")
    String canHibernate();

    /** Sent with true just before the computer goes to sleep and with false after it woke up. */
    @DBusMemberName("PrepareForSleep")
    class PrepareForSleep extends DBusSignal {
        private final boolean start;

        public PrepareForSleep(String path, boolean start) throws DBusException {
            super(path, start);
            this.start = start;
        }

        public boolean start() {
            return start;
        }
    }
}
