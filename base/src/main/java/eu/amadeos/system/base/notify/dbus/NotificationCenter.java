package eu.amadeos.system.base.notify.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

/**
 * amadeOS's own interface of the notification daemon, next to {@link Notifications} on the same bus name, at
 * {@code /eu/amadeos/Notifications}: what the Front Panel's bell needs (how many notifications are new, Do not
 * disturb) and what its menu does.
 */
@DBusInterfaceName("eu.amadeos.NotificationCenter1")
public interface NotificationCenter extends DBusInterface {
    String PATH = "/eu/amadeos/Notifications";

    /** {@link #setDoNotDisturb(long)}: off. */
    long DND_OFF = 0;
    /** {@link #setDoNotDisturb(long)}: until the user turns it off. */
    long DND_ON = -1;
    /** {@link #setDoNotDisturb(long)}: until tomorrow morning (the end of the schedule, else 07:00). */
    long DND_TOMORROW = -2;

    /** Shows the history window; the notifications in it count as seen. */
    @DBusMemberName("ShowHistory")
    void showHistory();

    /** Empties the history. */
    @DBusMemberName("ClearAll")
    void clearAll();

    /** {@link #DND_OFF}, {@link #DND_ON}, {@link #DND_TOMORROW} or the end as seconds since the epoch. */
    @DBusMemberName("SetDoNotDisturb")
    void setDoNotDisturb(long until);

    /** The notifications in the history the user has not seen yet. */
    @DBusMemberName("Unseen")
    int unseen();

    /** Whether Do not disturb holds back the pop-ups just now (by hand, by the schedule or a full screen window). */
    @DBusMemberName("DoNotDisturb")
    boolean doNotDisturb();

    /** Sent whenever the number of new notifications or Do not disturb changes. */
    class StateChanged extends DBusSignal {
        private final int unseen;
        private final boolean doNotDisturb;

        public StateChanged(String path, int unseen, boolean doNotDisturb) throws DBusException {
            super(path, unseen, doNotDisturb);
            this.unseen = unseen;
            this.doNotDisturb = doNotDisturb;
        }

        public int unseen() {
            return unseen;
        }

        public boolean doNotDisturb() {
            return doNotDisturb;
        }
    }
}
