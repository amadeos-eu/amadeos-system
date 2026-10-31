package eu.amadeos.system.settings;

import eu.amadeos.system.notify.NotificationSettings;
import eu.amadeos.system.notify.NotificationSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class NotificationsPageTest {
    @TempDir
    Path dir;

    @Test
    void writesWhatTheDaemonFollowsAndKeepsWhatItLearnedMeanwhile() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        NotificationSettingsStore store = new NotificationSettingsStore(dir.resolve(NotificationSettingsStore.FILE_NAME));
        store.save(NotificationSettings.DEFAULTS.withProgram("firefox-esr", null, "Firefox")
                .withProgram("amade-backup", NotificationSettings.Mode.HISTORY, "Backup & Sync"));
        List<List<String>> launched = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            NotificationsPage page = new NotificationsPage(s -> { }, store, launched::add);
            NotificationsPage.ProgramsModel programs = (NotificationsPage.ProgramsModel)
                    ((javax.swing.JTable) find(page, "notifications.programs")).getModel();
            assertEquals("Backup & Sync", programs.getValueAt(0, 0));
            assertEquals("History only", programs.getValueAt(0, 1));
            assertEquals("Firefox", programs.getValueAt(1, 0));
            programs.setValueAt("Off", 1, 1);
            ((javax.swing.JComboBox<?>) find(page, "notifications.corner")).setSelectedIndex(1);
            ((javax.swing.JCheckBox) find(page, "notifications.dnd")).doClick();
            // Meanwhile the daemon met a new program.
            store.save(store.load().withProgram("thunderbird", null, "Thunderbird"));
            assertEquals("Notifications applied", page.apply());
            ((javax.swing.JButton) find(page, "notifications.history")).doClick();
        });
        NotificationSettings s = store.load();
        assertEquals(NotificationSettings.Corner.TOP_RIGHT, s.corner());
        assertEquals(NotificationSettings.DND_ON, s.dndUntil());
        assertEquals(Map.of("amade-backup", NotificationSettings.Mode.HISTORY, "firefox-esr",
                NotificationSettings.Mode.OFF), s.programs());
        assertTrue(s.names().containsKey("thunderbird"));
        assertEquals(List.of(List.of("amadeos-notifications")), launched);
    }

    private static java.awt.Component find(java.awt.Container root, String name) {
        for (java.awt.Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof java.awt.Container inner) {
                java.awt.Component found = find(inner, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
