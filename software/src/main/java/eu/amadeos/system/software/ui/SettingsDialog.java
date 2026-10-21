package eu.amadeos.system.software.ui;

import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * Settings: whether security updates are installed automatically. Checking needs no setting: Debian's
 * apt-daily timer fetches the package lists once a day and the front panel shows what they offer.
 */
final class SettingsDialog extends JDialog {
    private final JCheckBox automatic;

    /** @param apply called with the new value when it was changed and confirmed */
    SettingsDialog(JFrame owner, Messages msg, boolean automaticNow, Consumer<Boolean> apply) {
        super(owner, msg.get("settings.title"), true);
        automatic = new JCheckBox(msg.get("settings.automatic"), automaticNow);
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        JLabel explain = new JLabel("<html><body style='width:360px'>" + msg.get("settings.automatic.explain")
                + "</body></html>");
        JLabel checking = new JLabel("<html><body style='width:360px'>" + msg.get("settings.checking")
                + "</body></html>");
        for (JComponent c : new JComponent[]{automatic, explain, checking}) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        explain.setBorder(BorderFactory.createEmptyBorder(2, 24, 12, 0));
        body.add(automatic);
        body.add(explain);
        body.add(checking);

        JButton ok = new JButton(msg.get("action.ok"));
        JButton cancel = new JButton(msg.get("action.cancel"));
        ok.addActionListener(e -> {
            dispose();
            if (automatic.isSelected() != automaticNow) apply.accept(automatic.isSelected());
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        getRootPane().setDefaultButton(ok);

        add(body, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    JCheckBox automaticBox() {
        return automatic;
    }
}
