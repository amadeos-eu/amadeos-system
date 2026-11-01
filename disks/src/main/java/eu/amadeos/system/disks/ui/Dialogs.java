package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;

/** Small helpers for the dialogs of Disks, and the one confirmation before anything is erased. */
final class Dialogs {
    static final Color DANGER = new Color(0xB0, 0x20, 0x20);

    private Dialogs() {
    }

    /** Text that wraps at a width, for labels and option panes. */
    static String wrap(String text, int width) {
        return "<html><div style=\"width:" + width + "px\">" + escape(text).replace("\n", "<br>") + "</div></html>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static JLabel bold(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    static JLabel hint(String text, int width) {
        JLabel l = new JLabel(wrap(text, width));
        l.setForeground(AmadeUI.theme().textInactive());
        return l;
    }

    static <C extends JComponent> C left(C c) {
        c.setAlignmentX(0);
        return c;
    }

    static JPanel buttons(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        for (JButton b : buttons) p.add(b);
        return p;
    }

    /** Escape closes the dialog like Cancel. */
    static void escapeCloses(JDialog d) {
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // --8<-- [start:confirm]
    /**
     * The confirmation before data is lost: names the drive, lists what goes, says it cannot be undone. Cancel is
     * the default button; the other one is red and says what happens.
     *
     * @return true when the user chose the red button
     */
    static boolean confirm(Component owner, Context c, String title, String heading, List<String> lost,
            String after, String action) {
        Window w = owner instanceof Window win ? win : javax.swing.SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] yes = {false};
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        p.add(new JLabel(UIManager.getIcon("OptionPane.warningIcon")), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(left(bold(heading)));
        if (!lost.isEmpty()) {
            text.add(Box.createVerticalStrut(6));
            text.add(left(hint(c.texts().get("confirm.lost"), 340)));
            text.add(Box.createVerticalStrut(2));
            for (String line : lost) text.add(left(new JLabel("   •  " + line)));
        }
        text.add(Box.createVerticalStrut(6));
        text.add(left(hint(after, 340)));
        p.add(text, BorderLayout.CENTER);
        JButton cancel = new JButton(c.texts().get("button.cancel"));
        JButton go = new JButton(action);
        go.setForeground(DANGER);
        cancel.addActionListener(e -> d.dispose());
        go.addActionListener(e -> {
            yes[0] = true;
            d.dispose();
        });
        p.add(buttons(cancel, go), BorderLayout.SOUTH);
        d.setContentPane(p);
        JRootPane root = d.getRootPane();
        root.setDefaultButton(cancel);
        escapeCloses(d);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        cancel.requestFocusInWindow();
        d.setVisible(true);
        return yes[0];
    }
    // --8<-- [end:confirm]
}
