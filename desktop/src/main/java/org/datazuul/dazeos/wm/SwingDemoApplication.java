package org.datazuul.dazeos.wm;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public final class SwingDemoApplication {
    private static final AtomicInteger WINDOW_COUNTER = new AtomicInteger(0);

    private SwingDemoApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SwingDemoApplication::showDemo);
    }

    private static void showDemo() {
        JFrame mainFrame = new JFrame("DazeOS Desktop - Control Panel");
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                showSessionPowerDialog(mainFrame);
            }
        });
        mainFrame.setLayout(new BorderLayout(10, 10));

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        JLabel titleLabel = new JLabel("DazeOS Desktop Session (M7)", JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16.0f));
        JLabel statusLabel = new JLabel("Reparenting Window Manager with Server-Side 3D Motif Decorations & Interactive Sizing", JLabel.CENTER);
        headerPanel.add(titleLabel);
        headerPanel.add(statusLabel);
        mainFrame.add(headerPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        JPanel row1 = new JPanel(new GridLayout(1, 3, 10, 0));
        JButton newWindowBtn = new JButton("Open Document Window");
        newWindowBtn.addActionListener(e -> spawnDocumentWindow());

        JButton dialogBtn = new JButton("Open Modal Dialog");
        dialogBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(
                    mainFrame,
                    "This is a transient dialog managed by DazeOS.\nNotice how it centers automatically over the parent window!",
                    "DazeOS Dialog",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        JButton terminalBtn = new JButton("Launch Terminal (xterm)");
        terminalBtn.addActionListener(e -> {
            try {
                new ProcessBuilder("xterm").start();
                statusLabel.setText("Launched xterm terminal window.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Failed to launch xterm: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        row1.add(newWindowBtn);
        row1.add(dialogBtn);
        row1.add(terminalBtn);
        centerPanel.add(row1);

        JPanel row2 = new JPanel(new GridLayout(1, 2, 10, 0));
        JButton logsBtn = new JButton("View Session Logs");
        logsBtn.addActionListener(e -> showLogViewerDialog(mainFrame));

        JButton sessionBtn = new JButton("Session & Power Controls...");
        sessionBtn.addActionListener(e -> showSessionPowerDialog(mainFrame));

        row2.add(logsBtn);
        row2.add(sessionBtn);
        centerPanel.add(row2);

        mainFrame.add(centerPanel, BorderLayout.CENTER);

        JLabel footerLabel = new JLabel("DazeOS Live System | OpenJDK 21 | Xorg Native Floating Window Manager", JLabel.CENTER);
        footerLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 12, 10));
        mainFrame.add(footerLabel, BorderLayout.SOUTH);

        mainFrame.setPreferredSize(new Dimension(680, 260));
        mainFrame.setMinimumSize(new Dimension(600, 240));
        mainFrame.pack();
        mainFrame.setLocationByPlatform(true);
        mainFrame.setVisible(true);
    }

    private static void showSessionPowerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "DazeOS Session & Power Control", true);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
        JLabel title = new JLabel("DazeOS Desktop Session (M7)", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(15.0f));
        JLabel subtitle = new JLabel("Select an action to end or control your session:", JLabel.CENTER);
        header.add(title);
        header.add(subtitle);
        dialog.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JButton logoutBtn = new JButton("<html><center><b>Log Out</b><br><small>Restart desktop session</small></center></html>");
        logoutBtn.addActionListener(e -> requestSessionAction("restart"));

        JButton shellBtn = new JButton("<html><center><b>Exit to Shell</b><br><small>Drop to tty1 console</small></center></html>");
        shellBtn.addActionListener(e -> requestSessionAction("shell"));

        JButton shutdownBtn = new JButton("<html><center><b>Shut Down</b><br><small>Power off computer</small></center></html>");
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

        JButton rebootBtn = new JButton("<html><center><b>Reboot</b><br><small>Restart computer</small></center></html>");
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
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        footer.add(cancelBtn);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.setPreferredSize(new Dimension(520, 260));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static void requestSessionAction(String action) {
        try {
            String home = System.getProperty("user.home", "/tmp");
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(home, ".dazeos-session-action"),
                    action,
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        System.exit(0);
    }

    private static void showLogViewerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "DazeOS Session Diagnostics & Logs", false);
        dialog.setLayout(new BorderLayout(8, 8));

        String homeDir = System.getProperty("user.home", "/tmp");
        java.io.File sessionLog = new java.io.File(homeDir, ".dazeos-session.log");
        java.io.File wmLog = new java.io.File(homeDir, ".dazeos-wm.log");

        javax.swing.JTabbedPane tabbedPane = new javax.swing.JTabbedPane();

        JTextArea sessionArea = createLogTextArea(sessionLog);
        tabbedPane.addTab("Session Log (~/.dazeos-session.log)", new JScrollPane(sessionArea));

        JTextArea wmArea = createLogTextArea(wmLog);
        tabbedPane.addTab("WM Daemon Log (~/.dazeos-wm.log)", new JScrollPane(wmArea));

        dialog.add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshBtn = new JButton("Reload Logs");
        refreshBtn.addActionListener(e -> {
            sessionArea.setText(readLogContent(sessionLog));
            wmArea.setText(readLogContent(wmLog));
        });
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());

        bottomPanel.add(refreshBtn);
        bottomPanel.add(closeBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setPreferredSize(new Dimension(640, 400));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static JTextArea createLogTextArea(java.io.File file) {
        JTextArea area = new JTextArea(readLogContent(file));
        area.setEditable(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        area.setCaretPosition(area.getDocument().getLength());
        return area;
    }

    private static String readLogContent(java.io.File file) {
        if (!file.exists()) {
            return "(Log file " + file.getAbsolutePath() + " does not exist yet.)";
        }
        try {
            return java.nio.file.Files.readString(file.toPath());
        } catch (Exception e) {
            return "Error reading " + file.getAbsolutePath() + ": " + e.getMessage();
        }
    }

    private static void spawnDocumentWindow() {
        int winNum = WINDOW_COUNTER.incrementAndGet();
        JFrame docFrame = new JFrame("Document Window #" + winNum);
        docFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        docFrame.setLayout(new BorderLayout(8, 8));

        JTextArea textArea = new JTextArea(
                "Welcome to DazeOS Window #" + winNum + "!\n\n"
                + "- Click anywhere in this window to focus and raise it.\n"
                + "- You can type text, resize, or open more windows.\n"
                + "- Close this window when finished.");
        textArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        docFrame.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        AtomicInteger clickCounter = new AtomicInteger(0);
        JButton counterBtn = new JButton("Clicks: 0");
        counterBtn.addActionListener(e -> counterBtn.setText("Clicks: " + clickCounter.incrementAndGet()));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> docFrame.dispose());

        bottomPanel.add(counterBtn);
        bottomPanel.add(closeBtn);
        docFrame.add(bottomPanel, BorderLayout.SOUTH);

        docFrame.setPreferredSize(new Dimension(420, 260));
        docFrame.pack();
        docFrame.setLocationByPlatform(true);
        docFrame.setVisible(true);
    }
}

