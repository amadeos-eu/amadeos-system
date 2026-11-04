package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.Change;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The waiting updates, security updates first, each with a check box: its update mark in {@link Marks}, which
 * the catalog tabs share. All are marked when they appear; a package the user unticked stays unticked when
 * the list is read again.
 */
final class UpdateTableModel extends AbstractTableModel {
    static final int SELECTED = 0;
    static final int NAME = 1;
    static final int INSTALLED = 2;
    static final int NEW = 3;
    static final int SOURCE = 4;
    private static final String[] COLUMNS = {"", "column.package", "column.installed", "column.new", "column.source"};

    private final Messages msg;
    private List<Change> updates = List.of();
    private final Marks marks;

    UpdateTableModel(Messages msg) {
        this(msg, new Marks());
    }

    UpdateTableModel(Messages msg, Marks marks) {
        this.msg = msg;
        this.marks = marks;
        // A mark set in a catalog tab shows here too; fireTableRowsUpdated keeps the chosen row.
        marks.addListener(() -> {
            if (!updates.isEmpty()) fireTableRowsUpdated(0, updates.size() - 1);
        });
    }

    /** Security updates first, then by name. */
    static List<Change> order(List<Change> updates) {
        return updates.stream()
                .sorted(Comparator.comparing((Change c) -> !c.security()).thenComparing(Change::name))
                .toList();
    }

    void setUpdates(List<Change> pending) {
        updates = order(pending);
        fireTableDataChanged();
        marks.updatesRead(updates.stream().map(Change::name).toList());
    }

    Change update(int row) {
        return updates.get(row);
    }

    List<Change> updates() {
        return updates;
    }

    /** The ticked packages. */
    List<String> selectedNames() {
        List<String> names = new ArrayList<>();
        for (Change c : updates) {
            if (ticked(c)) names.add(c.name());
        }
        return names;
    }

    private boolean ticked(Change c) {
        return marks.get(c.name()).orElse(null) == Marks.Mark.UPDATE;
    }

    boolean allSelected() {
        return updates.stream().allMatch(this::ticked);
    }

    void selectAll(boolean selected) {
        marks.markAll(updates.stream().map(Change::name).toList(), selected ? Marks.Mark.UPDATE : null);
    }

    long securityCount() {
        return updates.stream().filter(Change::security).count();
    }

    @Override
    public int getRowCount() {
        return updates.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column].isEmpty() ? "" : msg.get(COLUMNS[column]);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == SELECTED ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == SELECTED;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Change c = updates.get(row);
        return switch (column) {
            case SELECTED -> ticked(c);
            case NAME -> c.name();
            case INSTALLED -> c.installedVersion();
            case NEW -> c.newVersion();
            case SOURCE -> c.security() ? msg.get("source.security") : c.originLabel();
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column != SELECTED) return;
        String name = updates.get(row).name();
        if (Boolean.TRUE.equals(value)) {
            marks.mark(name, Marks.Mark.UPDATE);
        } else {
            marks.unmark(name);
        }
    }
}
