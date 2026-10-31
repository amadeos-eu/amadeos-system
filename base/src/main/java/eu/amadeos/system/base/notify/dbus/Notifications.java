package eu.amadeos.system.base.notify.dbus;

import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * The freedesktop.org Desktop Notifications service, version 1.2
 * (https://specifications.freedesktop.org/notification-spec/latest/): the bus name
 * {@code org.freedesktop.Notifications}, the object {@code /org/freedesktop/Notifications}. The notification
 * daemon of amadeos-session implements it; libnotify ({@code notify-send}), Firefox, Thunderbird and the Front
 * Panel call it.
 */
@DBusInterfaceName("org.freedesktop.Notifications")
public interface Notifications extends DBusInterface {
    String BUS_NAME = "org.freedesktop.Notifications";
    String PATH = "/org/freedesktop/Notifications";

    /** Why a notification was closed, the second argument of {@link NotificationClosed}. */
    int EXPIRED = 1;
    int DISMISSED = 2;
    int CLOSED_BY_CALL = 3;
    int UNDEFINED = 4;

    @DBusMemberName("GetCapabilities")
    List<String> getCapabilities();

    /**
     * Shows a notification.
     *
     * @param replacesId    0 for a new notification, else the id of the one this one replaces
     * @param actions       pairs of action key and label; the key {@code default} is the click on the body
     * @param hints         urgency (byte 0 to 2), category, desktop-entry, image-data, image-path, transient,
     *                      resident, suppress-sound, ...
     * @param expireTimeout milliseconds; -1 the server's default, 0 never
     * @return the notification's id, never 0
     */
    @DBusMemberName("Notify")
    UInt32 notify(String appName, UInt32 replacesId, String appIcon, String summary, String body,
                  List<String> actions, Map<String, Variant<?>> hints, int expireTimeout);

    @DBusMemberName("CloseNotification")
    void closeNotification(UInt32 id);

    @DBusMemberName("GetServerInformation")
    ServerInformation<String, String, String, String> getServerInformation();

    /** A notification went away: expired, dismissed by the user or closed by {@code CloseNotification}. */
    class NotificationClosed extends DBusSignal {
        private final UInt32 id;
        private final UInt32 reason;

        public NotificationClosed(String path, UInt32 id, UInt32 reason) throws DBusException {
            super(path, id, reason);
            this.id = id;
            this.reason = reason;
        }

        public long id() {
            return id.longValue();
        }

        public int reason() {
            return reason.intValue();
        }
    }

    /** The user clicked one of the notification's actions ({@code default}: the notification itself). */
    class ActionInvoked extends DBusSignal {
        private final UInt32 id;
        private final String actionKey;

        public ActionInvoked(String path, UInt32 id, String actionKey) throws DBusException {
            super(path, id, actionKey);
            this.id = id;
            this.actionKey = actionKey;
        }

        public long id() {
            return id.longValue();
        }

        public String actionKey() {
            return actionKey;
        }
    }

    /** Sent right before {@link ActionInvoked}: the token with which the program may raise its window. */
    class ActivationToken extends DBusSignal {
        private final UInt32 id;
        private final String token;

        public ActivationToken(String path, UInt32 id, String token) throws DBusException {
            super(path, id, token);
            this.id = id;
            this.token = token;
        }

        public long id() {
            return id.longValue();
        }

        public String token() {
            return token;
        }
    }
}
