package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.Health;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * A drive's health: the verdict in words on top, the figures a person understands below it (temperature, life
 * used, data written, hours), then the raw values. Self-tests run inside the drive; the dialog follows them.
 */
final class HealthDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    static final Color WATCH = new Color(0xC0, 0x7A, 0x00);

    private final Context c;
    private final Messages t;
    private final Drive drive;
    private final JLabel verdict = new JLabel(" ");
    private final JLabel reasons = new JLabel(" ");
    private final JPanel figures = new JPanel(new GridBagLayout());
    private final DefaultTableModel rows;
    private final JButton shortTest;
    private final JButton longTest;
    private final javax.swing.Timer follow;

    HealthDialog(Window owner, Context c, Drive drive) {
        super(owner, c.texts().format("health.title", drive.title().isEmpty() ? c.kind(drive) : drive.title()),
                ModalityType.MODELESS);
        this.c = c;
        this.t = c.texts();
        this.drive = drive;
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        verdict.setFont(verdict.getFont().deriveFont(Font.BOLD, verdict.getFont().getSize2D() + 2f));
        top.add(Dialogs.left(verdict));
        top.add(Dialogs.left(reasons));
        top.add(Box.createVerticalStrut(6));
        figures.setAlignmentX(0);
        top.add(figures);
        p.add(top, BorderLayout.NORTH);
        rows = new DefaultTableModel(new Object[]{t.get("health.col.value"), t.get("health.col.now"),
                t.get("health.col.limit"), t.get("health.col.state")}, 0) {
            @Override
            public boolean isCellEditable(int r, int col) {
                return false;
            }
        };
        JTable table = new JTable(rows);
        table.setRowHeight(20);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, s, f, r, col);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                if (!s) l.setForeground(t.get("health.state.ok").equals(v) ? MainWindow.GOOD : MainWindow.BAD);
                return l;
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(480, 170));
        p.add(sp, BorderLayout.CENTER);
        shortTest = new JButton(t.get("health.short-test"));
        longTest = new JButton(t.get("health.long-test"));
        JButton refresh = new JButton(t.get("health.refresh"));
        JButton close = new JButton(t.get("button.close"));
        shortTest.addActionListener(e -> selfTest(false));
        longTest.addActionListener(e -> selfTest(true));
        refresh.addActionListener(e -> load(true));
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(Dialogs.hint(t.get("health.hint"), 460), BorderLayout.NORTH);
        bottom.add(Dialogs.buttons(shortTest, longTest, refresh, close), BorderLayout.SOUTH);
        p.add(bottom, BorderLayout.SOUTH);
        setContentPane(p);
        getRootPane().setDefaultButton(close);
        Dialogs.escapeCloses(this);
        follow = new javax.swing.Timer(10_000, e -> load(false));
        verdict.setText(t.get("health.reading"));
        pack();
        setLocationRelativeTo(owner);
        load(false);
    }

    @Override
    public void dispose() {
        follow.stop();
        super.dispose();
    }

    private void load(boolean refresh) {
        new SwingWorker<Health, Void>() {
            @Override
            protected Health doInBackground() throws Exception {
                return c.service().health(drive, refresh);
            }

            @Override
            protected void done() {
                try {
                    show(get());
                } catch (Exception e) {
                    Errors.show(HealthDialog.this, c, e.getCause() != null ? e.getCause() : e);
                }
            }
        }.execute();
    }

    void show(Health h) {
        String key = "health.verdict." + h.verdict().name().toLowerCase(java.util.Locale.ROOT);
        verdict.setText("● " + t.get(key));
        verdict.setForeground(switch (h.verdict()) {
            case GOOD -> MainWindow.GOOD;
            case WATCH -> WATCH;
            case REPLACE -> MainWindow.BAD;
            case UNKNOWN -> AmadeUI.theme().textInactive();
        });
        StringBuilder why = new StringBuilder();
        for (String r : h.reasons()) {
            if (why.length() > 0) why.append(' ');
            why.append(t.has(r) ? t.get(r) : r);
        }
        reasons.setText(why.length() == 0 ? " " : Dialogs.wrap(why.toString(), 440));
        figures.removeAll();
        int row = 0;
        if (h.temperature() >= 0) figure(row++, "health.temperature", h.temperature() + " °C");
        if (h.lifeUsed() >= 0) figure(row++, "health.life-used", t.format("health.life-used.value",
                Integer.toString(h.lifeUsed())));
        if (h.written() >= 0) figure(row++, "health.written", c.size(h.written()));
        if (h.powerOnHours() >= 0) {
            String hours = t.format("health.hours", Long.toString(h.powerOnHours()));
            if (h.powerCycles() >= 0) hours += ", " + t.format("health.starts", Long.toString(h.powerCycles()));
            figure(row++, "health.powered-on", hours);
        }
        String test = h.selfTestRunning()
                ? t.format("health.self-test.running", Integer.toString(Math.max(0, 100 - h.selfTestLeft())))
                : h.selfTest().isEmpty() ? "–" : t.has("health.self-test." + h.selfTest())
                        ? t.get("health.self-test." + h.selfTest()) : h.selfTest();
        figure(row, "health.self-test", test);
        rows.setRowCount(0);
        for (Health.Row r : h.rows()) {
            String name = t.has("health.row." + r.name()) ? t.get("health.row." + r.name()) : r.name();
            rows.addRow(new Object[]{name, r.value(), r.limit(), t.get(r.ok() ? "health.state.ok" : "health.state.bad")});
        }
        shortTest.setEnabled(!h.selfTestRunning());
        longTest.setEnabled(!h.selfTestRunning());
        if (h.selfTestRunning()) follow.start();
        else follow.stop();
        figures.revalidate();
        pack();
    }

    private void figure(int row, String key, String value) {
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(1, 2, 1, 12);
        g.gridy = row;
        JLabel k = new JLabel(t.get(key));
        k.setForeground(AmadeUI.theme().textInactive());
        figures.add(k, g);
        g.gridx = 1;
        figures.add(new JLabel(value), g);
    }

    private void selfTest(boolean longOne) {
        shortTest.setEnabled(false);
        longTest.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                c.service().startSelfTest(drive, longOne);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Errors.show(HealthDialog.this, c, e.getCause() != null ? e.getCause() : e);
                }
                load(true);
            }
        }.execute();
    }
}
