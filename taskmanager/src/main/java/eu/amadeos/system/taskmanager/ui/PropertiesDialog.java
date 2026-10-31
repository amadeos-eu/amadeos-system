package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.system.taskmanager.control.ProcessControl;
import eu.amadeos.system.taskmanager.model.ProcessRow;
import eu.amadeos.system.taskmanager.proc.ProcFs;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

/** The details of one process. */
final class PropertiesDialog extends JDialog {

    PropertiesDialog(Window owner, Context context, ProcessRow row, Map<Integer, ProcessRow> all) {
        super(owner, context.texts().format("props.title", row.name()), ModalityType.MODELESS);
        Messages t = context.texts();
        Locale l = context.locale();
        ProcFs proc = context.monitor().sampler().proc();
        ProcFs.Details d = proc.details(row.pid());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        String unknown = t.get("props.unknown");
        ProcessRow parent = all.get(row.parentPid());
        long startSeconds = proc.bootTimeSeconds() + row.info().startTicks() / ProcFs.TICKS_PER_SECOND;
        String started = proc.bootTimeSeconds() == 0 ? "" : DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(l).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(startSeconds));
        String state = "state." + row.info().state();
        add(grid, c, t.get("props.name"), row.name());
        add(grid, c, t.get("props.pid"), Integer.toString(row.pid()));
        add(grid, c, t.get("props.parent"), parent == null ? Integer.toString(row.parentPid())
                : parent.name() + " (" + parent.pid() + ")");
        add(grid, c, t.get("props.user"), row.user());
        add(grid, c, t.get("props.state"), t.has(state) ? t.get(state) : String.valueOf(row.info().state()));
        add(grid, c, t.get("props.started"), started);
        add(grid, c, t.get("props.cpu-time"), Units.duration(row.info().cpuTicks() / (double) ProcFs.TICKS_PER_SECOND));
        add(grid, c, t.get("props.memory"), Units.bytes(row.memory(), l));
        add(grid, c, t.get("props.threads"), Integer.toString(row.info().threads()));
        add(grid, c, t.get("props.priority"), t.get("priority."
                + ProcessControl.Priority.of(row.info().nice()).name().toLowerCase(Locale.ROOT))
                + " (nice " + row.info().nice() + ")");
        add(grid, c, t.get("props.command"), row.info().command());
        add(grid, c, t.get("props.executable"), d.executable().isEmpty() ? unknown : d.executable());
        add(grid, c, t.get("props.directory"), d.workingDirectory().isEmpty() ? unknown : d.workingDirectory());
        add(grid, c, t.get("props.files"), d.openFiles() < 0 ? unknown : Integer.toString(d.openFiles()));
        add(grid, c, t.get("props.cgroup"), d.cgroup());

        JButton close = Mnemonics.apply(new JButton(), t.get("end.close"));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
        buttons.add(close);
        add(grid, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    /** A label and a read-only field that can be selected and copied. */
    private static void add(JPanel grid, GridBagConstraints c, String label, String value) {
        c.gridx = 0;
        c.weightx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(2, 0, 2, 8);
        grid.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);
        JTextField field = new JTextField(value, 36);
        field.setEditable(false);
        field.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        field.setOpaque(false);
        field.setCaretPosition(0);
        grid.add(field, c);
        c.gridy++;
    }
}
