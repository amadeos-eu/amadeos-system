package org.datazuul.dazeos.wm.panel;

import com.sun.jna.Native;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;

public final class DazeDesktopPanel extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(DazeDesktopPanel.class.getName());

    public static final int DOCK_HEIGHT = 36;
    private static final Color MOTIF_BG = new Color(0xC0, 0xC0, 0xC0);
    private static final Color MOTIF_LIGHT = Color.WHITE;
    private static final Color MOTIF_DARK = new Color(0x80, 0x80, 0x80);
    private static final Color MOTIF_BLACK = Color.BLACK;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final AtomicInteger DOC_COUNTER = new AtomicInteger(0);

    private final X11EwmhHelper helper;
    private final JPanel taskbarPanel;
    private final JLabel clockLabel;
    private final JLabel memLabel;
    private final Timer pollTimer;

    private List<Long> lastClientList = new ArrayList<>();
    private long lastActiveWindow = -1;
    private final List<WindowStateSnapshot> lastSnapshots = new ArrayList<>();

    private record WindowStateSnapshot(long id, String title, boolean minimized, boolean active) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String display = System.getenv("DISPLAY");
                X11EwmhHelper helper = X11EwmhHelper.open(display);
                DazeDesktopPanel panel = new DazeDesktopPanel(helper);
                panel.showPanel();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to launch DazeDesktopPanel", e);
                System.err.println("Failed to start DazeDesktopPanel: " + e.getMessage());
            }
        });
    }

    public DazeDesktopPanel(X11EwmhHelper helper) {
        super("DazeOS Desktop Panel");
        this.helper = Objects.requireNonNull(helper);

        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setBackground(MOTIF_BG);
        setLayout(new BorderLayout());

        // Custom top border: Motif 3D groove (white highlight, dark shadow)
        JPanel contentPane = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(MOTIF_LIGHT);
                g.drawLine(0, 0, getWidth(), 0);
                g.setColor(MOTIF_DARK);
                g.drawLine(0, 1, getWidth(), 1);
            }
        };
        contentPane.setBackground(MOTIF_BG);
        contentPane.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        setContentPane(contentPane);

        // 1. West: Start Menu button
        JButton startBtn = createMotifButton("🖥 DazeOS");
        startBtn.setFont(startBtn.getFont().deriveFont(Font.BOLD, 12f));
        startBtn.setPreferredSize(new Dimension(100, 28));
        startBtn.addActionListener(e -> showStartMenu(startBtn));
        contentPane.add(startBtn, BorderLayout.WEST);

        // 2. Center: Taskbar button container
        taskbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        taskbarPanel.setBackground(MOTIF_BG);
        contentPane.add(taskbarPanel, BorderLayout.CENTER);

        // 3. East: System Tray (Memory, Clock, Power)
        JPanel trayPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        trayPanel.setBackground(MOTIF_BG);

        memLabel = new JLabel("RAM: --", JLabel.CENTER);
        memLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        memLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED, MOTIF_LIGHT, MOTIF_DARK),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        trayPanel.add(memLabel);

        clockLabel = new JLabel(LocalTime.now().format(TIME_FMT), JLabel.CENTER);
        clockLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        clockLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED, MOTIF_LIGHT, MOTIF_DARK),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        trayPanel.add(clockLabel);

        JButton powerBtn = createMotifButton("⏻");
        powerBtn.setToolTipText("Session & Power Controls");
        powerBtn.setPreferredSize(new Dimension(30, 28));
        powerBtn.addActionListener(e -> showSessionPowerDialog(this));
        trayPanel.add(powerBtn);

        contentPane.add(trayPanel, BorderLayout.EAST);

        // Periodic timer to poll window list and refresh clock
        pollTimer = new Timer(500, e -> updateDesktopState());
    }

    public void showPanel() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int screenW = (helper.screenWidth() > 0) ? helper.screenWidth() : screen.width;
        int screenH = (helper.screenHeight() > 0) ? helper.screenHeight() : screen.height;

        setBounds(0, screenH - DOCK_HEIGHT, screenW, DOCK_HEIGHT);
        setVisible(true);

        // Register dock properties on native X11 window
        try {
            long nativeWinId = Native.getWindowID(this);
            if (nativeWinId != 0) {
                helper.setDockProperties(nativeWinId, screenW, screenH, DOCK_HEIGHT);
                LOGGER.info("Registered DazeDesktopPanel as EWMH dock (XID: " + nativeWinId + ")");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to register dock window properties: " + e.getMessage(), e);
        }

        updateDesktopState();
        pollTimer.start();
    }

    private void updateDesktopState() {
        // 1. Update Clock
        clockLabel.setText(LocalTime.now().format(TIME_FMT));

        // 2. Update Memory
        long free = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long total = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long used = total - free;
        memLabel.setText(used + "M / " + total + "M");
        memLabel.setToolTipText("JVM Memory: " + used + " MB used of " + total + " MB allocated");

        // 3. Query Windows from X11
        try {
            List<Long> clients = helper.getClientList();
            long activeWin = helper.getActiveWindow();

            List<WindowStateSnapshot> snapshots = new ArrayList<>();
            for (long win : clients) {
                String title = helper.getWindowTitle(win);
                boolean minimized = helper.isWindowMinimized(win);
                boolean active = (win == activeWin);
                snapshots.add(new WindowStateSnapshot(win, title, minimized, active));
            }

            // Only rebuild UI if window states changed
            if (!snapshots.equals(lastSnapshots) || !clients.equals(lastClientList) || activeWin != lastActiveWindow) {
                lastSnapshots.clear();
                lastSnapshots.addAll(snapshots);
                lastClientList = clients;
                lastActiveWindow = activeWin;
                rebuildTaskbar(snapshots);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error polling X11 window states: " + e.getMessage(), e);
        }
    }

    private void rebuildTaskbar(List<WindowStateSnapshot> snapshots) {
        taskbarPanel.removeAll();

        for (WindowStateSnapshot s : snapshots) {
            String displayTitle = s.title();
            if (displayTitle.length() > 24) {
                displayTitle = displayTitle.substring(0, 22) + "...";
            }
            if (s.minimized()) {
                displayTitle = "[_ " + displayTitle + "]";
            }

            JButton btn = createMotifButton(displayTitle);
            btn.setPreferredSize(new Dimension(160, 26));

            if (s.active()) {
                btn.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, MOTIF_LIGHT, MOTIF_DARK));
                btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
                btn.setBackground(new Color(0xD8, 0xD8, 0xD8));
            } else if (s.minimized()) {
                btn.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));
                btn.setFont(btn.getFont().deriveFont(Font.ITALIC, 11f));
                btn.setForeground(new Color(0x40, 0x40, 0x40));
            } else {
                btn.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));
                btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
            }

            btn.setToolTipText(s.title() + (s.minimized() ? " (Minimized)" : (s.active() ? " (Active)" : "")));

            btn.addActionListener(e -> {
                if (s.active()) {
                    // Clicking active window minimizes it
                    helper.minimizeWindow(s.id());
                } else {
                    // Clicking inactive or minimized window restores and focuses it
                    helper.activateWindow(s.id());
                }
                updateDesktopState();
            });

            taskbarPanel.add(btn);
        }

        taskbarPanel.revalidate();
        taskbarPanel.repaint();
    }

    private JButton createMotifButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(MOTIF_BG);
        btn.setForeground(MOTIF_BLACK);
        btn.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));
        btn.setMargin(new Insets(2, 6, 2, 6));
        return btn;
    }

    private void showStartMenu(JButton source) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(MOTIF_BG);
        menu.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));

        JMenuItem termItem = new JMenuItem("📟 Launch Terminal (xterm)");
        styleMenuItem(termItem);
        termItem.addActionListener(e -> {
            try {
                new ProcessBuilder("xterm").start();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to launch xterm: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(termItem);

        JMenuItem docItem = new JMenuItem("📝 Open Document Window");
        styleMenuItem(docItem);
        docItem.addActionListener(e -> spawnDocumentWindow());
        menu.add(docItem);

        JMenuItem logItem = new JMenuItem("📋 Session Diagnostics & Logs");
        styleMenuItem(logItem);
        logItem.addActionListener(e -> showLogViewerDialog(this));
        menu.add(logItem);

        menu.addSeparator();

        JMenuItem powerItem = new JMenuItem("⏻ Session & Power Controls...");
        styleMenuItem(powerItem);
        powerItem.setFont(powerItem.getFont().deriveFont(Font.BOLD));
        powerItem.addActionListener(e -> showSessionPowerDialog(this));
        menu.add(powerItem);

        // Show above the start button
        menu.show(source, 0, -menu.getPreferredSize().height);
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(MOTIF_BG);
        item.setForeground(MOTIF_BLACK);
        item.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    public static void showSessionPowerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "DazeOS Session & Power Control", true);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel header = new JPanel(new java.awt.GridLayout(2, 1, 4, 4));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
        JLabel title = new JLabel("DazeOS Desktop Session (M8)", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15.0f));
        JLabel subtitle = new JLabel("Select an action to end or control your session:", JLabel.CENTER);
        header.add(title);
        header.add(subtitle);
        dialog.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new java.awt.GridLayout(2, 2, 12, 12));
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
            Files.writeString(
                    java.nio.file.Path.of(home, ".dazeos-session-action"),
                    action,
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        System.exit(0);
    }

    private static void showLogViewerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "DazeOS Session Diagnostics & Logs", false);
        dialog.setLayout(new BorderLayout(8, 8));

        String homeDir = System.getProperty("user.home", "/tmp");
        File sessionLog = new File(homeDir, ".dazeos-session.log");
        File wmLog = new File(homeDir, ".dazeos-wm.log");

        JTabbedPane tabbedPane = new JTabbedPane();

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

    private static JTextArea createLogTextArea(File file) {
        JTextArea area = new JTextArea(readLogContent(file));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(area.getDocument().getLength());
        return area;
    }

    private static String readLogContent(File file) {
        if (!file.exists()) {
            return "(Log file " + file.getAbsolutePath() + " does not exist yet.)";
        }
        try {
            return Files.readString(file.toPath());
        } catch (Exception e) {
            return "Error reading " + file.getAbsolutePath() + ": " + e.getMessage();
        }
    }

    private static void spawnDocumentWindow() {
        int winNum = DOC_COUNTER.incrementAndGet();
        JFrame docFrame = new JFrame("Document Window #" + winNum);
        docFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        docFrame.setLayout(new BorderLayout(8, 8));

        JTextArea textArea = new JTextArea(
                "Welcome to DazeOS Window #" + winNum + "!\n\n"
                + "- Click anywhere in this window to focus and raise it.\n"
                + "- You can type text, resize, or open more windows.\n"
                + "- Minimize this window to see it parked on the bottom taskbar!\n"
                + "- Click its taskbar button to restore it!\n"
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

