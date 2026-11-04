package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.software.appstream.AppData;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One catalog tab (Applications, Command line, Libraries, Other): the packages of the category that the
 * search lets through, applications with their icon and name, installed ones in bold, next to the details
 * of the chosen one.
 */
final class CatalogTab extends JPanel {
    private static final int ICON_SIZE = 24;
    /** Packages without an icon of their own get this much room, so the names line up. */
    private static final Icon NO_ICON = new ImageIcon(new BufferedImage(ICON_SIZE, ICON_SIZE,
            BufferedImage.TYPE_INT_ARGB));

    private final Category category;
    private final Marks marks;
    private final CatalogTableModel model;
    private final JTable table;
    private final CatalogDetails details;
    private final Map<Path, Icon> icons = new HashMap<>();
    private final Messages msg;
    /** The groups on the left of the Applications tab; null in the other tabs. */
    private final JList<GroupRow> groupList;
    private final DefaultListModel<GroupRow> groupRows = new DefaultListModel<>();
    private boolean updatingGroups;
    private boolean withIcons;
    private boolean busy;

    /** A line of the group list: a group (null for all) and how many items the search leaves in it. */
    record GroupRow(Group group, int count) {
    }

    /**
     * @param chosen called with the package whose description to load (on the event dispatch thread)
     */
    CatalogTab(Messages msg, Category category, CatalogDetails.ScreenshotLoader screenshots, Consumer<String> links,
               Consumer<CatalogEntry> chosen, Consumer<CatalogEntry> install, Consumer<CatalogEntry> remove,
               Consumer<CatalogEntry> update) {
        this(msg, new Marks(), category, screenshots, links, chosen, install, remove, update);
    }

