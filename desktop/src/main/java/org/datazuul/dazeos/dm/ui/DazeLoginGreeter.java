package org.datazuul.dazeos.dm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.datazuul.dazeos.wm.theme.DesktopTheme;
import org.datazuul.dazeos.wm.theme.ThemeManager;

public final class DazeLoginGreeter extends JFrame {
    public interface LoginCallback {
        void onLogin(String username, String password, String sessionType);
    }

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("EEE, MMM dd  HH:mm:ss");

    private final LoginCallback loginCallback;
    private final Consumer<String> powerActionCallback;
    private final JTextField userField;
    private final JPasswordField passField;
    private final JComboBox<String> sessionCombo;
    private final JLabel statusLabel;
    private final JLabel clockLabel;
    private final JButton loginBtn;

    public DazeLoginGreeter(LoginCallback loginCallback, Consumer<String> powerActionCallback) {
        super("DazeOS Login Greeter");
        this.loginCallback = Objects.requireNonNull(loginCallback);
        this.powerActionCallback = powerActionCallback;

        DesktopTheme theme = ThemeManager.currentTheme();
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds(0, 0, screenSize.width, screenSize.height);

        // Root background panel (Classic UNIX workstation slate-blue #4a6984)
        JPanel rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                Color c1 = new Color(0x3B, 0x54, 0x6E);
                Color c2 = new Color(0x27, 0x38, 0x4A);
                java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, c1, 0, getHeight(), c2);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        rootPanel.setLayout(new BorderLayout());
        setContentPane(rootPanel);

        // 1. TOP HEADER BAR
        JPanel headerBar = new JPanel(new BorderLayout(10, 0));
        headerBar.setOpaque(false);
        headerBar.setBorder(BorderFactory.createEmptyBorder(16, 24, 0, 24));

