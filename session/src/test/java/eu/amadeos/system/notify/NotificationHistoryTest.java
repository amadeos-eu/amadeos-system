package eu.amadeos.system.notify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationHistoryTest {
    @TempDir
    Path dir;

    private static Notification n(long id, Instant time) {
        return new Notification(id, "Mail", "amade-mail", "amade-mail", "mail", null, "Summary " + id,
                "<b>Body</b>", List.of(new Notification.Action("default", "Open")), Notification.Urgency.CRITICAL,
                "email.arrived", -1, false, false, false, time, ":1.5");
    }

    @Test
    void survivesALogout() {
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        NotificationHistory h = new NotificationHistory();
        h.add(n(1, now.minusSeconds(60)), now);
        h.add(n(2, now), now);
        h.update(1, e -> e.seen(true));
        Path file = dir.resolve("cache/notifications.json");
        h.save(file);

        NotificationHistory loaded = new NotificationHistory();
        loaded.load(file, now);
        assertEquals(2, loaded.entries().size());
        NotificationHistory.Entry newest = loaded.entries().get(0);
        assertEquals(2, newest.notification().id());
        assertEquals("<b>Body</b>", newest.notification().body());
        assertEquals(Notification.Urgency.CRITICAL, newest.notification().urgency());
        assertEquals(now, newest.notification().time());
        assertEquals("Open", newest.notification().actions().get(0).label());
        assertFalse(newest.seen());
        assertFalse(newest.open(), "the senders of an earlier session are gone");
        assertTrue(loaded.entries().get(1).seen());
        assertEquals(2, loaded.highestId());
    }

    @Test
    void keepsAWeekAndAHundred() {
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        NotificationHistory h = new NotificationHistory();
        h.add(n(1, now.minus(Duration.ofDays(8))), now);
        assertTrue(h.entries().isEmpty());
        for (int i = 1; i <= 120; i++) h.add(n(i, now), now);
        assertEquals(NotificationHistory.MAX_ENTRIES, h.entries().size());
        assertEquals(120, h.entries().get(0).notification().id());
    }

    @Test
    void aBrokenFileIsAnEmptyHistory() throws Exception {
        Path file = dir.resolve("notifications.json");
        java.nio.file.Files.writeString(file, "{not json");
        NotificationHistory h = new NotificationHistory();
        h.load(file, Instant.now());
        assertTrue(h.entries().isEmpty());
    }
}
