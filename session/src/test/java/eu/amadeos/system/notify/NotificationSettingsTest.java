package eu.amadeos.system.notify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSettingsTest {
    @TempDir
    Path dir;

    @Test
    void savesAndLoads() {
        NotificationSettingsStore store = new NotificationSettingsStore(dir.resolve("notifications.conf"));
        assertEquals(NotificationSettings.DEFAULTS, store.load());
        NotificationSettings s = new NotificationSettings(NotificationSettings.Corner.TOP_LEFT, 10, true, 1_800_000_000L,
                true, LocalTime.of(21, 30), LocalTime.of(6, 0), false,
                Map.of("firefox-esr", NotificationSettings.Mode.HISTORY), Map.of("firefox-esr", "Firefox"));
        store.save(s);
        assertEquals(s, store.load());
        store.save(s.withDndUntil(NotificationSettings.DND_ON));
        assertEquals(NotificationSettings.DND_ON, store.load().dndUntil());
    }

    @Test
    void readsWhatPeopleWriteByHand() throws Exception {
        Path file = dir.resolve("notifications.conf");
        Files.writeString(file, "popup.corner=nowhere\npopup.seconds=999\ndnd.until=soon\ndnd.from=25:00\n"
                + "program.chatty=OFF\nprogram.odd=sometimes\n");
        NotificationSettings s = new NotificationSettingsStore(file).load();
        assertEquals(NotificationSettings.Corner.BOTTOM_RIGHT, s.corner());
        assertEquals(NotificationSettings.MAX_SECONDS, s.seconds());
        assertEquals(NotificationSettings.DND_OFF, s.dndUntil());
        assertEquals(LocalTime.of(22, 0), s.from());
        assertEquals(NotificationSettings.Mode.OFF, s.mode("chatty"));
        assertEquals(NotificationSettings.Mode.POPUP, s.mode("odd"));
    }

    @Test
    void scheduleOverMidnight() {
        NotificationSettings s = new NotificationSettings(null, 6, false, 0, true, LocalTime.of(22, 0),
                LocalTime.of(7, 0), false, null, null);
        assertTrue(s.scheduled(LocalTime.of(23, 0)));
        assertTrue(s.scheduled(LocalTime.of(6, 59)));
        assertFalse(s.scheduled(LocalTime.of(7, 0)));
        assertFalse(s.scheduled(LocalTime.of(12, 0)));
        NotificationSettings day = new NotificationSettings(null, 6, false, 0, true, LocalTime.of(12, 0),
                LocalTime.of(13, 0), false, null, null);
        assertTrue(day.scheduled(LocalTime.of(12, 30)));
        assertFalse(day.scheduled(LocalTime.of(13, 30)));
    }

    @Test
    void manualDoNotDisturbEnds() {
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        NotificationSettings s = NotificationSettings.DEFAULTS.withDndUntil(now.getEpochSecond() + 60);
        assertTrue(s.manualDnd(now));
        assertFalse(s.manualDnd(now.plusSeconds(61)));
        assertTrue(s.doNotDisturb(now, ZoneOffset.UTC, false));
        assertTrue(NotificationSettings.DEFAULTS.doNotDisturb(now, ZoneOffset.UTC, true), "full screen");
    }

    @Test
    void tomorrowMorning() {
        Instant evening = Instant.parse("2026-10-31T20:00:00Z");
        Instant early = Instant.parse("2026-10-31T05:00:00Z");
        assertEquals(Instant.parse("2026-11-01T07:00:00Z").getEpochSecond(),
                NotificationSettings.DEFAULTS.tomorrowMorning(evening, ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-10-31T07:00:00Z").getEpochSecond(),
                NotificationSettings.DEFAULTS.tomorrowMorning(early, ZoneOffset.UTC), "before 7 it is this morning");
    }

    @Test
    void programKeys() {
        assertEquals("firefox-esr", NotificationSettings.programKey("firefox-esr.desktop", "Firefox"));
        assertEquals("notify-send", NotificationSettings.programKey("", "notify-send"));
        assertEquals("backup & sync", NotificationSettings.programKey(null, " Backup & Sync "));
    }
}
