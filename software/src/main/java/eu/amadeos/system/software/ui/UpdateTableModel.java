package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.Change;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The waiting updates, security updates first, each with a check box. All are selected when they appear; a
 * package the user unticked stays unticked when the list is read again.
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
    private final Set<String> unticked = new HashSet<>();

    UpdateTableModel(Messages msg) {
        this.msg = msg;
    }

    /** Security updates first, then by name. */
    static List<Change> order(List<Change> updates) {
        return updates.stream()
                .sorted(Comparator.comparing((Change c) -> !c.security()).thenComparing(Change::name))
                .toList();
    }

    void setUpdates(List<Change> pending) {
        updates = order(pending);
        Set<String> names = new HashSet<>();
        for (Change c : updates) names.add(c.name());
        unticked.retainAll(names);
        fireTableDataChanged();
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
            if (!unticked.contains(c.name())) names.add(c.name());
        }
        return names;
    }

    boolean allSelected() {
        return unticked.isEmpty();
    }

    void selectAll(boolean selected) {
        unticked.clear();
        if (!selected) updates.forEach(c -> unticked.add(c.name()));
        fireTableDataChanged();
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
            case SELECTED -> !unticked.contains(c.name());
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
            unticked.remove(name);
        } else {
            unticked.add(name);
        }
        fireTableCellUpdated(row, column);
    }
}
