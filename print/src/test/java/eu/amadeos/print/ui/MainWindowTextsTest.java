package eu.amadeos.print.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.print.bootstrap.PrintModule;
import eu.amadeos.print.cups.Job;
import eu.amadeos.print.cups.Printer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainWindowTextsTest {
    private static final Messages EN = Messages.load(PrintModule.class, PrintModule.MESSAGES, Locale.US);

    @Test
    void printerLineSaysWhatMatters() {
        Printer printer = new Printer("Buero", "Office laser", "Room 2", "HP LaserJet", Printer.State.STOPPED,
                "Out of paper", List.of("media-empty-error"), false, "ipp://localhost/printers/Buero", 1, true);
        assertEquals("Office laser", printer.title());
        assertEquals("Paused · 1 job · default printer · not accepting jobs · Room 2 · Out of paper",
                MainWindow.describe(EN, printer));
        Printer idle = new Printer("Lab", "", "", "", Printer.State.IDLE, "", List.of(), true, "", 0, false);
        assertEquals("Lab", idle.title());
        assertEquals("Ready · no jobs", MainWindow.describe(EN, idle));
    }

    @Test
    void jobRowsAreFormattedAndKeptWhenUnchanged() {
        JobTableModel model = new JobTableModel(EN, ZoneOffset.UTC);
        List<Job> jobs = List.of(new Job(7, "Brief.pdf", "alice", "Buero", Job.State.PENDING, 12,
                Optional.of(Instant.parse("2026-10-17T09:30:00Z"))));
        assertTrue(model.setJobs(jobs));
        assertFalse(model.setJobs(List.copyOf(jobs)));
        assertEquals(1, model.getRowCount());
        assertEquals(7, model.getValueAt(0, 0));
        assertEquals("12 KB", model.getValueAt(0, 3));
        assertEquals("10/17/26, 9:30 AM", model.getValueAt(0, 4).toString().replace(' ', ' '));
        assertEquals("Waiting", model.getValueAt(0, 5));
        assertEquals("Document", model.getColumnName(1));
        assertEquals(0, model.rowOf(7));
        assertEquals(-1, model.rowOf(8));
    }
}
