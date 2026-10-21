package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;

import javax.swing.table.AbstractTableModel;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The packages of one catalog tab that the filter lets through. */
final class CatalogTableModel extends AbstractTableModel {
    static final int NAME = 0;
    static final int SUMMARY = 1;
    static final int STATUS = 2;
    static final int VERSION = 3;
    private static final String[] COLUMNS = {"column.name", "column.summary", "column.status", "column.version"};

    private final Messages msg;
    private List<Item> all = List.of();
    private CatalogFilter filter = CatalogFilter.ALL;
    /** What the filter lets through, in all groups. */
    private List<Item> matching = List.of();
    /** The chosen group, null for all. */
    private Group group;
    private List<Item> shown = List.of();
    private final Map<String, Set<Group>> groups = new HashMap<>();

    CatalogTableModel(Messages msg) {
        this.msg = msg;
    }

    void setItems(List<Item> items) {
        all = List.copyOf(items);
        groups.clear();
        update();
    }

    /** Only the items of a group; null for all. */
    void setGroup(Group group) {
        this.group = group;
        update();
    }

    Group group() {
        return group;
    }

    Set<Group> groups(Item item) {
        return groups.computeIfAbsent(item.name(), n -> Group.of(item));
    }

    /** How many items the filter lets through in each group. */
    Map<Group, Integer> counts() {
        Map<Group, Integer> counts = new EnumMap<>(Group.class);
        for (Item item : matching) {
            for (Group g : groups(item)) counts.merge(g, 1, Integer::sum);
        }
        return counts;
    }

    /** How many items the filter lets through, in all groups. */
    int matching() {
        return matching.size();
    }

    void setFilter(CatalogFilter filter) {
        this.filter = filter;
        update();
    }

    private void update() {
        matching = filter.apply(all);
        shown = group == null ? matching : matching.stream().filter(i -> groups(i).contains(group)).toList();
        fireTableDataChanged();
    }

    Item item(int row) {
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
        Item item = shown.get(row);
        CatalogEntry e = item.entry();
        return switch (column) {
            case NAME -> item.title();
            case SUMMARY -> item.summary();
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
