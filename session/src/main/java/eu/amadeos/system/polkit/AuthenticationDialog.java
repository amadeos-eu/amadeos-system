package eu.amadeos.system.polkit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.polkit.Authenticator.Answer;
import eu.amadeos.system.polkit.Authenticator.Request;
import eu.amadeos.system.polkit.Users.User;

/**
 * The password dialog of the agent, in the desktop's Motif style: polkit's message, who authenticates (a
 * choice when several administrators exist), the PAM prompt with its field, a status line and, on request,
 * the details of the action (for pkexec the command line). One window, reused for every request; the
 * methods of {@link Authenticator.Prompter} may be called from any thread.
 */
final class AuthenticationDialog implements Authenticator.Prompter {
    private static final Color ERROR = new Color(0xB0, 0x20, 0x20);
    /** Texts wrap at this width, so a long command line does not stretch the window. */
    private static final String WRAP = "<html><body style='width: 380px'>";

    private final Messages msg;
    private JFrame frame;
    private JLabel icon;
    private JLabel message;
    private JComboBox<User> users;
    private JLabel userName;
    private JLabel promptLabel;
    private JPasswordField password;
    private JTextField visible;
    private JLabel status;
    private JToggleButton detailsButton;
    private JScrollPane detailsPane;
    private JTextArea details;
    private JButton authenticate;
    /** The answer the helper waits for; null while no prompt is open. Only touched on the EDT. */
    private CompletableFuture<Answer> pending;
    /** Cancel or another user chosen while no prompt was open (the password being checked): the next answer. */
    private Answer early;
    private boolean closed = true;
    private boolean echo;
    private boolean showingUsers;

    AuthenticationDialog(Messages msg) {
        this.msg = msg;
    }

    @Override
    public void open(Request request) {
        onEdt(() -> {
            if (frame == null) build();
            closed = false;
            early = null;
            icon.setIcon(icon(request.iconName()));
            message.setText(WRAP + "<b>" + escape(request.message()) + "</b></body></html>");
            showingUsers = true;
            users.removeAllItems();
            request.users().forEach(users::addItem);
            showingUsers = false;
            boolean choice = request.users().size() > 1;
            users.setVisible(choice);
            userName.setVisible(!choice);
            userName.setText(request.users().getFirst().label());
            details.setText(detailsText(msg, request.actionId(), request.details()));
            details.setCaretPosition(0);
            detailsButton.setSelected(false);
            detailsPane.setVisible(false);
            setStatus(" ", false);
            setPrompt(msg.get("prompt.password"), false, false);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.toFront();
        });
    }

    @Override
    public Answer ask(User user, String prompt, boolean echo) {
        CompletableFuture<Answer> answer = new CompletableFuture<>();
        onEdt(() -> {
            if (closed || early != null) {
                answer.complete(closed ? new Answer.Cancel() : early);
                early = null;
                return;
            }
            pending = answer;
            setPrompt(prompt.strip(), echo, true);
            if (!status.getForeground().equals(ERROR)) setStatus(" ", false);
        });
        try {
            return answer.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Answer.Cancel();
        } catch (ExecutionException e) {
            return new Answer.Cancel();
        }
    }

    @Override
    public void info(String text) {
        SwingUtilities.invokeLater(() -> setStatus(text, false));
    }

    @Override
    public void error(String text) {
        SwingUtilities.invokeLater(() -> setStatus(text, true));
    }

    @Override
    public void failed() {
        SwingUtilities.invokeLater(() -> {
            setStatus(msg.get("status.failed"), true);
            frame.toFront();
        });
    }

    @Override
    public void close() {
        onEdt(() -> {
            closed = true;
            answer(new Answer.Cancel());
            if (frame != null) frame.setVisible(false);
        });
    }

    // ---- the window ----------------------------------------------------------------------------------

