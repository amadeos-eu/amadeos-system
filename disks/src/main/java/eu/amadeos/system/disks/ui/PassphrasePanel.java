package eu.amadeos.system.disks.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

/** The password and its repetition for an encrypted drive or partition. */
final class PassphrasePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    static final int MIN_LENGTH = 8;

    private final JPasswordField first = new JPasswordField(18);
    private final JPasswordField second = new JPasswordField(18);

    PassphrasePanel(Context c) {
        super(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 14, 2, 6);
        g.gridy = 0;
        add(new JLabel(c.texts().get("format.password")), g);
        g.gridx = 1;
        add(first, g);
        g.gridy = 1;
        g.gridx = 0;
        add(new JLabel(c.texts().get("format.password.repeat")), g);
        g.gridx = 1;
        add(second, g);
    }

    /** The message key of what is wrong, or null when both match and are long enough. */
    String problem() {
        char[] a = first.getPassword(), b = second.getPassword();
        try {
            if (a.length < MIN_LENGTH) return "format.password.short";
            if (!Arrays.equals(a, b)) return "format.password.different";
            return null;
        } finally {
            Arrays.fill(a, '\0');
            Arrays.fill(b, '\0');
        }
    }

    char[] password() {
        return first.getPassword();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        first.setEnabled(enabled);
        second.setEnabled(enabled);
    }
}
