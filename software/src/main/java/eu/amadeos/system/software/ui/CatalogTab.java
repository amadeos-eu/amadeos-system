package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.packages.PackageInfo;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One catalog tab (Applications, Command line, Libraries, Other): the packages of the category that the
 * search lets through, installed ones in bold, next to the details of the chosen one.
 */
final class CatalogTab extends JPanel {
    private final Category category;
    private final CatalogTableModel model;
    private final JTable table;
    private final CatalogDetails details;

    /**
     * @param chosen called with the package whose description to load (on the event dispatch thread)
     */
    CatalogTab(Messages msg, Category category, Consumer<CatalogEntry> chosen, Consumer<CatalogEntry> install,
               Consumer<CatalogEntry> remove, Consumer<CatalogEntry> update) {
        super(new BorderLayout());
        this.category = category;
        this.model = new CatalogTableModel(msg);
        this.table = new JTable(model);
        this.details = new CatalogDetails(msg, install, remove, update);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(table.getRowHeight(), 20));
        table.setDefaultRenderer(Object.class, new InstalledRenderer());
        int[] widths = {180, 340, 110, 140};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Optional<CatalogEntry> selected = selected();
            if (selected.isEmpty()) {
                details.clear();
            } else if (!selected.get().equals(details.entry())) {
                details.show(selected.get(), Optional.empty());
                chosen.accept(selected.get());
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), details);
        split.setResizeWeight(0.62);
        split.setDividerLocation(600);
        add(split, BorderLayout.CENTER);
    }

    Category category() {
        return category;
    }

    /** New entries (after reading the catalog again); the chosen package stays chosen. */
    void setEntries(List<CatalogEntry> entries) {
        String name = selected().map(CatalogEntry::name).orElse(null);
        model.setEntries(entries);
        reselect(name);
    }

    void setFilter(CatalogFilter filter) {
        String name = selected().map(CatalogEntry::name).orElse(null);
        model.setFilter(filter);
        reselect(name);
    }

    private void reselect(String name) {
        int row = name == null ? -1 : model.rowOf(name);
        // Otherwise the first: the best match of a search.
        if (row < 0 && model.getRowCount() > 0) row = 0;
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            CatalogEntry now = model.entry(row);
            // The same package with a new state, after installing or removing it.
            if (!now.equals(details.entry())) details.show(now, details.info());
        } else {
            table.clearSelection();
            details.clear();
        }
    }

    /** The repository's description of a package, once it has been read. */
    void showDetails(CatalogEntry entry, Optional<PackageInfo> info) {
        if (selected().map(e -> e.name().equals(entry.name())).orElse(false)) details.show(selected().get(), info);
    }

    void select(String name) {
        int row = model.rowOf(name);
        if (row >= 0) table.setRowSelectionInterval(row, row);
    }

    Optional<CatalogEntry> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.entry(table.convertRowIndexToModel(row)));
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

    /** Installed packages in bold. */
    private final class InstalledRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            boolean installed = model.entry(t.convertRowIndexToModel(row)).installed();
            setFont(installed ? getFont().deriveFont(Font.BOLD) : getFont().deriveFont(Font.PLAIN));
            setToolTipText(column == CatalogTableModel.SUMMARY && value != null ? value.toString() : null);
            return this;
        }
    }
}
