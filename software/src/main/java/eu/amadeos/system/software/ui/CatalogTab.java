package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.packages.PackageInfo;
import eu.amadeos.system.software.appstream.AppData;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final CatalogTableModel model;
    private final JTable table;
    private final CatalogDetails details;
    private final Map<Path, Icon> icons = new HashMap<>();
    private boolean withIcons;

    /**
     * @param chosen called with the package whose description to load (on the event dispatch thread)
     */
    CatalogTab(Messages msg, Category category, CatalogDetails.ScreenshotLoader screenshots, Consumer<String> links,
               Consumer<CatalogEntry> chosen, Consumer<CatalogEntry> install, Consumer<CatalogEntry> remove,
               Consumer<CatalogEntry> update) {
        super(new BorderLayout());
        this.category = category;
        this.model = new CatalogTableModel(msg);
        this.table = new JTable(model);
        this.details = new CatalogDetails(msg, screenshots, links, install, remove, update);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(table.getRowHeight(), 20));
        table.setDefaultRenderer(Object.class, new ItemRenderer());
        int[] widths = {200, 320, 110, 130};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
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
        split.setDividerLocation(560);
        add(split, BorderLayout.CENTER);
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
        reselect(name);
    }

    void setFilter(CatalogFilter filter) {
        String name = selected().map(Item::name).orElse(null);
        model.setFilter(filter);
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
        details.setBusy(busy);
    }

    /** The packages the filter shows. */
    int shown() {
        return model.getRowCount();
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
