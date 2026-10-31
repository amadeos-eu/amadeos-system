package eu.amadeos.system.panel.cde.status;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.notify.DesktopNotification;
import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.packages.Change;

/**
 * The notifications the Front Panel itself sends: the battery running low (at {@value #LOW} % a warning, at
 * {@value #CRITICAL} % a critical one that stays until closed and shows through Do not disturb), and software
 * updates that have arrived (once for each new set, with a button that opens them). Taken back when the computer
 * is plugged in or the updates are installed.
 */
public final class DesktopNotices {
    static final int LOW = 10;
    static final int CRITICAL = 5;

    /** Where the notices go: the notification daemon through NotificationClient, a recorder in tests. */
    public interface Sink {
        /** Sends the notification; its id, or 0 when no daemon runs. */
        long send(DesktopNotification n);

        void close(long id);
    }

    enum Level { NONE, LOW, CRITICAL }

    private final Sink sink;
    private final Messages texts;
    private final Runnable showUpdates;
    private Level batteryLevel = Level.NONE;
    private long batteryId;
    private Set<String> announced = Set.of();
    private long updatesId;

    /** @param showUpdates opens the update window from the notice's button; null for no button */
    public DesktopNotices(Sink sink, Runnable showUpdates) {
        this(sink, Messages.load(DesktopNotices.class, NetworkCell.DESKTOP_TEXTS, LocaleResolution.resolve(
                Optional.empty(), Optional.empty(), System.getenv()).language().locale()), showUpdates);
    }

    DesktopNotices(Sink sink, Messages texts, Runnable showUpdates) {
        this.sink = sink;
        this.texts = texts;
        this.showUpdates = showUpdates;
    }

    static Level level(BatteryStatus s) {
        if (!s.onBattery()) return Level.NONE;
        if (s.percent() <= CRITICAL) return Level.CRITICAL;
        return s.percent() <= LOW ? Level.LOW : Level.NONE;
    }

    /** A new battery reading; warns once per level as the charge falls. */
    public synchronized void battery(Optional<BatteryStatus> reading) {
        Level now = reading.map(DesktopNotices::level).orElse(Level.NONE);
        if (now == batteryLevel) return;
        if (now == Level.NONE) {
            if (batteryId != 0) sink.close(batteryId);
            batteryId = 0;
        } else if (now.ordinal() > batteryLevel.ordinal()) {
            BatteryStatus s = reading.get();
            String body = s.minutesLeft() >= 0
                    ? texts.format("notice.battery.body.time", s.percent(), s.minutesLeft())
                    : texts.format("notice.battery.body", s.percent());
            DesktopNotification n = DesktopNotification.of(texts.get("notice.battery.app"), "battery-caution",
                    texts.get(now == Level.CRITICAL ? "notice.battery.critical" : "notice.battery.low"), body)
                    .withCategory("device.error").withReplaces(batteryId)
                    .withUrgency(now == Level.CRITICAL ? DesktopNotification.Urgency.CRITICAL
                            : DesktopNotification.Urgency.NORMAL);
            batteryId = sink.send(n);
        }
        batteryLevel = now;
    }

    /** The pending updates as read; announces the ones that were not announced before. */
    public synchronized void updates(List<Change> pending) {
        Set<String> names = new HashSet<>();
        for (Change c : pending) names.add(c.name() + " " + c.newVersion());
        if (names.isEmpty()) {
            if (updatesId != 0) sink.close(updatesId);
            updatesId = 0;
            announced = Set.of();
            return;
        }
        if (announced.containsAll(names)) return;
        announced = names;
        long security = pending.stream().filter(Change::security).count();
        String summary = pending.size() == 1 ? texts.get("notice.updates.one")
                : texts.format("notice.updates.many", pending.size());
        String body = security == 0 ? texts.get("notice.updates.body")
                : texts.format("notice.updates.body.security", security);
        DesktopNotification n = DesktopNotification.of(texts.get("notice.updates.app"), "software-update-available",
                summary, body).withCategory("x-amadeos.updates").withDesktopEntry("amadeos-software")
                .withReplaces(updatesId)
                .withUrgency(security > 0 ? DesktopNotification.Urgency.NORMAL : DesktopNotification.Urgency.LOW);
        if (showUpdates != null) {
            n = n.withAction("default", texts.get("notice.updates.show"))
                    .withAction("show", texts.get("notice.updates.show"))
                    .onAction(key -> showUpdates.run());
        }
        updatesId = sink.send(n);
    }
}