        String hostName = "dazeos";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}

        JLabel hostLabel = new JLabel("DazeOS UNIX Workstation  [" + hostName + "]");
        hostLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        hostLabel.setForeground(new Color(0xE0, 0xE8, 0xF0));
        headerBar.add(hostLabel, BorderLayout.WEST);

        clockLabel = new JLabel(LocalDateTime.now().format(CLOCK_FMT));
        clockLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        clockLabel.setForeground(new Color(0xD0, 0xD8, 0xE0));
        headerBar.add(clockLabel, BorderLayout.EAST);
        rootPanel.add(headerBar, BorderLayout.NORTH);

        Timer clockTimer = new Timer(1000, e -> clockLabel.setText(LocalDateTime.now().format(CLOCK_FMT)));
        clockTimer.start();

        // 2. CENTER: Motif 3D Beveled Login Card
        JPanel centerContainer = new JPanel(new GridBagLayout());
        centerContainer.setOpaque(false);

        JPanel loginCard = createMotifCard(theme);
        centerContainer.add(loginCard, new GridBagConstraints());
        rootPanel.add(centerContainer, BorderLayout.CENTER);

        // 3. BOTTOM WORKSTATION CONTROLS BAR
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 24));

        JButton rebootBtn = createMotifButton("Reboot...", theme);
        rebootBtn.addActionListener(e -> confirmPowerAction("reboot", "Restart the computer?"));

        JButton poweroffBtn = createMotifButton("Shut Down...", theme);
        poweroffBtn.addActionListener(e -> confirmPowerAction("poweroff", "Shut down and power off the computer?"));

        bottomBar.add(rebootBtn);
        bottomBar.add(poweroffBtn);
        rootPanel.add(bottomBar, BorderLayout.SOUTH);

        // Populate fields and set default focus
        this.userField = new JTextField("dazeos", 16);
        this.passField = new JPasswordField(16);
        this.sessionCombo = new JComboBox<>(new String[]{"DazeOS CDE Desktop", "Failsafe Terminal"});
        this.statusLabel = new JLabel("Please enter your password to begin session.", SwingConstants.CENTER);
        this.loginBtn = createMotifButton("  Log In  ", theme);

        buildCardContent(loginCard, theme);

        // Auto-focus password field when opened
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                passField.requestFocusInWindow();
            }
        });
    }

    private JPanel createMotifCard(DesktopTheme theme) {
        JPanel card = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                DesktopTheme t = ThemeManager.currentTheme();
                int w = getWidth();
                int h = getHeight();

                // Outer 3D bevel (raised)
                g2.setColor(t.highlight());
                g2.drawLine(0, 0, w - 1, 0);
                g2.drawLine(0, 0, 0, h - 1);
                g2.setColor(t.softHighlight());
                g2.drawLine(1, 1, w - 2, 1);
                g2.drawLine(1, 1, 1, h - 2);

                g2.setColor(t.darkShadow());
                g2.drawLine(w - 1, 0, w - 1, h - 1);
                g2.drawLine(0, h - 1, w - 1, h - 1);
                g2.setColor(t.shadow());
                g2.drawLine(w - 2, 1, w - 2, h - 2);
                g2.drawLine(1, h - 2, w - 2, h - 2);
            }
        };
        card.setBackground(theme.base());
        card.setPreferredSize(new Dimension(460, 310));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
        return card;
    }

    private void buildCardContent(JPanel card, DesktopTheme theme) {
        // Card Title / Banner
        JPanel bannerPanel = new JPanel(new BorderLayout());
        bannerPanel.setBackground(theme.activeTitleBg());
        bannerPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JLabel titleText = new JLabel("DazeOS Desktop Login", SwingConstants.CENTER);
        titleText.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleText.setForeground(theme.textActive());
        bannerPanel.add(titleText, BorderLayout.CENTER);
        card.add(bannerPanel, BorderLayout.NORTH);

        // Input Form
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(theme.base());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // User row
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel userLabel = new JLabel("User Name:", SwingConstants.RIGHT);
        userLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        userLabel.setForeground(theme.textActive());
        formPanel.add(userLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        userField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        userField.addActionListener(e -> passField.requestFocusInWindow());
        formPanel.add(userField, gbc);

        // Password row
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        JLabel passLabel = new JLabel("Password:", SwingConstants.RIGHT);
        passLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        passLabel.setForeground(theme.textActive());
        formPanel.add(passLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        passField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        passField.addActionListener(e -> performLogin());
        formPanel.add(passField, gbc);

        // Session row
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        JLabel sessLabel = new JLabel("Session:", SwingConstants.RIGHT);
        sessLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        sessLabel.setForeground(theme.textActive());
        formPanel.add(sessLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.7;
        formPanel.add(sessionCombo, gbc);

        // Recessed status message box
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 4, 4, 4);
        JPanel statusPlate = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                DesktopTheme t = ThemeManager.currentTheme();
                // Sunken recessed plate
                g2.setColor(t.darkShadow());
                g2.drawLine(0, 0, getWidth() - 1, 0);
                g2.drawLine(0, 0, 0, getHeight() - 1);
                g2.setColor(t.highlight());
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
                g2.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
            }
        };
        statusPlate.setBackground(new Color(0xE8, 0xE2, 0xDD));
        statusPlate.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusLabel.setForeground(new Color(0x33, 0x33, 0x33));
        statusPlate.add(statusLabel, BorderLayout.CENTER);
        formPanel.add(statusPlate, gbc);

        card.add(formPanel, BorderLayout.CENTER);

        // Bottom Action Buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setBackground(theme.base());

        JButton clearBtn = createMotifButton("Clear", theme);
        clearBtn.addActionListener(e -> {
            passField.setText("");
            statusLabel.setText("Please enter your password to begin session.");
            statusLabel.setForeground(new Color(0x33, 0x33, 0x33));
            passField.requestFocusInWindow();
        });

        loginBtn.addActionListener(e -> performLogin());

        buttonRow.add(clearBtn);
        buttonRow.add(loginBtn);
        card.add(buttonRow, BorderLayout.SOUTH);
    }

    private JButton createMotifButton(String text, DesktopTheme theme) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        btn.setBackground(theme.base());
        btn.setForeground(theme.textActive());
        btn.setFocusPainted(false);
        return btn;
    }

    private void performLogin() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        String sessionType = (String) sessionCombo.getSelectedItem();

        if (username.isEmpty()) {
            setStatus("Please enter a username.", true);
            userField.requestFocusInWindow();
            return;
        }

        setStatus("Authenticating with system PAM...", false);
        loginBtn.setEnabled(false);

        // Execute authentication on background thread so UI remains responsive
        new Thread(() -> {
            try {
                loginCallback.onLogin(username, password, sessionType);
            } finally {
                SwingUtilities.invokeLater(() -> loginBtn.setEnabled(true));
            }
        }, "DazeOS-Auth-Thread").start();
    }

    public void setStatus(String message, boolean error) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            statusLabel.setForeground(error ? new Color(0x99, 0x11, 0x11) : new Color(0x11, 0x44, 0x11));
            if (error) {
                passField.selectAll();
                passField.requestFocusInWindow();
            }
        });
    }

    public void resetFields() {
        SwingUtilities.invokeLater(() -> {
            passField.setText("");
            statusLabel.setText("Please enter your password to begin session.");
            statusLabel.setForeground(new Color(0x33, 0x33, 0x33));
            passField.requestFocusInWindow();
        });
    }

    private void confirmPowerAction(String action, String message) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                message,
                "Workstation Power Control",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION && powerActionCallback != null) {
            powerActionCallback.accept(action);
        }
    }
}
