package org.datazuul.amadeos.wm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.datazuul.amadeos.dm.pam.PamAuthenticator;
import org.datazuul.amadeos.wm.theme.DesktopTheme;
import org.datazuul.amadeos.wm.theme.ThemeManager;

/**
 * Authentic CDE / Motif fullscreen Screen Locker dialog.
 * Covers the entire screen, grabs input, and unlocks only upon valid PAM authentication.
 */
public final class AmadeScreenLocker {
    private static final Logger LOGGER = Logger.getLogger(AmadeScreenLocker.class.getName());

    private static final AtomicBoolean IS_LOCKED = new AtomicBoolean(false);
    private static JDialog activeLockDialog = null;

    private AmadeScreenLocker() {}

    public static boolean isLocked() {
        return IS_LOCKED.get();
    }

    /**
     * Lock the screen immediately with an authentic fullscreen Motif unlock dialog.
     */
    public static void lockScreen() {
        if (!IS_LOCKED.compareAndSet(false, true)) {
            LOGGER.info("Screen is already locked.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                showLockDialog();
            } catch (Exception e) {
                LOGGER.severe("Failed to display lock dialog: " + e.getMessage());
                IS_LOCKED.set(false);
            }
        });
    }

    private static void showLockDialog() {
        DesktopTheme theme = ThemeManager.currentTheme();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int screenW = screenSize.width;
        int screenH = screenSize.height;
        if (gd != null && gd.getDefaultConfiguration() != null) {
            java.awt.Rectangle bounds = gd.getDefaultConfiguration().getBounds();
            if (bounds != null && bounds.width > 0 && bounds.height > 0) {
                screenW = bounds.width;
                screenH = bounds.height;
            }
        }

        JDialog dialog = new JDialog();
        activeLockDialog = dialog;
        dialog.setTitle("amadeOS Workstation Locked");
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.setModal(false);
        dialog.setResizable(false);
        dialog.setBounds(0, 0, screenW, screenH);
        dialog.setPreferredSize(new Dimension(screenW, screenH));

        // Backdrop panel: Dark chiseled slate / stippled pattern background
        JPanel backdrop = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth();
                int h = getHeight();

                // Dark slate background
                Color darkBase = new Color(0x1e, 0x26, 0x2c);
                g.setColor(darkBase);
                g.fillRect(0, 0, w, h);

                // Subtle classic UNIX stipple dots pattern
                g.setColor(new Color(0x16, 0x1d, 0x22));
                for (int y = 0; y < h; y += 4) {
                    for (int x = (y % 8 == 0 ? 0 : 2); x < w; x += 4) {
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
        };

        // Inner CDE / Motif unlock modal card
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth();
                int h = getHeight();

                // 3D Outer Raised Motif Border
                g.setColor(theme.highlight());
                g.drawLine(0, 0, w - 1, 0);
                g.drawLine(0, 0, 0, h - 1);
                g.setColor(theme.softHighlight());
                g.drawLine(1, 1, w - 2, 1);
                g.drawLine(1, 1, 1, h - 2);

                g.setColor(theme.darkShadow());
                g.drawLine(0, h - 1, w - 1, h - 1);
                g.drawLine(w - 1, 0, w - 1, h - 1);
                g.setColor(theme.shadow());
                g.drawLine(1, h - 2, w - 2, h - 2);
                g.drawLine(w - 2, 1, w - 2, h - 2);
            }
        };
        card.setBackground(theme.base());
        card.setPreferredSize(new Dimension(460, 310));
        card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Header: Title plate
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(theme.activeTitleBg());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JLabel titleLabel = new JLabel("amadeOS Workstation Locked", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setForeground(theme.textActive());
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        card.add(headerPanel, BorderLayout.NORTH);

        // Body: Content & Credentials Form
        JPanel bodyPanel = new JPanel(new GridBagLayout());
        bodyPanel.setBackground(theme.base());
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(16, 24, 12, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 6, 4);

        // Padlock icon / notice
        JLabel noticeLabel = new JLabel(
                "<html><center>This screen is locked.<br>Enter your password to unlock the desktop.</center></html>",
                SwingConstants.CENTER
        );
        noticeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        noticeLabel.setForeground(theme.textActive());
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        bodyPanel.add(noticeLabel, gbc);

        // User name display
        String currentUser = System.getProperty("user.name", "amadeos");
        JLabel userPrompt = new JLabel("User:", SwingConstants.RIGHT);
        userPrompt.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        userPrompt.setForeground(theme.textActive());
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.3;
        bodyPanel.add(userPrompt, gbc);

        JLabel userVal = new JLabel(currentUser, SwingConstants.LEFT);
        userVal.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        userVal.setForeground(theme.textActive());
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0.7;
        bodyPanel.add(userVal, gbc);

        // Password input field
        JLabel passPrompt = new JLabel("Password:", SwingConstants.RIGHT);
        passPrompt.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        passPrompt.setForeground(theme.textActive());
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.3;
        bodyPanel.add(passPrompt, gbc);

        JPasswordField passwordField = new JPasswordField(16);
        passwordField.setBackground(theme.sunkenBg());
        passwordField.setForeground(theme.textActive());
        passwordField.setCaretColor(theme.textActive());
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 0.7;
        bodyPanel.add(passwordField, gbc);

        // Status / Error message label
        JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        statusLabel.setForeground(new Color(0xD0, 0x30, 0x30)); // Motif error red
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(8, 4, 4, 4);
        bodyPanel.add(statusLabel, gbc);

        card.add(bodyPanel, BorderLayout.CENTER);

        // Bottom Button Bar: Unlock Button
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(theme.base());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 16, 12, 16));

        JButton unlockBtn = new JButton("Unlock");
        unlockBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        unlockBtn.setPreferredSize(new Dimension(110, 30));
        unlockBtn.setFocusPainted(false);
        buttonPanel.add(unlockBtn);

        card.add(buttonPanel, BorderLayout.SOUTH);
        backdrop.add(card);
        dialog.setContentPane(backdrop);

        // Unlock Action logic
        Runnable unlockAction = () -> {
            String enteredPassword = new String(passwordField.getPassword());
            statusLabel.setForeground(theme.textActive());
            statusLabel.setText("Verifying credentials...");
            unlockBtn.setEnabled(false);
            passwordField.setEnabled(false);

            new Thread(() -> {
                PamAuthenticator authenticator = new PamAuthenticator();
                PamAuthenticator.AuthResult result = authenticator.authenticate(currentUser, enteredPassword);

                SwingUtilities.invokeLater(() -> {
                    if (result.success()) {
                        LOGGER.info("Screen unlocked successfully for user " + currentUser);
                        if (result.session() != null) {
                            result.session().close();
                        }
                        dialog.dispose();
                        activeLockDialog = null;
                        IS_LOCKED.set(false);
                    } else {
                        LOGGER.warning("Failed unlock attempt: " + result.errorMessage());
                        statusLabel.setForeground(new Color(0xD0, 0x30, 0x30));
                        statusLabel.setText(result.errorMessage() != null ? result.errorMessage() : "Authentication failed");
                        passwordField.setText("");
                        passwordField.setEnabled(true);
                        unlockBtn.setEnabled(true);
                        passwordField.requestFocusInWindow();
                    }
                });
            }, "amadeos-screen-unlocker").start();
        };

        unlockBtn.addActionListener(e -> unlockAction.run());
        passwordField.addActionListener(e -> unlockAction.run());

        // Keyboard navigation and security lock
        dialog.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    unlockAction.run();
                }
            }
        });

        // Ensure window cannot be dismissed without unlocking
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Ignore closing attempts
            }

            @Override
            public void windowOpened(WindowEvent e) {
                dialog.toFront();
                dialog.requestFocus();
                passwordField.requestFocusInWindow();
            }
        });

        dialog.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                passwordField.requestFocusInWindow();
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                if (IS_LOCKED.get() && dialog.isVisible()) {
                    SwingUtilities.invokeLater(() -> {
                        if (IS_LOCKED.get() && dialog.isVisible()) {
                            dialog.toFront();
                            passwordField.requestFocusInWindow();
                        }
                    });
                }
            }
        });

        dialog.setVisible(true);
        dialog.toFront();
        dialog.requestFocus();
        passwordField.requestFocusInWindow();
        LOGGER.info("amadeOS Screen Locker activated for user " + currentUser);
    }

    /**
     * Test hook to forcibly dismiss the screen locker.
     */
    public static void forceUnlockForTesting() {
        IS_LOCKED.set(false);
        if (activeLockDialog != null) {
            SwingUtilities.invokeLater(() -> {
                if (activeLockDialog != null) {
                    activeLockDialog.dispose();
                    activeLockDialog = null;
                }
            });
        }
    }
}
