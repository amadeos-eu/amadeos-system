package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;

import javax.swing.table.AbstractTableModel;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The packages of one catalog tab that the filter lets through, each with a check box: whether it is marked
 * in {@link Marks} (to install, update or remove), which all tabs share.
 */
final class CatalogTableModel extends AbstractTableModel {
    static final int MARK = 0;
    static final int NAME = 1;
    static final int SUMMARY = 2;
    static final int STATUS = 3;
    static final int VERSION = 4;
    private static final String[] COLUMNS = {"", "column.name", "column.summary", "column.status",
            "column.version"};

    private final Messages msg;
    private final Marks marks;
    private boolean busy;
    private List<Item> all = List.of();
    private CatalogFilter filter = CatalogFilter.ALL;
    /** What the filter lets through, in all groups. */
    private List<Item> matching = List.of();
    /** The chosen group, null for all. */
    private Group group;
    private List<Item> shown = List.of();
    private final Map<String, Set<Group>> groups = new HashMap<>();

    CatalogTableModel(Messages msg) {
        this(msg, new Marks());
    }

    CatalogTableModel(Messages msg, Marks marks) {
        this.msg = msg;
        this.marks = marks;
        // A mark set in another tab shows here too; fireTableRowsUpdated keeps the chosen row.
        marks.addListener(() -> {
            if (!shown.isEmpty()) fireTableRowsUpdated(0, shown.size() - 1);
        });
    }

    /** While apt works, the marks cannot be changed. */
    void setBusy(boolean busy) {
        this.busy = busy;
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
        return COLUMNS[column].isEmpty() ? "" : msg.get(COLUMNS[column]);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == MARK ? Boolean.class : Object.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == MARK && !busy && Marks.defaultMark(shown.get(row).entry()).isPresent();
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column != MARK) return;
        CatalogEntry e = shown.get(row).entry();
        if (Boolean.TRUE.equals(value)) {
            Marks.defaultMark(e).ifPresent(mark -> marks.mark(e.name(), mark));
        } else {
            marks.unmark(e.name());
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        Item item = shown.get(row);
        CatalogEntry e = item.entry();
        return switch (column) {
            case MARK -> marks.marked(e.name());
            case NAME -> item.title();
            case SUMMARY -> item.summary();
            case STATUS -> marks.get(e.name()).map(m -> markText(msg, m)).orElseGet(() -> status(msg, e));
            case VERSION -> e.installed() ? e.installedVersion() : e.availableVersion();
            default -> "";
        };
    }

    /** "To be installed" and the like, in place of the status while a package is marked. */
    static String markText(Messages msg, Marks.Mark mark) {
        return msg.get(switch (mark) {
            case INSTALL -> "mark.install";
            case UPDATE -> "mark.update";
            case REMOVE -> "mark.remove";
        });
    }

    static String status(Messages msg, CatalogEntry e) {
        if (e.upgradable()) return msg.get("status.update");
        return e.installed() ? msg.get("status.installed") : "";
    }
}
