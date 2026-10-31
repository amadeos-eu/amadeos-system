package eu.amadeos.system.notify;

import java.awt.image.BufferedImage;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.amadeos.system.base.notify.dbus.NotificationCenter;
import eu.amadeos.system.base.notify.dbus.Notifications;
import eu.amadeos.system.base.notify.dbus.ServerInformation;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import org.freedesktop.dbus.DBusCallInfo;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * The two objects the daemon exports on the session bus: {@code /org/freedesktop/Notifications} (the
 * specification) and {@code /eu/amadeos/Notifications} (the bell). Turns the D-Bus arguments into a
 * {@link Notification} and hands everything to the {@link NotificationCore}. Runs on dbus-java's worker threads.
 */
final class NotificationServer {
    private static final Logger LOGGER = Logger.getLogger(NotificationServer.class.getName());
    static final List<String> CAPABILITIES = List.of("body", "body-markup", "body-hyperlinks", "actions",
            "icon-static", "persistence");
    static final String SPEC_VERSION = "1.2";
    /** Passed on to the core, which works out the morning from the schedule. */
    static final long TOMORROW = NotificationCenter.DND_TOMORROW;

    /** Finds a desktop entry by its id without {@code .desktop}. */
    interface DesktopEntries {
        Optional<XdgDesktopEntry> find(String id);
    }

    private final NotificationCore core;
    private final DesktopEntries entries;
    private final Clock clock;
    private final Runnable showHistory;
    private final String version;

    NotificationServer(NotificationCore core, DesktopEntries entries, Clock clock, Runnable showHistory, String version) {
        this.core = core;
        this.entries = entries;
        this.clock = clock;
        this.showHistory = showHistory;
        this.version = version;
    }

    Notifications specObject() {
        return new Spec();
    }

    NotificationCenter centerObject() {
        return new Center();
    }

    /** {@code Notify} without the bus: the arguments as the specification has them. */
    long notify(String appName, long replacesId, String appIcon, String summary, String body, List<String> actions,
                Map<String, Variant<?>> hints, int expireTimeout, String sender) {
        Map<String, Variant<?>> h = hints == null ? Map.of() : hints;
        String desktopEntry = string(h, "desktop-entry");
        if (desktopEntry.endsWith(".desktop")) desktopEntry = desktopEntry.substring(0, desktopEntry.length() - 8);
        Optional<XdgDesktopEntry> entry = desktopEntry.isEmpty() ? Optional.empty() : entries.find(desktopEntry);
        String name = appName != null && !appName.isBlank() ? appName.strip()
                : entry.map(XdgDesktopEntry::name).orElse(desktopEntry);
        String icon = firstNonBlank(string(h, "image-path"), string(h, "image_path"), appIcon,
                entry.map(XdgDesktopEntry::icon).orElse(""));
        if (icon.startsWith("file://")) icon = icon.substring(7);
        // A program that calls through short-lived helpers (gdbus call, as amade-commons does) names its own
        // process; its notifications stay open as long as that process runs, not the helper's bus connection.
        long senderPid = number(h.get("sender-pid"), 0);
        if (senderPid > 0) sender = Notification.PID_SENDER + senderPid;
        BufferedImage image = NotificationImages.fromImageData(first(h, "image-data", "image_data", "icon_data"));
        Notification n = new Notification(0, name, NotificationSettings.programKey(desktopEntry, name), desktopEntry,
                icon, image, summary, body, Notification.actions(actions),
                Notification.Urgency.of(number(h.get("urgency"), 1)), string(h, "category"), expireTimeout,
                bool(h.get("transient")), bool(h.get("resident")), bool(h.get("suppress-sound")), clock.instant(),
                sender);
        return core.notify(n, replacesId);
    }

    private static String sender() {
        DBusCallInfo info = AbstractConnection.getCallInfo();
        return info == null || info.getSource() == null ? "" : info.getSource();
    }

    private final class Spec implements Notifications {
        @Override
        public List<String> getCapabilities() {
            return CAPABILITIES;
        }

        @Override
        public UInt32 notify(String appName, UInt32 replacesId, String appIcon, String summary, String body,
                             List<String> actions, Map<String, Variant<?>> hints, int expireTimeout) {
            try {
                return new UInt32(NotificationServer.this.notify(appName, replacesId.longValue(), appIcon, summary,
                        body, actions, hints, expireTimeout, sender()));
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Notification from " + appName + " not shown", e);
                throw e;
            }
        }

        @Override
        public void closeNotification(UInt32 id) {
            core.closeByCall(id.longValue());
        }

        @Override
        public ServerInformation<String, String, String, String> getServerInformation() {
            return new ServerInformation<>("amadeOS Notifications", "amadeOS", version, SPEC_VERSION);
        }

        @Override
        public String getObjectPath() {
            return PATH;
        }
    }

    private final class Center implements NotificationCenter {
        @Override
        public void showHistory() {
            showHistory.run();
        }

        @Override
        public void clearAll() {
            core.clearAll();
        }

        @Override
        public void setDoNotDisturb(long until) {
            core.setDoNotDisturb(until == DND_TOMORROW ? TOMORROW : until < 0 ? NotificationSettings.DND_ON : until);
        }

        @Override
        public int unseen() {
            return core.unseen();
        }

        @Override
        public boolean doNotDisturb() {
            return core.doNotDisturb();
        }

        @Override
        public String getObjectPath() {
            return PATH;
        }
    }

    // ---- hints ---------------------------------------------------------------------------------

    private static Object first(Map<String, Variant<?>> h, String... keys) {
        for (String k : keys) {
            Variant<?> v = h.get(k);
            if (v != null) return v.getValue();
        }
        return null;
    }

    private static String string(Map<String, Variant<?>> h, String key) {
        Variant<?> v = h.get(key);
        return v != null && v.getValue() instanceof String s ? s.strip() : "";
    }

    static int number(Variant<?> v, int fallback) {
        if (v == null) return fallback;
        Object o = v.getValue();
        if (o instanceof Number n) return n.intValue() & (o instanceof Byte ? 0xFF : -1);
        return fallback;
    }

    private static boolean bool(Variant<?> v) {
        if (v == null) return false;
        Object o = v.getValue();
        if (o instanceof Boolean b) return b;
        return o instanceof Number n && n.intValue() != 0;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.strip();
        return "";
    }
}