    private void build() {
        frame = new JFrame(msg.get("title"));
        frame.setName("amadeos-polkit-agent");
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }
        });

        icon = new JLabel();
        icon.setVerticalAlignment(SwingConstants.TOP);
        icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        message = new JLabel();
        message.setFont(message.getFont().deriveFont(Font.PLAIN, 13f));
        JLabel intro = new JLabel(WRAP + escape(msg.get("intro")) + "</body></html>");

        users = new JComboBox<>();
        users.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focus) {
                Object shown = value instanceof User user ? user.label() : value;
                return super.getListCellRendererComponent(list, shown, index, selected, focus);
            }
        });
        users.addActionListener(e -> {
            if (!showingUsers && users.getSelectedItem() instanceof User user) answer(new Answer.SwitchUser(user));
        });
        userName = new JLabel();
        promptLabel = new JLabel();
        password = new JPasswordField(22);
        visible = new JTextField(22);
        password.addActionListener(e -> submit());
        visible.addActionListener(e -> submit());
        status = new JLabel(" ");
        status.setFont(status.getFont().deriveFont(Font.BOLD, 11f));

        details = new JTextArea(5, 40);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        detailsPane = new JScrollPane(details);
        detailsPane.setVisible(false);
        detailsButton = new JToggleButton(msg.get("button.details"));
        detailsButton.addActionListener(e -> {
            detailsPane.setVisible(detailsButton.isSelected());
            frame.pack();
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 8, 0);
        form.add(message, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        form.add(intro, c);
        c.gridy++;
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 6, 10);
        form.add(new JLabel(msg.get("label.user")), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 6, 0);
        JPanel user = new JPanel(new BorderLayout());
        user.add(users, BorderLayout.NORTH);
        user.add(userName, BorderLayout.SOUTH);
        form.add(user, c);
        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 6, 10);
        form.add(promptLabel, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 6, 0);
        JPanel field = new JPanel(new BorderLayout());
        field.add(password, BorderLayout.NORTH);
        field.add(visible, BorderLayout.SOUTH);
        form.add(field, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        form.add(status, c);
        c.gridy++;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        form.add(detailsPane, c);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        top.add(icon, BorderLayout.WEST);
        top.add(form, BorderLayout.CENTER);

        authenticate = new JButton(msg.get("button.authenticate"));
        authenticate.addActionListener(e -> submit());
        JButton cancel = new JButton(msg.get("button.cancel"));
        cancel.addActionListener(e -> cancel());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        left.add(detailsButton);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEtchedBorder());
        bar.add(left, BorderLayout.WEST);
        buttons.setBorder(null);
        buttons.add(authenticate);
        buttons.add(cancel);
        bar.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.CENTER);
        content.add(bar, BorderLayout.SOUTH);
        content.registerKeyboardAction(e -> cancel(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.setContentPane(content);
        frame.getRootPane().setDefaultButton(authenticate);
        frame.setResizable(false);
    }

    private void setPrompt(String prompt, boolean echo, boolean ready) {
        this.echo = echo;
        promptLabel.setText(prompt.endsWith(":") ? prompt : prompt + ":");
        password.setVisible(!echo);
        visible.setVisible(echo);
        JTextField active = echo ? visible : password;
        active.setText("");
        active.setEnabled(ready);
        users.setEnabled(ready);
        authenticate.setEnabled(ready);
        if (ready) active.requestFocusInWindow();
        if (frame.isVisible()) frame.pack();
    }

    private void setStatus(String text, boolean error) {
        status.setForeground(error ? ERROR : javax.swing.UIManager.getColor("Label.foreground"));
        status.setText(text.isBlank() ? " " : text);
    }

    private void submit() {
        if (pending == null) return;
        char[] typed = echo ? visible.getText().toCharArray() : password.getPassword();
        password.setText("");
        visible.setText("");
        setPrompt(promptLabel.getText(), echo, false);
        setStatus(msg.get("status.checking"), false);
        answer(new Answer.Text(typed));
    }

    private void cancel() {
        // The helper's run ends with it; the window goes away with close() when the request is over.
        answer(new Answer.Cancel());
    }

    private void answer(Answer answer) {
        CompletableFuture<Answer> waiting = pending;
        pending = null;
        if (waiting != null) {
            waiting.complete(answer);
        } else if (answer instanceof Answer.Text text) {
            Arrays.fill(text.chars(), '\0');
        } else {
            early = answer;
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** The action and polkit's details, sorted, for the Details pane. */
    static String detailsText(Messages msg, String actionId, Map<String, String> details) {
        StringBuilder text = new StringBuilder(msg.format("details.action", actionId));
        for (Map.Entry<String, String> entry : new TreeMap<>(details).entrySet()) {
            text.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return text.toString();
    }

    private static Icon icon(String name) {
        for (String candidate : List.of(name, "dialog-password", "system-lock-screen")) {
            if (candidate == null || candidate.isBlank()) continue;
            Icon found = IconManager.getIcon(candidate, 48);
            if (found != null) return found;
        }
        return null;
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Runs on the EDT and waits; the agent's threads come from D-Bus. */
    private static void onEdt(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    // ---- for tests ----------------------------------------------------------------------------------

    JFrame frame() {
        return frame;
    }

    JPasswordField passwordField() {
        return password;
    }

    JButton authenticateButton() {
        return authenticate;
    }

    JComboBox<User> userChoice() {
        return users;
    }

    String statusText() {
        return status.getText();
    }

    String promptText() {
        return promptLabel.getText();
    }

    String detailsText() {
        return details.getText();
    }

    static <T> T read(Supplier<T> value) {
        if (SwingUtilities.isEventDispatchThread()) return value.get();
        Object[] box = new Object[1];
        onEdt(() -> box[0] = value.get());
        @SuppressWarnings("unchecked")
        T result = (T) box[0];
        return result;
    }
}
