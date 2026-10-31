package eu.amadeos.system.panel.cde.status;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.notify.DesktopNotification;
import eu.amadeos.system.packages.Change;

/**
 * The notifications the Front Panel itself sends: software updates that have arrived (once for each new set,
 * with a button that opens them), taken back when the updates are installed. The low battery warning belongs to
 * the power manager of amadeos-session, which follows Settings &gt; Power.
 */
public final class DesktopNotices {
    /** Where the notices go: the notification daemon through NotificationClient, a recorder in tests. */
    public interface Sink {
        /** Sends the notification; its id, or 0 when no daemon runs. */
        long send(DesktopNotification n);

        void close(long id);
    }

    private final Sink sink;
    private final Messages texts;
    private final Runnable showUpdates;
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


    // --8<-- [start:updates]
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
    // --8<-- [end:updates]
}
