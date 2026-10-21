package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** The packages of one catalog tab that the filter lets through. */
final class CatalogTableModel extends AbstractTableModel {
    static final int NAME = 0;
    static final int SUMMARY = 1;
    static final int STATUS = 2;
    static final int VERSION = 3;
    private static final String[] COLUMNS = {"column.package", "column.summary", "column.status", "column.version"};

    private final Messages msg;
    private List<CatalogEntry> all = List.of();
    private CatalogFilter filter = CatalogFilter.ALL;
    private List<CatalogEntry> shown = List.of();

    CatalogTableModel(Messages msg) {
        this.msg = msg;
    }

    void setEntries(List<CatalogEntry> entries) {
        all = List.copyOf(entries);
        update();
    }

    void setFilter(CatalogFilter filter) {
        this.filter = filter;
        update();
    }

    private void update() {
        shown = filter.apply(all);
        fireTableDataChanged();
    }

    CatalogEntry entry(int row) {
        return shown.get(row);
    }

    /** The row of a package, -1 when it is not shown. */
    int rowOf(String name) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).name().equals(name)) return i;
        }
        return -1;
    }

    /** All packages of the tab, whatever the filter. */
    int total() {
        return all.size();
    }

    @Override
    public int getRowCount() {
        return shown.size();
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
    public Object getValueAt(int row, int column) {
        CatalogEntry e = shown.get(row);
        return switch (column) {
            case NAME -> e.name();
            case SUMMARY -> e.summary();
            case STATUS -> status(msg, e);
            case VERSION -> e.installed() ? e.installedVersion() : e.availableVersion();
            default -> "";
        };
    }

    static String status(Messages msg, CatalogEntry e) {
        if (e.upgradable()) return msg.get("status.update");
        return e.installed() ? msg.get("status.installed") : "";
    }
}
