package eu.amadeos.system.settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A control of the compact view in a window of its own, as in CDE's Style Manager: OK applies and closes, Apply
 * applies, Cancel closes. Pages that act at once only have Close.
 */
final class ControlDialog extends JDialog {
    private final SettingsPage page;

    ControlDialog(JFrame owner, SettingsPage page) {
        super(owner, SettingsTexts.format("dialog.title", page.category().label()), false);
        this.page = page;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        root.add(page, BorderLayout.CENTER);

        JButton ok = new JButton(SettingsTexts.get("button.ok"));
        JButton apply = new JButton(SettingsTexts.get("button.apply"));
        JButton cancel = new JButton(SettingsTexts.get("button.cancel"));
        ok.addActionListener(e -> {
            if (page.applyChanges()) dispose();
        });
        apply.addActionListener(e -> page.applyChanges());
        cancel.addActionListener(e -> {
            page.cancelled();
            dispose();
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                page.cancelled();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        if (page.category().immediate()) {
            // Pages that act at once have nothing to apply.
            JButton close = new JButton(SettingsTexts.get("button.close"));
            close.addActionListener(e -> dispose());
            buttons.add(close);
            ok = close;
        } else {
            buttons.add(ok);
            buttons.add(apply);
            buttons.add(cancel);
        }
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    SettingsPage page() {
        return page;
    }
}
