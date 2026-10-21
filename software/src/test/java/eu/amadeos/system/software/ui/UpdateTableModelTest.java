package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.Origin;
import eu.amadeos.system.software.bootstrap.SoftwareModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateTableModelTest {
    static final Messages EN = Messages.load(SoftwareModule.class, SoftwareModule.MESSAGES, Locale.US);
    static final Messages DE = Messages.load(SoftwareModule.class, SoftwareModule.MESSAGES, Locale.GERMANY);
    static final Origin STABLE = new Origin("Debian", "13.1", "stable");
    static final Origin SECURITY = new Origin("Debian-Security", "13", "stable-security");

    static Change update(String name, Origin origin) {
        return new Change(Change.Kind.UPGRADE, name, "1.0", "1.1", List.of(origin), "amd64");
    }

    @Test
    void securityUpdatesComeFirstThenByName() {
        UpdateTableModel model = new UpdateTableModel(EN);
        model.setUpdates(List.of(update("zsh", STABLE), update("openssl", SECURITY), update("bash", STABLE),
                update("libc6", SECURITY)));
        assertEquals(List.of("libc6", "openssl", "bash", "zsh"), model.selectedNames());
        assertEquals(2, model.securityCount());
        assertEquals("Security update", model.getValueAt(0, UpdateTableModel.SOURCE));
        assertEquals("Debian", model.getValueAt(2, UpdateTableModel.SOURCE));
        assertEquals("1.0", model.getValueAt(2, UpdateTableModel.INSTALLED));
        assertEquals("1.1", model.getValueAt(2, UpdateTableModel.NEW));
        assertEquals("Package", model.getColumnName(UpdateTableModel.NAME));
        assertTrue(model.isCellEditable(0, UpdateTableModel.SELECTED));
        assertFalse(model.isCellEditable(0, UpdateTableModel.NAME));
    }

    @Test
    void allAreTickedAndAnUntickedPackageStaysUnticked() {
        UpdateTableModel model = new UpdateTableModel(EN);
        model.setUpdates(List.of(update("bash", STABLE), update("zsh", STABLE)));
        assertTrue(model.allSelected());
        model.setValueAt(false, 1, UpdateTableModel.SELECTED);
        assertFalse(model.allSelected());
        assertEquals(List.of("bash"), model.selectedNames());

        model.setUpdates(List.of(update("bash", STABLE), update("zsh", STABLE), update("vim", STABLE)));
        assertEquals(List.of("bash", "vim"), model.selectedNames());
        assertEquals(Boolean.FALSE, model.getValueAt(2, UpdateTableModel.SELECTED));

        // Once zsh has no update any more, it is forgotten.
        model.setUpdates(List.of(update("bash", STABLE)));
        assertTrue(model.allSelected());

        model.selectAll(false);
        assertEquals(List.of(), model.selectedNames());
        model.selectAll(true);
        assertEquals(List.of("bash"), model.selectedNames());
    }

    @Test
    void summarySaysHowManyWaitAndHowManyAreSecurityUpdates() {
        assertEquals("Your software is up to date.", UpdatesTab.summaryText(EN, 0, 0));
        assertEquals("One update is waiting.", UpdatesTab.summaryText(EN, 1, 0));
        assertEquals("12 updates are waiting.", UpdatesTab.summaryText(EN, 12, 0));
        assertEquals("12 updates are waiting, one of them a security update.", UpdatesTab.summaryText(EN, 12, 1));
        assertEquals("12 updates are waiting, 3 of them security updates.", UpdatesTab.summaryText(EN, 12, 3));
        assertEquals("12 Aktualisierungen warten, davon 3 Sicherheitsupdates.", UpdatesTab.summaryText(DE, 12, 3));
        assertEquals("Eine Aktualisierung wartet, davon ein Sicherheitsupdate.", UpdatesTab.summaryText(DE, 1, 1));
    }
}
