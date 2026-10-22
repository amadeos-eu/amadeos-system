package eu.amadeos.system.base.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * Helper providing authentic Motif session power dialogs, logout triggers, and screen lock integration.
 */
public final class SessionDialogHelper {
    private static final Logger LOGGER = Logger.getLogger(SessionDialogHelper.class.getName());

    /** The texts of the dialog, {@code session.properties} and its translations. */
    public static final String TEXTS = "eu.amadeos.system.base.ui.session";

    private SessionDialogHelper() {}

    /** The texts in the language of the session, which the session sets from {@code locale.conf}. */
    static Messages texts() {
        Locale locale = LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale();
        return Messages.load(SessionDialogHelper.class, TEXTS, locale);
    }

    public static void requestSessionAction(String action) {
        try {
            String home = System.getProperty("user.home", "/tmp");
            Files.writeString(
                    new File(home, ".amadeos-session-action").toPath(),
                    action,
                    StandardCharsets.UTF_8);
            LOGGER.info(() -> "Requested session action: " + action);
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to write session action file: " + e.getMessage());
        }
        System.exit(0);
    }

    public static void showSessionPowerDialog(Component parent) {
        SwingUtilities.invokeLater(() -> {
            Messages texts = texts();
            JDialog dialog = new JDialog(
                    parent != null ? JOptionPane.getFrameForComponent(parent) : null,
                    texts.get("title"),
                    true);
            dialog.setLayout(new BorderLayout(12, 12));
            DesktopTheme t = ThemeManager.currentTheme();
            dialog.getContentPane().setBackground(t.base());

            JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
            header.setBackground(t.base());
            header.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
            JLabel title = new JLabel(texts.get("heading"), JLabel.CENTER);
            title.setFont(title.getFont().deriveFont(15.0f));
            title.setForeground(t.textActive());
            JLabel subtitle = new JLabel(texts.get("subtitle"), JLabel.CENTER);
            subtitle.setForeground(t.textActive());
            header.add(title);
            header.add(subtitle);
            dialog.add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
            grid.setBackground(t.base());
            grid.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

            JButton logoutBtn = actionButton("logout", texts.get("logout"), texts.get("logout.explanation"));
            logoutBtn.addActionListener(e -> requestSessionAction("restart"));

            JButton shellBtn = actionButton("terminal", texts.get("shell"), texts.get("shell.explanation"));
            shellBtn.addActionListener(e -> requestSessionAction("shell"));

            JButton shutdownBtn = actionButton("shutdown", texts.get("shutdown"), texts.get("shutdown.explanation"));
            shutdownBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(
                        dialog,
                        texts.get("shutdown.confirm"),
                        texts.get("shutdown.confirm.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    requestSessionAction("poweroff");
                }
            });

            JButton rebootBtn = actionButton("reboot", texts.get("reboot"), texts.get("reboot.explanation"));
            rebootBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(
                        dialog,
                        texts.get("reboot.confirm"),
                        texts.get("reboot.confirm.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    requestSessionAction("reboot");
                }
            });

            grid.add(logoutBtn);
            grid.add(shellBtn);
            grid.add(shutdownBtn);
            grid.add(rebootBtn);
            dialog.add(grid, BorderLayout.CENTER);

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            footer.setBackground(t.base());
            footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
            JButton cancelBtn = new JButton(texts.get("cancel"));
            cancelBtn.addActionListener(e -> dialog.dispose());
            footer.add(cancelBtn);
            dialog.add(footer, BorderLayout.SOUTH);
            dialog.getRootPane().setDefaultButton(cancelBtn);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

            dialog.pack();
            // Centred on the screen: the parent is usually the front panel at the bottom edge
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        });
    }

    /** A large session action button: its icon above the action and a short explanation. */
    private static JButton actionButton(String iconKey, String action, String explanation) {
        JButton button = new JButton(
                "<html><center><b>" + action + "</b><br><small>" + explanation + "</small></center></html>",
                IconManager.getIcon(iconKey, 48));
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(6);
        button.setBorder(BorderFactory.createCompoundBorder(button.getBorder(),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        return button;
    }

    public static void lockScreen(Component parent) {
        eu.amadeos.system.base.process.LockLauncher.lockScreen();
    }
}

