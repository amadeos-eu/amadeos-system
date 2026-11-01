package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.disks.model.FsChoice;
import eu.amadeos.system.disks.model.Sizes;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Format a whole drive or one partition: a name and what it is for, in plain words; the file system names follow
 * in grey. Returns the choice; the confirmation comes after it.
 */
final class FormatDialog {

    /** What the user chose. */
    record Result(FsChoice choice, String label, boolean overwrite, char[] passphrase) {
        String choiceName(Context c) {
            return c.texts().get("choice." + choice.name().toLowerCase(java.util.Locale.ROOT) + ".short");
        }

        void clear() {
            if (passphrase != null) Arrays.fill(passphrase, '\0');
        }
    }

    private FormatDialog() {
    }

    static Result ask(Component owner, Context c, String heading, long size, boolean removable, boolean whole,
            String currentName) {
        Messages t = c.texts();
        Window w = owner instanceof Window win ? win : javax.swing.SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, t.get(whole ? "format.title.drive" : "format.title.volume"),
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        Result[] out = {null};
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        p.add(Dialogs.left(Dialogs.bold(heading)));
        p.add(Box.createVerticalStrut(10));
        JPanel namePanel = new JPanel(new BorderLayout(8, 0));
        namePanel.add(new JLabel(t.get("format.name")), BorderLayout.WEST);
        JTextField name = new JTextField(currentName == null ? "" : currentName, 18);
        namePanel.add(name, BorderLayout.CENTER);
        p.add(Dialogs.left(namePanel));
        p.add(Box.createVerticalStrut(10));
        p.add(Dialogs.left(new JLabel(t.get("format.use-with"))));
        ButtonGroup group = new ButtonGroup();
        Map<FsChoice, JRadioButton> buttons = new EnumMap<>(FsChoice.class);
        FsChoice suggested = FsChoice.suggested(size, removable);
        for (FsChoice choice : FsChoice.values()) {
            String key = "choice." + choice.name().toLowerCase(java.util.Locale.ROOT);
            JRadioButton rb = new JRadioButton("<html>" + Dialogs.escape(t.get(key)) + "  <font color=\"#6a6a6a\">"
                    + Dialogs.escape(t.get(key + ".fs")) + "</font></html>", choice == suggested);
            rb.setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 0));
            group.add(rb);
            buttons.put(choice, rb);
            p.add(Dialogs.left(rb));
        }
        PassphrasePanel pass = new PassphrasePanel(c);
        pass.setVisible(suggested.encrypted());
        p.add(Dialogs.left(pass));
        p.add(Box.createVerticalStrut(8));
        JCheckBox overwrite = new JCheckBox(t.get("format.overwrite"));
        p.add(Dialogs.left(overwrite));
        p.add(Box.createVerticalStrut(4));
        JLabel fatNote = Dialogs.hint(t.get("format.fat32-note"), 380);
        p.add(Dialogs.left(fatNote));
        p.add(Dialogs.left(Dialogs.hint(t.get(whole ? "format.note.drive" : "format.note.volume"), 380)));
        Runnable sync = () -> {
            FsChoice chosen = chosen(buttons);
            pass.setVisible(chosen.encrypted());
            fatNote.setVisible(chosen == FsChoice.FAT32 && size > 32L * Sizes.GB);
            d.pack();
        };
        buttons.values().forEach(b -> b.addActionListener(e -> sync.run()));
        JButton cancel = new JButton(t.get("button.cancel"));
        JButton go = new JButton(t.get("button.format"));
        cancel.addActionListener(e -> d.dispose());
        go.addActionListener(e -> {
            FsChoice chosen = chosen(buttons);
            if (chosen.encrypted()) {
                String problem = pass.problem();
                if (problem != null) {
                    JOptionPane.showMessageDialog(d, t.get(problem), d.getTitle(), JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            out[0] = new Result(chosen, chosen.label(name.getText()), overwrite.isSelected(),
                    chosen.encrypted() ? pass.password() : null);
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

    private static FsChoice chosen(Map<FsChoice, JRadioButton> buttons) {
        for (var e : buttons.entrySet()) {
            if (e.getValue().isSelected()) return e.getKey();
        }
        return FsChoice.EXFAT;
    }
}
