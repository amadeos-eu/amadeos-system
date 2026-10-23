package eu.amadeos.system.settings;

import eu.amadeos.system.keyring.Keyring;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Passwords: the login keyring, where Mail and other programs keep their passwords. Shows whether it is locked
 * and how many passwords it holds, and locks, unlocks and changes its password. The keyring works at once, so
 * the page has nothing to apply; Apply only reads the state again.
 */
final class PasswordsPage extends SettingsPage {
    private final Keyring keyring;
    private final JLabel state = new JLabel();
    private final JLabel items = new JLabel();
    private final JButton lock = new JButton(SettingsTexts.get("passwords.lock"));
    private final JButton unlock = new JButton(SettingsTexts.get("passwords.unlock"));
    private final JButton change = new JButton(SettingsTexts.get("passwords.change"));
    private Keyring.Status shown = Keyring.Status.NONE;

    PasswordsPage(Consumer<String> status, Keyring keyring) {
        super(SettingsCategory.PASSWORDS, status);
        this.keyring = keyring;
        state.setFont(state.getFont().deriveFont(Font.BOLD));
        lock.addActionListener(e -> run(SettingsTexts.get("passwords.status.locked"), () -> keyring.lock()));
        unlock.addActionListener(e -> askUnlock());
        change.addActionListener(e -> askChange());

        layoutSections(
                section(SettingsTexts.get("passwords.keyring.title"),
                        line(new JLabel(SettingsTexts.get("passwords.state")), state),
                        line(new JLabel(SettingsTexts.get("passwords.items")), items),
                        line(lock, unlock, change)),
                note(SettingsTexts.get("passwords.hint")),
                note(SettingsTexts.get("passwords.login.hint")));
        refresh();
    }

    Keyring.Status shown() {
        return shown;
    }

    /** Reads the keyring's state in the background and shows it. */
    void refresh() {
        new SwingWorker<Keyring.Status, Void>() {
            @Override
            protected Keyring.Status doInBackground() {
                return keyring.status();
            }

            @Override
            protected void done() {
                try {
                    show(get());
                } catch (InterruptedException | ExecutionException e) {
                    show(Keyring.Status.NONE);
                }
            }
        }.execute();
    }

    private void show(Keyring.Status s) {
        shown = s;
        state.setText(SettingsTexts.get(!s.available() ? "passwords.state.none"
                : s.locked() ? "passwords.state.locked" : "passwords.state.unlocked"));
        items.setText(s.available() ? SettingsTexts.format("passwords.items.count", s.items()) : "–");
        lock.setEnabled(s.available() && !s.locked());
        unlock.setEnabled(s.available() && s.locked());
        change.setEnabled(s.available());
    }

    private interface KeyringAction {
        void run() throws Exception;
    }

    /** Runs a keyring call in the background, then reads the state again; a failure goes to the status line. */
    private void run(String success, KeyringAction action) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    status(success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    String message = String.valueOf(e.getCause().getMessage());
                    // gnome-keyring answers a wrong password with "The password was invalid".
                    status(message.toLowerCase(java.util.Locale.ROOT).contains("password was invalid")
                            ? SettingsTexts.get("passwords.status.wrong")
                            : SettingsTexts.format("passwords.status.failed", message));
                }
                refresh();
            }
        }.execute();
    }

    private void askUnlock() {
        JPasswordField password = new JPasswordField(20);
        if (!ask(SettingsTexts.get("passwords.unlock.title"), SettingsTexts.get("passwords.unlock.prompt"), password)) {
            return;
        }
        char[] value = password.getPassword();
        run(SettingsTexts.get("passwords.status.unlocked"), () -> {
            try {
                keyring.unlock(value);
            } finally {
                Arrays.fill(value, '\0');
            }
        });
    }

    private void askChange() {
        JPasswordField current = new JPasswordField(20);
        JPasswordField first = new JPasswordField(20);
        JPasswordField second = new JPasswordField(20);
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 6));
        fields.add(new JLabel(SettingsTexts.get("passwords.change.current")));
        fields.add(current);
        fields.add(new JLabel(SettingsTexts.get("passwords.change.new")));
        fields.add(first);
        fields.add(new JLabel(SettingsTexts.get("passwords.change.repeat")));
        fields.add(second);
        if (!ask(SettingsTexts.get("passwords.change.title"), SettingsTexts.get("passwords.change.prompt"), fields)) {
            return;
        }
        char[] old = current.getPassword();
        char[] neu = first.getPassword();
        char[] again = second.getPassword();
        boolean same = Arrays.equals(neu, again);
        Arrays.fill(again, '\0');
        if (!same) {
            Arrays.fill(old, '\0');
            Arrays.fill(neu, '\0');
            status(SettingsTexts.get("passwords.status.mismatch"));
            return;
        }
        run(SettingsTexts.get("passwords.status.changed"), () -> {
            try {
                keyring.changePassword(old, neu);
            } finally {
                Arrays.fill(old, '\0');
                Arrays.fill(neu, '\0');
            }
        });
    }

    private boolean ask(String title, String prompt, java.awt.Component input) {
        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 8));
        panel.add(new JLabel(prompt), java.awt.BorderLayout.NORTH);
        panel.add(input, java.awt.BorderLayout.CENTER);
        return JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    protected String apply() {
        refresh();
        return null;
    }
}
