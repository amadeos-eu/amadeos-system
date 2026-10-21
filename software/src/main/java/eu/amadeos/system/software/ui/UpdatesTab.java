package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.packages.Change;
import eu.amadeos.system.packages.PackageInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Updates tab: a line that says how many updates wait, the table of them (security updates first and in
 * red, all ticked) next to the details of the chosen one, and Install selected / Security updates only.
 */
final class UpdatesTab extends JPanel {
    static final Color SECURITY = new Color(0xB0, 0x20, 0x20);

    private final Messages msg;
    private final UpdateTableModel model;
    private final JTable table;
    private final DetailsPane details;
    private final JLabel summary = new JLabel(" ");
    private final JLabel lastCheck = new JLabel(" ");
    private final JButton installSelected;
    private final JButton installSecurity;
    private final JButton selectAll;
    private boolean busy;

    /**
     * @param chosen  called with the update whose details to load (on the event dispatch thread)
     * @param install called with the packages to upgrade, or an empty list for all of them
     * @param security called for "security updates only"
     */
    UpdatesTab(Messages msg, Consumer<Change> chosen, Consumer<List<String>> install, Runnable security) {
        super(new BorderLayout(0, 6));
        this.msg = msg;
        this.model = new UpdateTableModel(msg);
        this.table = new JTable(model);
        this.details = new DetailsPane(msg);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        summary.setFont(summary.getFont().deriveFont(Font.BOLD));
        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setOpaque(false);
        head.add(summary, BorderLayout.NORTH);
        head.add(lastCheck, BorderLayout.SOUTH);

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(table.getRowHeight(), 20));
        table.setDefaultRenderer(String.class, new SecurityRenderer());
        int[] widths = {28, 200, 150, 150, 120};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(UpdateTableModel.SELECTED).setMaxWidth(32);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0) {
                details.clear();
            } else {
                Change c = model.update(table.convertRowIndexToModel(row));
                details.show(c, Optional.empty());
                chosen.accept(c);
            }
        });
        model.addTableModelListener(e -> updateButtons());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), details);
        split.setResizeWeight(0.62);
        split.setDividerLocation(560);

        selectAll = new JButton(msg.get("action.select-all"));
        selectAll.addActionListener(e -> model.selectAll(!model.allSelected()));
        installSecurity = new JButton(msg.get("action.install-security"));
        installSecurity.addActionListener(e -> security.run());
        installSelected = new JButton(msg.get("action.install-selected"));
        installSelected.addActionListener(e -> install.accept(model.allSelected() ? List.of() : model.selectedNames()));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(selectAll);
        buttons.add(installSecurity);
        buttons.add(installSelected);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(left, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);

        add(head, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setUpdates(List.of(), Optional.empty());
    }

    void setUpdates(List<Change> updates, Optional<Instant> lastRefresh) {
        String selected = selectedUpdate().map(Change::name).orElse(null);
        model.setUpdates(updates);
        summary.setText(summaryText(msg, model.updates().size(), model.securityCount()));
        lastCheck.setText(lastRefresh.map(t -> msg.format("updates.last-check", Date.from(t)))
                .orElse(msg.get("updates.never-checked")));
        int row = -1;
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.update(i).name().equals(selected)) row = table.convertRowIndexToView(i);
        }
        if (row < 0 && model.getRowCount() > 0) row = 0;
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
        } else {
            details.clear();
        }
        updateButtons();
    }

    /** "12 updates are waiting, 3 of them security updates." */
    static String summaryText(Messages msg, int count, long security) {
        if (count == 0) return msg.get("updates.none");
        return security == 0 ? msg.format("updates.count", count)
                : msg.format("updates.count.security", count, security);
    }

    /** The repository's description of an update, once it has been read. */
    void showDetails(Change update, Optional<PackageInfo> info) {
        if (selectedUpdate().map(c -> c.name().equals(update.name())).orElse(false)) details.show(update, info);
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateButtons();
    }

    private void updateButtons() {
        boolean any = model.getRowCount() > 0;
        installSelected.setEnabled(!busy && !model.selectedNames().isEmpty());
        installSecurity.setEnabled(!busy && model.securityCount() > 0);
        selectAll.setEnabled(!busy && any);
        selectAll.setText(msg.get(any && model.allSelected() ? "action.select-none" : "action.select-all"));
        table.setEnabled(!busy);
    }

    Optional<Change> selectedUpdate() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.update(table.convertRowIndexToModel(row)));
    }

    UpdateTableModel model() {
        return model;
    }

    String summaryText() {
        return summary.getText();
    }

    DetailsPane details() {
        return details;
    }

    JButton installSelectedButton() {
        return installSelected;
    }

    JButton installSecurityButton() {
        return installSecurity;
    }

    /** Security updates in red. */
    private final class SecurityRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            boolean security = model.update(t.convertRowIndexToModel(row)).security();
            if (!selected) setForeground(security ? SECURITY : t.getForeground());
            setToolTipText(column == UpdateTableModel.NAME ? value.toString() : null);
            return this;
        }
    }
}
