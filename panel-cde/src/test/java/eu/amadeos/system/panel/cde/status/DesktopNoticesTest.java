package eu.amadeos.system.panel.cde.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.notify.DesktopNotification;
import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.Origin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopNoticesTest {
    private static final Messages EN = Messages.load(DesktopNotices.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);
    private static final Origin STABLE = new Origin("Debian", "13.1", "stable");
    private static final Origin SECURITY = new Origin("Debian-Security", "13", "stable-security");

    private final List<DesktopNotification> sent = new ArrayList<>();
    private final List<Long> closed = new ArrayList<>();
    private int opened;
    private final DesktopNotices notices = new DesktopNotices(new DesktopNotices.Sink() {
        @Override
        public long send(DesktopNotification n) {
            sent.add(n);
            return sent.size();
        }

        @Override
        public void close(long id) {
            closed.add(id);
        }
    }, EN, () -> opened++);

    private static Optional<BatteryStatus> battery(int percent, BatteryStatus.State state) {
        return Optional.of(new BatteryStatus(percent, state, 30));
    }

    @Test
    void warnsOncePerLevelAndTakesItBackOnMains() {
        notices.battery(battery(50, BatteryStatus.State.DISCHARGING));
        assertTrue(sent.isEmpty());
        notices.battery(battery(10, BatteryStatus.State.DISCHARGING));
        notices.battery(battery(9, BatteryStatus.State.DISCHARGING));
        assertEquals(1, sent.size());
        assertEquals("Battery low", sent.get(0).summary());
        assertEquals("10 % left, about 30 min. Plug in the computer.", sent.get(0).body());
        assertEquals(DesktopNotification.Urgency.NORMAL, sent.get(0).urgency());
        notices.battery(battery(5, BatteryStatus.State.DISCHARGING));
        assertEquals(2, sent.size());
        assertEquals(DesktopNotification.Urgency.CRITICAL, sent.get(1).urgency());
        assertEquals(1, sent.get(1).replaces(), "replaces the low warning");
        notices.battery(battery(5, BatteryStatus.State.CHARGING));
        assertEquals(List.of(2L), closed);
        notices.battery(battery(4, BatteryStatus.State.DISCHARGING));
        assertEquals(3, sent.size(), "unplugged again: warned again");
    }

    @Test
    void noBatteryNoWarning() {
        notices.battery(Optional.empty());
        assertTrue(sent.isEmpty());
    }

    private static Change upgrade(String name, Origin origin) {
        return new Change(Change.Kind.UPGRADE, name, "1.0", "1.1", List.of(origin), "amd64");
    }

    @Test
    void announcesNewUpdatesOnce() {
        List<Change> pending = List.of(upgrade("openssl", SECURITY), upgrade("curl", STABLE));
        notices.updates(pending);
        notices.updates(pending);
        assertEquals(1, sent.size());
        DesktopNotification n = sent.get(0);
        assertEquals("2 updates are ready", n.summary());
        assertEquals("One of them is a security update.", n.body());
        assertEquals("amadeos-software", n.desktopEntry());
        n.onAction().accept("show");
        assertEquals(1, opened);
        notices.updates(List.of(upgrade("curl", STABLE)));
        assertEquals(1, sent.size(), "fewer updates: nothing new to say");
        notices.updates(List.of(upgrade("curl", STABLE), upgrade("bash", STABLE)));
        assertEquals(2, sent.size());
        assertEquals("Install them from the Front Panel or in Software.", sent.get(1).body());
        notices.updates(List.of());
        assertEquals(List.of(2L), closed);
    }
}
