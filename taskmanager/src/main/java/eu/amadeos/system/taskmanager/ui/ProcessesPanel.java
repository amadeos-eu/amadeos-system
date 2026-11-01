package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.taskmanager.control.ProcessControl;
import eu.amadeos.system.taskmanager.model.ProcessRow;
import eu.amadeos.system.taskmanager.model.ProcessTree;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The Processes tab: all processes as a tree or a list, with search, sorting and the process menu. */
final class ProcessesPanel extends JPanel {
    private static final int INDENT = 14;
    private static final int COL_NAME = 0, COL_PID = 1, COL_USER = 2, COL_CPU = 3, COL_MEMORY = 4, COL_DISK = 5,
            COL_COMMAND = 6;

    private final Context context;
    private final Messages t;
    private final Model model = new Model();
    private final JTable table;
    private final JTextField search = new JTextField(16);
    private final JCheckBox tree = new JCheckBox();
    private final JCheckBox allUsers = new JCheckBox();
    private final JButton properties = new JButton();
    private final JButton endProcess = new JButton();
    private final Set<Integer> collapsed = new HashSet<>();
    private int sortColumn = COL_NAME;
    private boolean ascending = true;
    private Snapshot snapshot;
    private int wanted;

    ProcessesPanel(Context context) {
        super(new BorderLayout(0, 6));
        this.context = context;
        this.t = context.texts();
        this.table = new JTable(model);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel searchLabel = Mnemonics.apply(new JLabel(), t.get("processes.search"));
        searchLabel.setLabelFor(search);
        left.add(searchLabel);
        left.add(search);
        top.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        Mnemonics.apply(tree, t.get("processes.tree"));
        Mnemonics.apply(allUsers, t.get("processes.all-users"));
        tree.setSelected(true);
        tree.addActionListener(e -> rebuild());
        allUsers.addActionListener(e -> rebuild());
        right.add(tree);
        right.add(allUsers);
        top.add(right, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Tables.style(table, new int[]{230, 60, 70, 60, 75, 80, 280}, COL_PID, COL_CPU, COL_MEMORY, COL_DISK);
        table.getColumnModel().getColumn(COL_NAME).setCellRenderer(new NameRenderer());
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int c = table.columnAtPoint(e.getPoint());
                if (c < 0) return;
                if (c == sortColumn) ascending = !ascending;
                else {
                    sortColumn = c;
                    // Figures from the largest down, texts from A.
                    ascending = c == COL_NAME || c == COL_USER || c == COL_COMMAND || c == COL_PID;
                }
                rebuild();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) table.setRowSelectionInterval(row, row);
                if (e.isPopupTrigger()) popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) popup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0 || !SwingUtilities.isLeftMouseButton(e)) return;
                if (table.columnAtPoint(e.getPoint()) == COL_NAME && onExpander(row, e.getX())) {
                    toggle(row);
                } else if (e.getClickCount() == 2) {
                    showProperties();
                }
            }
        });
        bind(KeyEvent.VK_DELETE, 0, "end", this::endSelected);
        bind(KeyEvent.VK_DELETE, KeyEvent.SHIFT_DOWN_MASK, "kill", this::forceEndSelected);
        bind(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK, "properties", this::showProperties);
        bind(KeyEvent.VK_RIGHT, 0, "expand", () -> setExpanded(true));
        bind(KeyEvent.VK_LEFT, 0, "collapse", () -> setExpanded(false));
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        Mnemonics.apply(properties, t.get("action.properties")).addActionListener(e -> showProperties());
        Mnemonics.apply(endProcess, t.get("action.end-process")).addActionListener(e -> endSelected());
        buttons.add(properties);
        buttons.add(endProcess);
        add(buttons, BorderLayout.SOUTH);
        updateButtons();
    }

    JCheckBox treeBox() {
        return tree;
    }

    JCheckBox allUsersBox() {
        return allUsers;
    }

    JTable table() {
        return table;
    }

    void show(Snapshot s) {
        snapshot = s;
        rebuild();
    }

    /** Selects the process, showing it first: its tree is opened and the search cleared. */
    void select(int pid) {
        wanted = pid;
        if (snapshot == null) return;
        ProcessRow row = snapshot.sample().processes().get(pid);
        if (row == null) return;
        if (!search.getText().isEmpty()) search.setText("");
        if (row.info().uid() != context.ownUid() && !allUsers.isSelected()) allUsers.setSelected(true);
        collapsed.removeAll(ProcessTree.ancestors(pid, snapshot.sample().processes()));
        rebuild();
    }

    // --8<-- [start:rebuild]
    private void rebuild() {
        if (snapshot == null) return;
        int selected = wanted != 0 ? wanted : selected().map(ProcessRow::pid).orElse(0);
        wanted = 0;
        List<ProcessRow> rows = new ArrayList<>();
        for (ProcessRow r : snapshot.sample().processes().values()) {
            if (r.info().kernelThread() && !allUsers.isSelected()) continue;
            if (allUsers.isSelected() || r.info().uid() == context.ownUid()) rows.add(r);
        }
        model.lines = ProcessTree.lines(rows, order(), tree.isSelected(), collapsed, search.getText());
        model.fireTableDataChanged();
        for (int c = 0; c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setHeaderValue(model.getColumnName(c));
        }
        table.getTableHeader().repaint();
        for (int i = 0; i < model.lines.size(); i++) {
            if (model.lines.get(i).row().pid() == selected) {
                table.setRowSelectionInterval(i, i);
                Rectangle cell = table.getCellRect(i, 0, true);
                if (!table.getVisibleRect().contains(cell)) table.scrollRectToVisible(cell);
                break;
            }
        }
        updateButtons();
    }
    // --8<-- [end:rebuild]

    private Comparator<ProcessRow> order() {
        Comparator<ProcessRow> c = switch (sortColumn) {
            case COL_PID -> Comparator.comparingInt(ProcessRow::pid);
            case COL_USER -> Comparator.comparing(ProcessRow::user, String.CASE_INSENSITIVE_ORDER);
            case COL_CPU -> Comparator.comparingDouble(ProcessRow::cpu);
            case COL_MEMORY -> Comparator.comparingLong(ProcessRow::memory);
            case COL_DISK -> Comparator.comparingDouble(ProcessRow::diskRate);
            case COL_COMMAND -> Comparator.comparing(r -> r.info().command(), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(ProcessRow::name, String.CASE_INSENSITIVE_ORDER);
        };
        if (!ascending) c = c.reversed();
        return c.thenComparingInt(ProcessRow::pid);
    }

    private Optional<ProcessRow> selected() {
        int i = table.getSelectedRow();
        return i >= 0 && i < model.lines.size() ? Optional.of(model.lines.get(i).row()) : Optional.empty();
    }

    private void updateButtons() {
        boolean any = selected().isPresent();
        properties.setEnabled(any);
        endProcess.setEnabled(any);
    }

    private boolean onExpander(int row, int x) {
        ProcessTree.Line line = model.lines.get(row);
        if (!line.hasChildren()) return false;
        Rectangle cell = table.getCellRect(row, COL_NAME, false);
        int start = cell.x + 4 + line.depth() * INDENT;
        return x >= start && x < start + INDENT + 2;
    }

    private void toggle(int row) {
        ProcessTree.Line line = model.lines.get(row);
        if (!line.hasChildren()) return;
        if (!collapsed.remove(line.row().pid())) collapsed.add(line.row().pid());
        wanted = line.row().pid();
        rebuild();
    }

    private void setExpanded(boolean expanded) {
        int row = table.getSelectedRow();
        if (row < 0 || !tree.isSelected()) return;
        ProcessTree.Line line = model.lines.get(row);
        if (line.hasChildren() && line.expanded() != expanded) toggle(row);
    }

    private void bind(int key, int modifiers, String name, Runnable action) {
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key, modifiers), name);
        table.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---- the process menu ----------------------------------------------------------------------------------

    /** The process menu, for the right mouse button and the menu bar. */
    JPopupMenu menu() {
        JPopupMenu m = new JPopupMenu();
        for (Component c : menuItems()) m.add(c);
        return m;
    }

    /** The entries of the process menu for the selected process. */
    List<Component> menuItems() {
        ProcessRow row = selected().orElse(null);
        boolean any = row != null;
        List<Component> items = new ArrayList<>();
        items.add(item("action.end-process", any, this::endSelected, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)));
        items.add(item("action.end-tree", any && hasChildren(row), this::endTree, null));
        items.add(item("action.force-end", any, this::forceEndSelected,
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, KeyEvent.SHIFT_DOWN_MASK)));
        items.add(new JPopupMenu.Separator());
        boolean paused = any && row.info().state() == 'T';
        items.add(item("action.pause", any && !paused, () -> signal(row, false), null));
        items.add(item("action.continue", paused, () -> signal(row, true), null));
        JMenu priority = Mnemonics.apply(new JMenu(), t.get("action.priority"));
        priority.setEnabled(any);
        if (any) {
            ButtonGroup group = new ButtonGroup();
            ProcessControl.Priority now = ProcessControl.Priority.of(row.info().nice());
            for (ProcessControl.Priority p : ProcessControl.Priority.values()) {
                JRadioButtonMenuItem r = new JRadioButtonMenuItem(t.get("priority." + p.name().toLowerCase(Locale.ROOT)),
                        p == now);
                r.addActionListener(e -> setPriority(row, p));
                group.add(r);
                priority.add(r);
            }
        }
        items.add(priority);
        items.add(new JPopupMenu.Separator());
        items.add(item("action.open-folder", any, () -> openFolder(row), null));
        items.add(item("action.copy-command", any && !row.info().command().isEmpty(), () -> copyCommand(row), null));
        items.add(item("action.properties", any, this::showProperties,
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK)));
        return items;
    }

    private JMenuItem item(String key, boolean enabled, Runnable action, KeyStroke accelerator) {
        JMenuItem i = Mnemonics.apply(new JMenuItem(), t.get(key));
        i.setEnabled(enabled);
        if (accelerator != null) i.setAccelerator(accelerator);
        i.addActionListener(e -> action.run());
        return i;
    }

    private void popup(MouseEvent e) {
        if (selected().isEmpty()) return;
        menu().show(table, e.getX(), e.getY());
    }

    private boolean hasChildren(ProcessRow row) {
        return snapshot != null && ProcessTree.subtree(row.pid(), snapshot.sample().processes().values()).size() > 1;
    }

    // ---- actions --------------------------------------------------------------------------------------------

    private void endSelected() {
        selected().ifPresent(r -> end(r, List.of(r.pid())));
    }

    private void endTree() {
        selected().ifPresent(r -> {
            List<Integer> pids = new ArrayList<>();
            pids.add(r.pid());
            for (int p : ProcessTree.subtree(r.pid(), snapshot.sample().processes().values())) {
                if (p != r.pid()) pids.add(p);
            }
            end(r, pids);
        });
    }

    private void end(ProcessRow r, List<Integer> pids) {
        new EndProcessDialog(SwingUtilities.getWindowAncestor(this), context,
                new EndProcessDialog.Target(r.name(), pids, r.info().uid(), r.user(), Context.isImportant(r)))
                .setVisible(true);
    }

    private void forceEndSelected() {
        ProcessRow r = selected().orElse(null);
        if (r == null) return;
        if (javax.swing.JOptionPane.showConfirmDialog(this,
                t.format("end.question", r.name()) + "\n" + t.get("end.explain"), t.get("end.title"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE)
                != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        background(() -> context.control().kill(r.pid()), "error.verb.end");
    }

    private void signal(ProcessRow r, boolean resume) {
        background(() -> {
            if (resume) context.control().resume(r.pid()); else context.control().pause(r.pid());
        }, resume ? "error.verb.continue" : "error.verb.pause");
    }

    private void setPriority(ProcessRow r, ProcessControl.Priority p) {
        background(() -> context.control().setPriority(r.pid(), r.info().nice(), p), "error.verb.priority");
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private void background(IoAction action, String verbKey) {
        Thread.ofVirtual().start(() -> {
            try {
                action.run();
                context.monitor().refresh();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> context.failed(this, verbKey, e));
            }
        });
    }

    private void openFolder(ProcessRow r) {
        String exe = context.monitor().sampler().proc().details(r.pid()).executable();
        if (exe.isEmpty()) {
            List<String> argv = List.of(r.info().command().split(" "));
            exe = argv.isEmpty() ? "" : argv.get(0);
        }
        Path parent = exe.isEmpty() ? null : Path.of(exe).getParent();
        if (parent == null) return;
        try {
            ProcessLauncher.start(List.of("xdg-open", parent.toString()));
        } catch (IOException e) {
            context.failed(this, "error.verb.end", e);
        }
    }

    private void copyCommand(ProcessRow r) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(r.info().command()), null);
    }

    private void showProperties() {
        selected().ifPresent(r -> new PropertiesDialog(SwingUtilities.getWindowAncestor(this), context, r,
                snapshot.sample().processes()).setVisible(true));
    }

    // ---- table ----------------------------------------------------------------------------------------------

    private final class NameRenderer extends Tables.Padded {
        private ProcessTree.Line line;

        NameRenderer() {
            super(JLabel.LEADING);
        }

        @Override
        public Component getTableCellRendererComponent(JTable tb, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(tb, value, selected, focus, row, column);
            line = row < model.lines.size() ? model.lines.get(row) : null;
            int indent = line == null || !tree.isSelected() ? 0 : line.depth() * INDENT + INDENT;
            l.setBorder(BorderFactory.createEmptyBorder(0, 4 + indent, 0, 10));
            return l;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (line == null || !line.hasChildren() || !tree.isSelected()) return;
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getForeground());
            int x = 4 + line.depth() * INDENT + 2, cy = getHeight() / 2;
            java.awt.Polygon p = new java.awt.Polygon();
            if (line.expanded()) {
                p.addPoint(x, cy - 3);
                p.addPoint(x + 8, cy - 3);
                p.addPoint(x + 4, cy + 2);
            } else {
                p.addPoint(x + 2, cy - 4);
                p.addPoint(x + 7, cy);
                p.addPoint(x + 2, cy + 4);
            }
            g2.fill(p);
            g2.dispose();
        }
    }

    private final class Model extends AbstractTableModel {
        List<ProcessTree.Line> lines = new ArrayList<>();

        @Override
        public int getRowCount() {
            return lines.size();
        }

        @Override
        public int getColumnCount() {
            return 7;
        }

        @Override
        public String getColumnName(int c) {
            String name = t.get(switch (c) {
                case COL_PID -> "col.pid";
                case COL_USER -> "col.user";
                case COL_CPU -> "col.cpu";
                case COL_MEMORY -> "col.memory";
                case COL_DISK -> "col.disk";
                case COL_COMMAND -> "col.command";
                default -> "col.name";
            });
            return c == sortColumn ? name + (ascending ? " ▲" : " ▼") : name;
        }

        @Override
        public Object getValueAt(int r, int c) {
            ProcessRow p = lines.get(r).row();
            Locale l = context.locale();
            return switch (c) {
                case COL_PID -> Integer.toString(p.pid());
                case COL_USER -> p.user();
                case COL_CPU -> Units.percent(p.cpu(), l);
                case COL_MEMORY -> Units.bytes(p.memory(), l);
                case COL_DISK -> Units.rate(p.diskRate(), l);
                case COL_COMMAND -> p.info().command();
                default -> p.name();
            };
        }
    }

    /** For the tests: the process ids in the order of the table. */
    List<Integer> shownPids() {
        return model.lines.stream().map(l -> l.row().pid()).toList();
    }

    Map<Integer, ProcessRow> processes() {
        return snapshot == null ? Map.of() : snapshot.sample().processes();
    }
}
