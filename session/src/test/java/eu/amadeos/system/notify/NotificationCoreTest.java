package eu.amadeos.system.notify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationCoreTest {
    private final List<String> screen = new ArrayList<>();
    private final List<String> signals = new ArrayList<>();
    private NotificationSettings settings = NotificationSettings.DEFAULTS;
    private boolean locked;
    private boolean fullscreen;
    private NotificationHistory history;
    private NotificationCore core;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        history = new NotificationHistory();
        core = new NotificationCore(() -> settings, s -> settings = s, new NotificationCore.Screen() {
            @Override
            public void popup(Notification n, long timeoutMs) {
                screen.add("popup " + n.id() + " " + timeoutMs);
            }

            @Override
            public void unpop(long id) {
                screen.add("unpop " + id);
            }

            @Override
            public void historyChanged() {
            }

            @Override
            public boolean fullscreen() {
                return fullscreen;
            }

            @Override
            public boolean locked() {
                return locked;
            }

            @Override
            public void beep() {
                screen.add("beep");
            }

            @Override
            public void launch(String desktopEntry) {
                screen.add("launch " + desktopEntry);
            }
        }, new NotificationCore.Signals() {
            @Override
            public void closed(long id, int reason) {
                signals.add("closed " + id + " " + reason);
            }

            @Override
            public void actionInvoked(long id, String key) {
                signals.add("action " + id + " " + key);
            }

            @Override
            public void stateChanged(int unseen, boolean doNotDisturb) {
                signals.add("state " + unseen + " " + doNotDisturb);
            }
        }, clock, history, () -> { });
    }

    private Notification n(String app, Notification.Urgency urgency, List<Notification.Action> actions, int timeout) {
        return new Notification(0, app, NotificationSettings.programKey("", app), "", "", null, "Summary", "Body",
                actions, urgency, "", timeout, false, false, false, clock.instant(), ":1.42");
    }

    private Notification n(String app) {
        return n(app, Notification.Urgency.NORMAL, List.of(), -1);
    }

    @Test
    void popsUpWithTheUsersTimeAndCountsAsNew() {
        long id = core.notify(n("Mail"), 0);
        assertEquals(1, id);
        assertEquals(List.of("popup 1 6000"), screen);
        assertEquals(List.of("state 1 false"), signals);
        assertEquals(1, core.unseen());
        assertEquals("Mail", settings.names().get("mail"), "the Settings learn the program's name");
    }

    @Test
    void idsCountUpAndReplacingKeepsTheId() {
        long first = core.notify(n("Mail"), 0);
        long second = core.notify(n("Mail"), 0);
        assertEquals(2, second);
        assertEquals(first, core.notify(n("Mail"), first));
        assertEquals(3, core.notify(n("Mail"), 99), "an unknown id to replace gives a new one");
        assertEquals(3, core.entries().size());
    }

    @Test
    void criticalStaysAndExpireTimeoutIsHonoured() {
        assertEquals(0, NotificationCore.timeout(n("Battery", Notification.Urgency.CRITICAL, List.of(), 3000), settings));
        assertEquals(0, NotificationCore.timeout(n("A", Notification.Urgency.NORMAL, List.of(), 0), settings));
        assertEquals(3000, NotificationCore.timeout(n("A", Notification.Urgency.NORMAL, List.of(), 3000), settings));
        assertEquals(6000, NotificationCore.timeout(n("A", Notification.Urgency.NORMAL, List.of(), -1), settings));
    }

    @Test
    void doNotDisturbHoldsBackAllButCritical() {
        settings = settings.withDndUntil(NotificationSettings.DND_ON);
        core.notify(n("Mail"), 0);
        assertTrue(screen.isEmpty());
        assertEquals(1, core.unseen());
        core.notify(n("Battery", Notification.Urgency.CRITICAL, List.of(), -1), 0);
        assertEquals(List.of("popup 2 0"), screen);
        assertTrue(signals.contains("state 2 true"));
    }

    @Test
    void fullScreenWindowsHoldBackPopups() {
        fullscreen = true;
        core.notify(n("Mail"), 0);
        assertTrue(screen.isEmpty());
        settings = new NotificationSettings(settings.corner(), 6, false, 0, false, null, null, false, Map.of(), Map.of());
        core.notify(n("Mail"), 0);
        assertEquals(List.of("popup 2 6000"), screen, "unless switched off in the settings");
    }

    @Test
    void programModes() {
        settings = settings.withProgram("chatty", NotificationSettings.Mode.OFF, "Chatty")
                .withProgram("quiet", NotificationSettings.Mode.HISTORY, "Quiet");
        long off = core.notify(n("Chatty"), 0);
        assertEquals(List.of("closed " + off + " 4"), signals);
        assertEquals(0, core.entries().size());
        core.notify(n("Quiet"), 0);
        assertTrue(screen.isEmpty());
        assertEquals(1, core.unseen());
    }

    @Test
    void nothingPopsUpOverTheLockAndCriticalOnesComeAfterTheUnlock() {
        locked = true;
        core.notify(n("Mail"), 0);
        long critical = core.notify(n("Battery", Notification.Urgency.CRITICAL, List.of(), -1), 0);
        assertTrue(screen.isEmpty());
        locked = false;
        core.tick();
        assertEquals(List.of("popup " + critical + " 0"), screen);
    }

    @Test
    void anExpiredPopupStaysOpenInTheHistory() {
        long id = core.notify(n("Calendar", Notification.Urgency.NORMAL,
                List.of(new Notification.Action("snooze", "Snooze")), -1), 0);
        core.expired(id);
        assertEquals("unpop " + id, screen.get(1));
        assertTrue(core.entries().get(0).open());
        assertFalse(signals.stream().anyMatch(s -> s.startsWith("closed")));
        core.invoke(id, "snooze");
        assertTrue(signals.contains("action " + id + " snooze"));
        assertTrue(signals.contains("closed " + id + " 2"));
        assertTrue(core.entries().get(0).seen());
        assertFalse(core.entries().get(0).open());
    }

    @Test
    void transientNotificationsAreNotKept() {
        Notification t = new Notification(0, "Volume", "volume", "", "", null, "50 %", "", List.of(),
                Notification.Urgency.LOW, "", -1, true, false, false, clock.instant(), "");
        long id = core.notify(t, 0);
        assertTrue(core.entries().isEmpty());
        core.expired(id);
        assertTrue(signals.contains("closed " + id + " 1"));
    }

    @Test
    void theSenderTakesItBack() {
        long id = core.notify(n("Mail"), 0);
        core.closeByCall(id);
        assertTrue(core.entries().isEmpty());
        assertTrue(signals.contains("closed " + id + " 3"));
        assertEquals(0, core.unseen());
    }

    @Test
    void dismissingMarksSeenAndKeepsTheEntry() {
        long id = core.notify(n("Mail"), 0);
        core.dismiss(id);
        assertEquals(1, core.entries().size());
        assertEquals(0, core.unseen());
        assertTrue(signals.contains("closed " + id + " 2"));
    }

    @Test
    void aClickAfterTheSenderQuitStartsItsProgram() {
        Notification withEntry = new Notification(0, "Calendar", "amade-calendar", "amade-calendar", "", null, "S",
                "", List.of(new Notification.Action("default", "Open")), Notification.Urgency.NORMAL, "", -1, false,
                false, false, clock.instant(), ":1.7");
        long id = core.notify(withEntry, 0);
        core.senderGone(":1.7");
        core.open(id);
        assertTrue(screen.contains("launch amade-calendar"));
        assertFalse(signals.stream().anyMatch(s -> s.startsWith("action")));
    }

    @Test
    void aSenderNamedByItsProcessStaysOpenWhileTheProcessRuns() {
        Notification alive = new Notification(0, "Calendar", "amade-calendar", "amade-calendar", "", null, "S", "",
                List.of(new Notification.Action("snooze", "Snooze")), Notification.Urgency.NORMAL, "", -1, false,
                false, false, clock.instant(), Notification.PID_SENDER + ProcessHandle.current().pid());
        long id = core.notify(alive, 0);
        core.senderGone(":1.7");
        core.invoke(id, "snooze");
        assertTrue(signals.contains("action " + id + " snooze"), "this test's own process still runs");

        Notification gone = new Notification(0, "Calendar", "amade-calendar", "amade-calendar", "", null, "S", "",
                List.of(new Notification.Action("snooze", "Snooze")), Notification.Urgency.NORMAL, "", -1, false,
                false, false, clock.instant(), Notification.PID_SENDER + Long.MAX_VALUE);
        long id2 = core.notify(gone, 0);
        core.invoke(id2, "snooze");
        assertFalse(signals.contains("action " + id2 + " snooze"), "no process with that id");
    }

    @Test
    void showingTheHistoryAndClearing() {
        core.notify(n("Mail"), 0);
        core.notify(n("Mail"), 0);
        core.historyShown();
        assertEquals(0, core.unseen());
        core.clearAll();
        assertTrue(core.entries().isEmpty());
        assertTrue(signals.contains("closed 1 2") && signals.contains("closed 2 2"));
    }

    @Test
    void doNotDisturbUntilTomorrowMorning() {
        core.setDoNotDisturb(NotificationServer.TOMORROW);
        assertEquals(Instant.parse("2026-11-01T07:00:00Z").getEpochSecond(), settings.dndUntil());
        assertTrue(core.doNotDisturb());
        core.setDoNotDisturb(NotificationSettings.DND_OFF);
        assertFalse(core.doNotDisturb());
    }

    @Test
    void theBeepWhenWanted() {
        settings = new NotificationSettings(settings.corner(), 6, true, 0, false, null, null, true, Map.of(), Map.of());
        core.notify(n("Mail"), 0);
        assertTrue(screen.contains("beep"));
    }
}
