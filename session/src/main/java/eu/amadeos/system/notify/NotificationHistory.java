package eu.amadeos.system.notify;

import eu.amadeos.commons.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The notifications the bell and the history window show, newest first: at most {@link #MAX_ENTRIES}, none older
 * than {@link #MAX_AGE}. Kept in {@code ~/.cache/amadeos/notifications.json}, so they survive a logout; the
 * images the senders passed as data are not kept. Not thread-safe: the daemon's core guards it.
 */
final class NotificationHistory {
    private static final Logger LOGGER = Logger.getLogger(NotificationHistory.class.getName());
    static final int MAX_ENTRIES = 100;
    static final Duration MAX_AGE = Duration.ofDays(7);

    /** One notification in the history: whether the user has seen it, and whether its sender can still act on it. */
    record Entry(Notification notification, boolean seen, boolean open) {
        Entry seen(boolean s) {
            return new Entry(notification, s, open);
        }

        Entry closed() {
            return new Entry(notification, seen, false);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Adds the notification, or replaces the one with its id; prunes old ones. */
    void add(Notification n, Instant now) {
        entries.removeIf(e -> e.notification().id() == n.id());
        entries.add(0, new Entry(n, false, true));
        prune(now);
    }

    Entry find(long id) {
        return entries.stream().filter(e -> e.notification().id() == id).findFirst().orElse(null);
    }

    boolean contains(long id) {
        return find(id) != null;
    }

    void update(long id, java.util.function.UnaryOperator<Entry> change) {
        entries.replaceAll(e -> e.notification().id() == id ? change.apply(e) : e);
    }

    /** Marks every notification of the sender as no longer open, e.g. when it quit. */
    void senderGone(String sender) {
        entries.replaceAll(e -> e.open() && e.notification().sender().equals(sender) ? e.closed() : e);
    }

    boolean remove(long id) {
        return entries.removeIf(e -> e.notification().id() == id);
    }

    void clear() {
        entries.clear();
    }

    void markAllSeen() {
        entries.replaceAll(e -> e.seen(true));
    }

    int unseen() {
        return (int) entries.stream().filter(e -> !e.seen()).count();
    }

    void prune(Instant now) {
        Instant oldest = now.minus(MAX_AGE);
        entries.removeIf(e -> e.notification().time().isBefore(oldest));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    }

    /** The highest id in the history, so that new ids of this session do not repeat old ones. */
    long highestId() {
        return entries.stream().mapToLong(e -> e.notification().id()).max().orElse(0);
    }

    // ---- file ----------------------------------------------------------------------------------

    void load(Path file, Instant now) {
        entries.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            for (Object o : Json.parseArray(Files.readString(file, StandardCharsets.UTF_8))) {
                if (!(o instanceof Map<?, ?> m)) continue;
                List<Notification.Action> actions = new ArrayList<>();
                List<Object> list = Json.list(m, "actions");
                if (list != null) {
                    for (Object a : list) actions.add(new Notification.Action(Json.str(a, "key"), Json.str(a, "label")));
                }
                Notification n = new Notification(number(m.get("id")), Json.str(m, "app"), Json.str(m, "program"),
                        Json.str(m, "desktopEntry"), Json.str(m, "icon"), null, Json.str(m, "summary"),
                        Json.str(m, "body"), actions, Notification.Urgency.of((int) number(m.get("urgency"))),
                        Json.str(m, "category"), -1, false, false, false,
                        Instant.ofEpochMilli(number(m.get("time"))), "");
                // Notifications of an earlier session have no sender any more.
                entries.add(new Entry(n, Boolean.TRUE.equals(m.get("seen")), false));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Cannot read the notification history " + file, e);
            entries.clear();
        }
        prune(now);
    }

    void save(Path file) {
        List<Object> out = new ArrayList<>();
        for (Entry e : entries) {
            Notification n = e.notification();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id());
            m.put("app", n.appName());
            m.put("program", n.programKey());
            m.put("desktopEntry", n.desktopEntry());
            m.put("icon", n.icon());
            m.put("summary", n.summary());
            m.put("body", n.body());
            m.put("urgency", n.urgency().ordinal());
            m.put("category", n.category());
            m.put("time", n.time().toEpochMilli());
            m.put("seen", e.seen());
            List<Object> actions = new ArrayList<>();
            for (Notification.Action a : n.actions()) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("key", a.key());
                am.put("label", a.label());
                actions.add(am);
            }
            m.put("actions", actions);
            out.add(m);
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, Json.write(out), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot write the notification history " + file, e);
        }
    }

    private static long number(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