    CatalogTab(Messages msg, Marks marks, Category category, CatalogDetails.ScreenshotLoader screenshots,
               Consumer<String> links, Consumer<CatalogEntry> chosen, Consumer<CatalogEntry> install,
               Consumer<CatalogEntry> remove, Consumer<CatalogEntry> update) {
        super(new BorderLayout());
        this.msg = msg;
        this.category = category;
        this.marks = marks;
        this.model = new CatalogTableModel(msg, marks);
        this.table = new JTable(model);
        this.details = new CatalogDetails(msg, screenshots, links, install, remove, update);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(table.getRowHeight(), 20));
        table.setDefaultRenderer(Object.class, new ItemRenderer());
        int[] widths = {28, 190, 300, 150, 120};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(CatalogTableModel.MARK).setMaxWidth(32);
        // Space ticks or unticks the chosen package, whichever column has the focus.
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleMark");
        table.getActionMap().put("toggleMark", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.isEditing()) table.getCellEditor().stopCellEditing();
                toggleMark();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Optional<Item> selected = selected();
            if (selected.isEmpty()) {
                details.clear();
            } else if (!selected.get().equals(details.item())) {
                boolean other = details.item() == null || !details.item().name().equals(selected.get().name());
                details.show(selected.get(), Optional.empty());
                if (other) chosen.accept(selected.get().entry());
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), details);
        split.setResizeWeight(0.55);
        split.setDividerLocation(category == Category.APPLICATION ? 470 : 560);
        if (category == Category.APPLICATION) {
            groupList = new JList<>(groupRows);
            groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            groupList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean selected, boolean focus) {
                    super.getListCellRendererComponent(list, value, index, selected, focus);
                    GroupRow row = (GroupRow) value;
                    setText(msg.format("group.count", msg.get(row.group() == null ? "group.all" : row.group().key),
                            row.count()));
                    setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                    return this;
                }
            });
            groupList.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting() || updatingGroups) return;
                GroupRow row = groupList.getSelectedValue();
                Group group = row == null ? null : row.group();
                if (group != model.group()) {
                    String name = selected().map(Item::name).orElse(null);
                    model.setGroup(group);
                    reselect(name);
                }
            });
            JScrollPane groupScroll = new JScrollPane(groupList);
            groupScroll.setPreferredSize(new Dimension(170, 100));
            JPanel left = new JPanel(new BorderLayout());
            left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
            left.add(groupScroll, BorderLayout.CENTER);
            add(left, BorderLayout.WEST);
        } else {
            groupList = null;
        }
        add(split, BorderLayout.CENTER);
    }

    /** Ticks the chosen package (install, update or remove, as its state says) or unticks it. */
    void toggleMark() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int modelRow = table.convertRowIndexToModel(row);
        if (!model.isCellEditable(modelRow, CatalogTableModel.MARK)) return;
        model.setValueAt(!Boolean.TRUE.equals(model.getValueAt(modelRow, CatalogTableModel.MARK)), modelRow,
                CatalogTableModel.MARK);
    }

    /** The context menu of a row: mark it for installation, update or removal, or unmark it. */
    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) return;
        table.setRowSelectionInterval(row, row);
        markMenu(model.item(table.convertRowIndexToModel(row)).entry()).show(table, e.getX(), e.getY());
    }

    JPopupMenu markMenu(CatalogEntry entry) {
        JPopupMenu menu = new JPopupMenu();
        Set<Marks.Mark> allowed = Marks.allowed(entry);
        Marks.Mark current = marks.get(entry.name()).orElse(null);
        for (Marks.Mark mark : Marks.Mark.values()) {
            if (!allowed.contains(mark)) continue;
            JMenuItem item = new JMenuItem(msg.get(switch (mark) {
                case INSTALL -> "action.mark-install";
                case UPDATE -> "action.mark-update";
                case REMOVE -> "action.mark-remove";
            }));
            item.setEnabled(!busy && mark != current);
            item.addActionListener(a -> marks.mark(entry.name(), mark));
            menu.add(item);
        }
        JMenuItem unmark = new JMenuItem(msg.get("action.unmark"));
        unmark.setEnabled(!busy && current != null);
        unmark.addActionListener(a -> marks.unmark(entry.name()));
        menu.add(unmark);
        return menu;
    }

    /** The groups with their counts for the current search; the chosen group stays chosen. */
    private void updateGroups() {
        if (groupList == null) return;
        updatingGroups = true;
        try {
            Map<Group, Integer> counts = model.counts();
            groupRows.clear();
            groupRows.addElement(new GroupRow(null, model.matching()));
            for (Group group : Group.values()) {
                int count = counts.getOrDefault(group, 0);
                if (count > 0 || group == model.group()) groupRows.addElement(new GroupRow(group, count));
            }
            for (int i = 0; i < groupRows.size(); i++) {
                if (groupRows.get(i).group() == model.group()) groupList.setSelectedIndex(i);
            }
        } finally {
            updatingGroups = false;
        }
    }

    /** Shows only one group (null for all). */
    void selectGroup(Group group) {
        String name = selected().map(Item::name).orElse(null);
        model.setGroup(group);
        updateGroups();
        reselect(name);
    }

    List<GroupRow> groupRows() {
        List<GroupRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < groupRows.size(); i++) rows.add(groupRows.get(i));
        return rows;
    }

    Category category() {
        return category;
    }

    /** New items (after reading the catalog or AppStream again); the chosen package stays chosen. */
    void setItems(List<Item> items) {
        String name = selected().map(Item::name).orElse(null);
        withIcons = items.stream().anyMatch(i -> i.app().flatMap(AppData::icon).isPresent());
        table.setRowHeight(withIcons ? ICON_SIZE + 6 : Math.max(new JTable().getRowHeight(), 20));
        model.setItems(items);
        updateGroups();
        reselect(name);
    }

    void setFilter(CatalogFilter filter) {
        String name = selected().map(Item::name).orElse(null);
        model.setFilter(filter);
        updateGroups();
        reselect(name);
    }

    private void reselect(String name) {
        int row = name == null ? -1 : model.rowOf(name);
        // Otherwise the first: the best match of a search.
        if (row < 0 && model.getRowCount() > 0) row = 0;
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            Item now = model.item(row);
            // The same package with a new state, after installing or removing it.
            if (!now.equals(details.item())) details.show(now, details.info());
        } else {
            table.clearSelection();
            details.clear();
        }
    }

    /** The repository's description of a package, once it has been read. */
    void showDetails(CatalogEntry entry, Optional<PackageInfo> info) {
        Optional<Item> selected = selected();
        if (selected.isPresent() && selected.get().name().equals(entry.name())) details.show(selected.get(), info);
    }

    void select(String name) {
        int row = model.rowOf(name);
        if (row >= 0) table.setRowSelectionInterval(row, row);
    }

    Optional<Item> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.item(table.convertRowIndexToModel(row)));
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        model.setBusy(busy);
        details.setBusy(busy);
    }

    /** The packages the search lets through, in all groups. */
    int shown() {
        return model.matching();
    }

    CatalogTableModel model() {
        return model;
    }

    CatalogDetails details() {
        return details;
    }

    private Icon icon(Item item) {
        Optional<Path> file = item.app().flatMap(AppData::icon);
        if (file.isEmpty()) return NO_ICON;
        return icons.computeIfAbsent(file.get(), f -> {
            ImageIcon image = new ImageIcon(f.toString());
            if (image.getIconWidth() <= 0) return NO_ICON;
            return new ImageIcon(image.getImage().getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
        });
    }

    /** Applications with their icon, installed packages in bold. */
    private final class ItemRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            Item item = model.item(t.convertRowIndexToModel(row));
            setFont(item.entry().installed() ? getFont().deriveFont(Font.BOLD) : getFont().deriveFont(Font.PLAIN));
            // A marked package says in its status what will happen to it; a removal in red.
            Optional<Marks.Mark> mark = marks.get(item.name());
            if (!selected) {
                setForeground(column == CatalogTableModel.STATUS && mark.isPresent()
                        ? (mark.get() == Marks.Mark.REMOVE ? UpdatesTab.SECURITY : CatalogDetails.linkColour(
                        t.getBackground())) : t.getForeground());
            }
            setIcon(column == CatalogTableModel.NAME && withIcons ? icon(item) : null);
            setToolTipText(switch (column) {
                case CatalogTableModel.NAME -> item.title().equals(item.name()) ? null : item.name();
                case CatalogTableModel.SUMMARY -> value == null ? null : value.toString();
                default -> null;
            });
            return this;
        }
    }
}
