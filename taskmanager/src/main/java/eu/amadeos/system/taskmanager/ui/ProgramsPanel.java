package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.system.taskmanager.model.ProcessRow;
import eu.amadeos.system.taskmanager.model.ProcessTree;
import eu.amadeos.system.taskmanager.model.ProgramRow;
import eu.amadeos.system.taskmanager.x.WindowInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The Programs tab: one line per program window; Switch To, End Program, Show Processes. */
final class ProgramsPanel extends JPanel {
    private static final Color NOT_RESPONDING = new Color(0xB0, 0x20, 0x20);

    private final Context context;
    private final Messages t;
    private final Model model = new Model();
    private final JTable table;
    private final JButton showProcesses = new JButton();
    private final JButton switchTo = new JButton();
    private final JButton endProgram = new JButton();
    private Snapshot snapshot;

    ProgramsPanel(Context context) {
        super(new BorderLayout(0, 6));
        this.context = context;
        this.t = context.texts();
        this.table = new JTable(model);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Tables.style(table, new int[]{300, 110, 100, 60, 80, 80}, 3, 4, 5);
        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.Padded(JLabel.LEADING) {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tb, v, s, f, r, c);
                boolean hang = t.get("status.not-responding").equals(v);
                comp.setFont(comp.getFont().deriveFont(hang ? Font.BOLD : Font.PLAIN));
                if (hang && !s) comp.setForeground(NOT_RESPONDING);
                else comp.setForeground(s ? tb.getSelectionForeground() : tb.getForeground());
                return comp;
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) switchTo();
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "end");
        table.getActionMap().put("end", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                endProgram();
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "switch");
        table.getActionMap().put("switch", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                switchTo();
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        JLabel hint = new JLabel("<html><div style=\"width:300px\">" + t.get("programs.hint") + "</div></html>");
        hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() * 0.85f));
        bar.add(hint, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        Mnemonics.apply(showProcesses, t.get("action.show-processes")).addActionListener(e -> showProcesses());
        Mnemonics.apply(switchTo, t.get("action.switch-to")).addActionListener(e -> switchTo());
        Mnemonics.apply(endProgram, t.get("action.end-program")).addActionListener(e -> endProgram());
        buttons.add(showProcesses);
        buttons.add(switchTo);
        buttons.add(endProgram);
        bar.add(buttons, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);
        updateButtons();
    }

    void show(Snapshot s) {
        long selected = selected().map(r -> r.window().id()).orElse(0L);
        snapshot = s;
        model.rows = new ArrayList<>(s.programs());
        model.fireTableDataChanged();
        select(selected);
        updateButtons();
    }

    /** Selects the window's line, if it is there. */
    void select(long windowId) {
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).window().id() == windowId) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    JTable table() {
        return table;
    }

    private java.util.Optional<ProgramRow> selected() {
        int i = table.getSelectedRow();
        return i >= 0 && i < model.rows.size() ? java.util.Optional.of(model.rows.get(i)) : java.util.Optional.empty();
    }

    private void updateButtons() {
        boolean any = selected().isPresent();
        boolean pid = selected().map(r -> r.window().pid() > 0).orElse(false);
        switchTo.setEnabled(any);
        endProgram.setEnabled(pid);
        showProcesses.setEnabled(pid);
    }

    private void switchTo() {
        selected().ifPresent(r -> context.monitor().onWorker(w -> w.switchTo(r.window())));
    }

    private void showProcesses() {
        selected().ifPresent(r -> context.showProcess().accept((int) r.window().pid()));
    }

    private void endProgram() {
        ProgramRow r = selected().orElse(null);
        if (r == null || r.window().pid() <= 0 || snapshot == null) return;
        end(javax.swing.SwingUtilities.getWindowAncestor(this), context, r.window(), snapshot.sample().processes());
    }

    /** Ends the program of a window: its process and the processes it started. */
    static void end(java.awt.Window owner, Context context, WindowInfo window, Map<Integer, ProcessRow> processes) {
        int pid = (int) window.pid();
        ProcessRow main = processes.get(pid);
        List<Integer> pids = new ArrayList<>();
        pids.add(pid);
        if (main != null) {
            for (int p : ProcessTree.subtree(pid, processes.values())) if (p != pid) pids.add(p);
        }
        String user = main == null ? "" : main.user();
        int uid = main == null ? context.ownUid() : main.info().uid();
        boolean important = main != null && Context.isImportant(main);
        // A program is one thing to the user: the question names the window, not its processes.
        new EndProcessDialog(owner, context, new EndProcessDialog.Target(window.title(), List.of(pids.toArray(Integer[]::new)),
                uid, user, important)).setVisible(true);
    }

    private final class Model extends AbstractTableModel {
        List<ProgramRow> rows = new ArrayList<>();
        private static final String[] COLUMNS = {"col.program", "col.status", "col.workspace", "col.cpu",
                "col.memory", "col.disk"};

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return t.get(COLUMNS[c]);
        }

        @Override
        public Object getValueAt(int r, int c) {
            ProgramRow p = rows.get(r);
            WindowInfo w = p.window();
            return switch (c) {
                case 0 -> w.title();
                case 1 -> t.get(w.notResponding() ? "status.not-responding"
                        : w.minimized() ? "status.minimized" : "status.running");
                case 2 -> workspace(w.workspace());
                case 3, 4, 5 -> p.known() ? figure(p, c) : "–";
                default -> "";
            };
        }

        private String figure(ProgramRow p, int c) {
            return switch (c) {
                case 3 -> Units.percent(p.cpu(), context.locale());
                case 4 -> Units.bytes(p.memory(), context.locale());
                default -> Units.rate(p.diskRate(), context.locale());
            };
        }

        private String workspace(int index) {
            if (index < 0) return "";
            List<String> names = snapshot == null ? List.of() : snapshot.workspaces();
            return index < names.size() ? (index + 1) + " " + names.get(index) : Integer.toString(index + 1);
        }
    }
}
