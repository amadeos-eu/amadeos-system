package eu.amadeos.system.panel.cde.status;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.notify.NotificationClient;
import eu.amadeos.system.base.notify.dbus.NotificationCenter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationCellTest {
    private static final Messages EN = Messages.load(NotificationCell.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);

    private final List<Long> dnd = new ArrayList<>();
    private boolean running = true;
    private int started;

    private NotificationCell cell() {
        return new NotificationCell(new NotificationCell.Daemon() {
            @Override
            public NotificationClient.State state() {
                return new NotificationClient.State(0, false, running);
            }

            @Override
            public void onState(Consumer<NotificationClient.State> listener) {
            }

            @Override
            public boolean showHistory() {
                return running;
            }

            @Override
            public boolean clearAll() {
                return running;
            }

            @Override
            public boolean setDoNotDisturb(long until) {
                dnd.add(until);
                return true;
            }
        }, () -> started++, () -> { }, EN);
    }

    private static List<String> names(JPopupMenu menu) {
        List<String> out = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && item.getName() != null) out.add(item.getName());
        }
        return out;
    }

    @Test
    void tooltip() {
        NotificationCell cell = cell();
        assertEquals("No new notifications", cell.describe(new NotificationClient.State(0, false, true)));
        assertEquals("1 new notification", cell.describe(new NotificationClient.State(1, false, true)));
        assertEquals("3 new notifications · Do not disturb", cell.describe(new NotificationClient.State(3, true, true)));
    }

    @Test
    void menuOffersDoNotDisturbOrItsEnd() {
        NotificationCell cell = cell();
        cell.show(new NotificationClient.State(2, false, true));
        List<String> items = names(cell.buildMenu());
        assertEquals(List.of("notifications.history", "notifications.dnd.hour", "notifications.dnd.tomorrow",
                "notifications.dnd.always", "notifications.clear", "notifications.settings"), items);
        cell.show(new NotificationClient.State(2, true, true));
        JPopupMenu menu = cell.buildMenu();
        assertTrue(names(menu).contains("notifications.dnd.off"));
        assertFalse(names(menu).contains("notifications.dnd.hour"));
    }

    @Test
    void withoutDaemonTheHistoryStartsIt() throws Exception {
        running = false;
        NotificationCell cell = cell();
        cell.showHistory();
        Thread.sleep(200);
        assertEquals(1, started);
    }

    @Test
    void tomorrowIsLeftToTheDaemon() throws Exception {
        NotificationCell cell = cell();
        cell.show(new NotificationClient.State(0, false, true));
        for (Component c : cell.buildMenu().getComponents()) {
            if (c instanceof JMenuItem item && "notifications.dnd.tomorrow".equals(item.getName())) item.doClick();
        }
        Thread.sleep(200);
        assertEquals(List.of(NotificationCenter.DND_TOMORROW), dnd);
    }
}
