package org.datazuul.amadeos.wm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.base.theme.IconManager;
import org.datazuul.amadeos.base.theme.ThemeManager;

/**
 * Helper providing authentic Motif session power dialogs, logout triggers, and screen lock integration.
 */
public final class SessionDialogHelper {
    private static final Logger LOGGER = Logger.getLogger(SessionDialogHelper.class.getName());

    private SessionDialogHelper() {}

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
            JDialog dialog = new JDialog(
                    parent != null ? JOptionPane.getFrameForComponent(parent) : null,
                    "amadeOS Session & Power Control",
                    true);
            dialog.setLayout(new BorderLayout(12, 12));
            DesktopTheme t = ThemeManager.currentTheme();
            dialog.getContentPane().setBackground(t.base());

            JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
            header.setBackground(t.base());
            header.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
            JLabel title = new JLabel("amadeOS Desktop Session", JLabel.CENTER);
            title.setFont(title.getFont().deriveFont(15.0f));
            title.setForeground(t.textActive());
            JLabel subtitle = new JLabel("Select an action to end or control your session:", JLabel.CENTER);
            subtitle.setForeground(t.textActive());
            header.add(title);
            header.add(subtitle);
            dialog.add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
            grid.setBackground(t.base());
            grid.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

            JButton logoutBtn = actionButton("logout", "Log Out", "Restart desktop session");
            logoutBtn.addActionListener(e -> requestSessionAction("restart"));

            JButton shellBtn = actionButton("terminal", "Exit to Shell", "Drop to tty1 console");
            shellBtn.addActionListener(e -> requestSessionAction("shell"));

            JButton shutdownBtn = actionButton("shutdown", "Shut Down", "Power off computer");
            shutdownBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(
                        dialog,
                        "Are you sure you want to shut down the computer?",
                        "Confirm Power Off",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    requestSessionAction("poweroff");
                }
            });

            JButton rebootBtn = actionButton("reboot", "Reboot", "Restart computer");
            rebootBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(
                        dialog,
                        "Are you sure you want to restart the computer?",
                        "Confirm Reboot",
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
            JButton cancelBtn = new JButton("Cancel");
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
        org.datazuul.amadeos.lock.LockLauncher.lockScreen();
    }
}

