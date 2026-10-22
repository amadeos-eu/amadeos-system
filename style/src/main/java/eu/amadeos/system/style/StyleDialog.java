package eu.amadeos.system.style;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * A Style Manager control dialog with CDE's buttons: OK applies and closes, Apply applies, Cancel closes.
 */
abstract class StyleDialog extends JDialog {
    private final Consumer<String> status;

    StyleDialog(JFrame owner, StyleCategory category, Consumer<String> status) {
        super(owner, "Style Manager - " + category.label(), false);
        this.status = status;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    // --8<-- [start:layout]
    /** Builds the dialog; call at the end of the subclass constructor. */
    protected final void layoutDialog(JComponent content) {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        root.add(content, BorderLayout.CENTER);

        JButton ok = new JButton("OK");
        JButton apply = new JButton("Apply");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> {
            if (applyChanges()) dispose();
        });
        apply.addActionListener(e -> applyChanges());
        cancel.addActionListener(e -> {
            cancelled();
            dispose();
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancelled();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.add(ok);
        buttons.add(apply);
        buttons.add(cancel);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(getOwner());
    }
    // --8<-- [end:layout]

    private boolean applyChanges() {
        try {
            String message = apply();
            if (message != null) status.accept(message);
            return true;
        } catch (RuntimeException e) {
            status.accept("Could not apply: " + e.getMessage());
            return false;
        }
    }

    /** Called on Cancel or when the window is closed; undoes previews. */
    protected void cancelled() {
    }

    /** Applies the dialog's settings; returns a message for the Style Manager's status line. */
    protected abstract String apply();
}
