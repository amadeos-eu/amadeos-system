package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.disks.model.Drive;
import eu.amadeos.system.disks.model.FsChoice;
import eu.amadeos.system.disks.model.Layout;
import eu.amadeos.system.disks.model.Segment;
import eu.amadeos.system.disks.model.Sizes;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Arrays;

/** A new partition in free space: size, name, type, and whether it is mounted at every start. */
final class NewPartitionDialog {

    record Result(long size, String label, FsChoice choice, char[] passphrase, boolean mountAtStart) {
        void clear() {
            if (passphrase != null) Arrays.fill(passphrase, '\0');
        }
    }

    private NewPartitionDialog() {
    }

    static Result ask(Component owner, Context c, Drive drive, Segment free) {
        Messages t = c.texts();
        Window w = owner instanceof Window win ? win : javax.swing.SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, t.get("create.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        Result[] out = {null};
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        p.add(Dialogs.left(new JLabel(t.format("create.heading", c.size(free.size()),
                drive.title().isEmpty() ? c.kind(drive) : drive.title()))));
        p.add(Box.createVerticalStrut(8));
        long max = Layout.alignDown(free.size());
        long min = Math.min(max, 16 * Sizes.MIB);
        SizeField size = new SizeField(c, min, max, max);
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sizeRow.add(new JLabel(t.get("create.size")));
        sizeRow.add(size);
        p.add(Dialogs.left(sizeRow));
        p.add(Box.createVerticalStrut(6));
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        nameRow.add(new JLabel(t.get("format.name")));
        JTextField name = new JTextField(t.get("create.default-name"), 14);
        nameRow.add(name);
        p.add(Dialogs.left(nameRow));
        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        typeRow.add(new JLabel(t.get("create.type")));
        JComboBox<FsChoice> type = new JComboBox<>(FsChoice.values());
        type.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean s,
                    boolean f) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, s, f);
                String key = "choice." + ((FsChoice) value).name().toLowerCase(java.util.Locale.ROOT);
                l.setText(t.get(key + ".fs") + " – " + t.get(key));
                return l;
            }
        });
        boolean removable = drive.removable() || drive.kind() == Drive.Kind.STICK || drive.kind() == Drive.Kind.CARD;
        type.setSelectedItem(FsChoice.suggested(free.size(), removable));
        typeRow.add(type);
        p.add(Dialogs.left(typeRow));
        PassphrasePanel pass = new PassphrasePanel(c);
        p.add(Dialogs.left(pass));
        p.add(Box.createVerticalStrut(6));
        JCheckBox atStart = new JCheckBox(t.get("create.mount-at-start"), !removable);
        JLabel atStartHint = Dialogs.hint(t.get("create.mount-at-start.hint"), 330);
        p.add(Dialogs.left(atStart));
        p.add(Dialogs.left(atStartHint));
        Runnable sync = () -> {
            FsChoice chosen = (FsChoice) type.getSelectedItem();
            pass.setVisible(chosen.encrypted());
            atStart.setVisible(!removable && !chosen.encrypted());
            atStartHint.setVisible(atStart.isVisible());
            d.pack();
        };
        type.addActionListener(e -> sync.run());
        JButton cancel = new JButton(t.get("button.cancel"));
        JButton go = new JButton(t.get("button.create"));
        cancel.addActionListener(e -> d.dispose());
        go.addActionListener(e -> {
            FsChoice chosen = (FsChoice) type.getSelectedItem();
            if (chosen.encrypted() && pass.problem() != null) {
                JOptionPane.showMessageDialog(d, t.get(pass.problem()), d.getTitle(), JOptionPane.WARNING_MESSAGE);
                return;
            }
            long bytes = size.value();
            // The last bit of free space: let udisks take all of it (0 = as large as possible).
            if (bytes >= max) bytes = 0;
            out[0] = new Result(bytes, chosen.label(name.getText()), chosen,
                    chosen.encrypted() ? pass.password() : null, atStart.isVisible() && atStart.isSelected());
            d.dispose();
        });
        p.add(Dialogs.left(Dialogs.buttons(cancel, go)));
        d.setContentPane(p);
        d.getRootPane().setDefaultButton(go);
        Dialogs.escapeCloses(d);
        sync.run();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return out[0];
    }
}
