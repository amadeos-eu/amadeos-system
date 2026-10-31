package eu.amadeos.system.base.notify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.amadeos.system.base.notify.dbus.NotificationCenter;
import eu.amadeos.system.base.notify.dbus.Notifications;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * The desktop's side of the notification daemon, over the session bus: sends notifications
 * ({@link #send(DesktopNotification)}), hears which action the user chose, and follows the daemon's state for the
 * Front Panel's bell ({@link #onState(Consumer)}). Connects on first use and again after the bus went away; when
 * there is no session bus or no daemon, the calls do nothing and return false or 0.
 */
public final class NotificationClient implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(NotificationClient.class.getName());

    /** What the bell shows: new notifications, Do not disturb, and whether a daemon answers at all. */
    public record State(int unseen, boolean doNotDisturb, boolean running) {
        public static final State NOT_RUNNING = new State(0, false, false);
    }

    private final Map<Long, Consumer<String>> handlers = new ConcurrentHashMap<>();
    private final List<Consumer<State>> stateListeners = new CopyOnWriteArrayList<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private DBusConnection bus;

    /** Sends the notification; its id, or 0 when no notification service runs. */
    public synchronized long send(DesktopNotification n) {
        Notifications service = notifications();
        if (service == null) return 0;
        List<String> actions = new ArrayList<>();
        n.actions().forEach((key, label) -> {
            actions.add(key);
            actions.add(label);
        });
        Map<String, Variant<?>> hints = new java.util.HashMap<>();
        hints.put("urgency", new Variant<>((byte) n.urgency().ordinal()));
        if (!n.category().isEmpty()) hints.put("category", new Variant<>(n.category()));
        if (!n.desktopEntry().isEmpty()) hints.put("desktop-entry", new Variant<>(n.desktopEntry()));
        try {
            long id = service.notify(n.appName(), new UInt32(n.replaces()), n.icon(), n.summary(), n.body(), actions,
                    hints, -1).longValue();
            if (!n.actions().isEmpty()) handlers.put(id, n.onAction());
            return id;
        } catch (DBusExecutionException e) {
            LOGGER.log(Level.FINE, "No notification service", e);
            return 0;
        }
    }

    /** Closes a notification sent earlier. */
    public synchronized void close(long id) {
        Notifications service = notifications();
        if (service == null || id == 0) return;
        try {
            service.closeNotification(new UInt32(id));
        } catch (DBusExecutionException e) {
            LOGGER.log(Level.FINE, "Cannot close notification " + id, e);
        }
        handlers.remove(id);
    }

    /** Shows the daemon's history window; false when no daemon of amadeOS runs. */
    public synchronized boolean showHistory() {
        return call(NotificationCenter::showHistory);
    }

    public synchronized boolean clearAll() {
        return call(NotificationCenter::clearAll);
    }

    /** @param until {@link NotificationCenter#DND_OFF}, {@link NotificationCenter#DND_ON} or epoch seconds */
    public synchronized boolean setDoNotDisturb(long until) {
        return call(c -> c.setDoNotDisturb(until));
    }

    /** Asks the daemon; {@link State#NOT_RUNNING} when it does not answer. */
    public synchronized State state() {
        NotificationCenter center = center();
        if (center == null) return State.NOT_RUNNING;
        try {
            return new State(center.unseen(), center.doNotDisturb(), true);
        } catch (DBusExecutionException e) {
            return State.NOT_RUNNING;
        }
    }

    /**
     * Calls the listener (on a D-Bus thread) whenever the daemon's state changes, and when the daemon starts or
     * stops; false when there is no session bus.
     */
    public synchronized boolean onState(Consumer<State> listener) {
        stateListeners.add(listener);
        return connection() != null;
    }

    private boolean call(Consumer<NotificationCenter> action) {
        NotificationCenter center = center();
        if (center == null) return false;
        try {
            action.accept(center);
            return true;
        } catch (DBusExecutionException e) {
            LOGGER.log(Level.FINE, "The notification daemon did not answer", e);
            return false;
        }
    }

    private Notifications notifications() {
        DBusConnection c = connection();
        if (c == null) return null;
        try {
            return c.getRemoteObject(Notifications.BUS_NAME, Notifications.PATH, Notifications.class);
        } catch (DBusException e) {
            return null;
        }
    }

    private NotificationCenter center() {
        DBusConnection c = connection();
        if (c == null) return null;
        try {
            return c.getRemoteObject(Notifications.BUS_NAME, NotificationCenter.PATH, NotificationCenter.class);
        } catch (DBusException e) {
            return null;
        }
    }

    private DBusConnection connection() {
        if (bus != null && bus.isConnected()) return bus;
        closeBus();
        try {
            DBusConnection c = DBusConnectionBuilder.forSessionBus().withShared(false).build();
            subscriptions.add(c.addSigHandler(Notifications.ActionInvoked.class, signal -> {
                Consumer<String> handler = handlers.remove(signal.id());
                if (handler != null) handler.accept(signal.actionKey());
            }));
            subscriptions.add(c.addSigHandler(Notifications.NotificationClosed.class,
                    signal -> handlers.remove(signal.id())));
            subscriptions.add(c.addSigHandler(NotificationCenter.StateChanged.class,
                    signal -> tell(new State(signal.unseen(), signal.doNotDisturb(), true))));
            subscriptions.add(c.addSigHandler(DBus.NameOwnerChanged.class, signal -> {
                if (!Notifications.BUS_NAME.equals(signal.name)) return;
                handlers.clear();
                if (signal.newOwner.isEmpty()) tell(State.NOT_RUNNING);
                else new Thread(() -> tell(state()), "notification-state").start();
            }));
            bus = c;
            return c;
        } catch (DBusException | RuntimeException e) {
            LOGGER.log(Level.FINE, "No session bus", e);
            return null;
        }
    }

    private void tell(State state) {
        for (Consumer<State> l : stateListeners) {
            try {
                l.accept(state);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Notification state listener failed", e);
            }
        }
    }

    private void closeBus() {
        for (AutoCloseable s : subscriptions) {
            try {
                s.close();
            } catch (Exception ignored) {
                // the connection is gone anyway
            }
        }
        subscriptions.clear();
        if (bus != null) {
            try {
                bus.close();
            } catch (java.io.IOException ignored) {
                // nothing to do
            }
            bus = null;
        }
    }

    @Override
    public synchronized void close() {
        closeBus();
    }
}
