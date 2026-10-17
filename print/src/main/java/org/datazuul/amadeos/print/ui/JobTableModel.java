package org.datazuul.amadeos.print.ui;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.print.cups.Job;

import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** The jobs of the chosen printer: number, document, owner, size, submitted, state. */
final class JobTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"column.id", "column.name", "column.user", "column.size", "column.created",
            "column.state"};

    private final Messages msg;
    private final DateTimeFormatter dates;
    private List<Job> jobs = List.of();

    JobTableModel(Messages msg, ZoneId zone) {
        this.msg = msg;
        this.dates = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(msg.locale()).withZone(zone);
    }

    /** Replaces the jobs; true if anything changed, so that the selection is kept when nothing did. */
    boolean setJobs(List<Job> newJobs) {
        if (Objects.equals(jobs, newJobs)) return false;
        jobs = List.copyOf(newJobs);
        fireTableDataChanged();
        return true;
    }

    Job job(int row) {
        return jobs.get(row);
    }

    int rowOf(int jobId) {
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).id() == jobId) return i;
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return jobs.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return msg.get(COLUMNS[column]);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Job job = jobs.get(row);
        return switch (column) {
            case 0 -> job.id();
            case 1 -> job.name();
            case 2 -> job.user();
            case 3 -> job.sizeKb() > 0 ? msg.format("size.kb", job.sizeKb()) : "";
            case 4 -> job.created().map(dates::format).orElse("");
            default -> state(msg, job.state());
        };
    }

    static String state(Messages msg, Job.State state) {
        return msg.get("job." + state.name().toLowerCase(Locale.ROOT));
    }
}
