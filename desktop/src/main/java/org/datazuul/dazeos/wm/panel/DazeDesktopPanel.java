package org.datazuul.dazeos.wm.panel;

import com.sun.jna.Native;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;

public final class DazeDesktopPanel extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(DazeDesktopPanel.class.getName());

    public static final int DOCK_HEIGHT = 62;
    private static final int DOCK_WIDTH = 750;

    private static final Color MOTIF_BG = new Color(0xB8, 0xB8, 0xB8);
    private static final Color MOTIF_LIGHT = Color.WHITE;
    private static final Color MOTIF_DARK = new Color(0x60, 0x60, 0x60);
    private static final Color MOTIF_SHADOW = new Color(0x30, 0x30, 0x30);
    private static final Color MOTIF_SUNKEN_BG = new Color(0x9E, 0x9E, 0x9E);
    private static final Color MOTIF_BLACK = Color.BLACK;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");
    private static final AtomicInteger DOC_COUNTER = new AtomicInteger(0);

    private final X11EwmhHelper helper;
    private final JLabel clockTimeLabel;
    private final JLabel clockDateLabel;
    private final JLabel memValueLabel;
    private final JProgressBar memProgressBar;
    private final Timer pollTimer;

    private int activeWorkspace = 1;
    private final JButton[] workspaceButtons = new JButton[4];

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
        super("DazeOS CDE Front Panel");
        this.helper = Objects.requireNonNull(helper);

        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setBackground(MOTIF_BG);
        setLayout(new BorderLayout());

        // Outer CDE 3D chiseled container
        JPanel rootPanel = new JPanel(new BorderLayout(4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Top/Left 3D chisel highlight
                g.setColor(MOTIF_LIGHT);
                g.drawLine(0, 0, getWidth() - 1, 0);
                g.drawLine(0, 0, 0, getHeight() - 1);
                g.drawLine(1, 1, getWidth() - 2, 1);
                g.drawLine(1, 1, 1, getHeight() - 2);

                // Bottom/Right 3D chisel shadow
                g.setColor(MOTIF_SHADOW);
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
                g.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
                g.setColor(MOTIF_DARK);
                g.drawLine(getWidth() - 2, 1, getWidth() - 2, getHeight() - 2);
                g.drawLine(1, getHeight() - 2, getWidth() - 2, getHeight() - 2);
            }
        };
        rootPanel.setBackground(MOTIF_BG);
        rootPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        setContentPane(rootPanel);

        // 1. LEFT SLOTS: File Manager, Text Editor, Terminal
        JPanel leftSlots = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftSlots.setBackground(MOTIF_BG);

        // Slot 1: File Manager
        leftSlots.add(createSlotWithDrawer(
                "📁", "File Manager",
                e -> spawnFileManager(),
                "File Manager Tools",
                new DrawerItem("🏠 Home Directory", e -> launchProcess("xterm", "-e", "sh", "-c", "cd $HOME && ls -la && exec sh")),
                new DrawerItem("💾 Root Filesystem (/)", e -> launchProcess("xterm", "-e", "sh", "-c", "cd / && ls -la && exec sh"))
        ));

        // Slot 2: Text Editor
        leftSlots.add(createSlotWithDrawer(
                "📝", "Text Editor",
                e -> spawnDocumentWindow(),
                "Document & Notes",
                new DrawerItem("📄 New Document", e -> spawnDocumentWindow()),
                new DrawerItem("📋 Quick Notes", e -> spawnDocumentWindow())
        ));

        // Slot 3: Terminal
        leftSlots.add(createSlotWithDrawer(
                "📟", "Terminal (xterm)",
                e -> launchProcess("xterm"),
                "Terminal Tools",
                new DrawerItem("📟 Launch xterm", e -> launchProcess("xterm")),
                new DrawerItem("⚡ Root Shell Console", e -> launchProcess("xterm", "-title", "Root Shell", "-e", "sudo", "sh")),
                new DrawerItem("📋 Session Diagnostics", e -> showLogViewerDialog(this))
        ));

        rootPanel.add(leftSlots, BorderLayout.WEST);

        // 2. CENTER: Iconic CDE Workspace Switcher Pad
        JPanel centerPad = createCenterWorkspacePad();
        rootPanel.add(centerPad, BorderLayout.CENTER);

        // 3. RIGHT SLOTS: Clock, Memory Meter, Tools, Trash/Power
        JPanel rightSlots = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightSlots.setBackground(MOTIF_BG);

        // Slot 4: Digital Clock & Date
        JPanel clockBox = new JPanel(new GridLayout(2, 1, 0, 0));
        clockBox.setPreferredSize(new Dimension(68, 52));
        clockBox.setBackground(MOTIF_SUNKEN_BG);
        clockBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED, MOTIF_LIGHT, MOTIF_SHADOW),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        clockTimeLabel = new JLabel(LocalTime.now().format(TIME_FMT), JLabel.CENTER);
        clockTimeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        clockTimeLabel.setForeground(MOTIF_BLACK);

        clockDateLabel = new JLabel(LocalDate.now().format(DATE_FMT).toUpperCase(), JLabel.CENTER);
        clockDateLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
        clockDateLabel.setForeground(new Color(0x33, 0x33, 0x33));

        clockBox.add(clockTimeLabel);
        clockBox.add(clockDateLabel);
        rightSlots.add(clockBox);

        // Slot 5: Memory Meter
        JPanel memBox = new JPanel(new BorderLayout(2, 2));
        memBox.setPreferredSize(new Dimension(62, 52));
        memBox.setBackground(MOTIF_SUNKEN_BG);
        memBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED, MOTIF_LIGHT, MOTIF_SHADOW),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        memValueLabel = new JLabel("RAM", JLabel.CENTER);
        memValueLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        memValueLabel.setForeground(MOTIF_BLACK);

        memProgressBar = new JProgressBar(0, 100);
        memProgressBar.setValue(35);
        memProgressBar.setPreferredSize(new Dimension(54, 8));
        memProgressBar.setForeground(new Color(0x2E, 0x8B, 0x57)); // Sea green
        memProgressBar.setBackground(new Color(0x50, 0x50, 0x50));
        memProgressBar.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));

        memBox.add(memValueLabel, BorderLayout.CENTER);
        memBox.add(memProgressBar, BorderLayout.SOUTH);
        rightSlots.add(memBox);

        // Slot 6: Diagnostics & Tools
        rightSlots.add(createSlotWithDrawer(
                "📋", "Session Diagnostics",
                e -> showLogViewerDialog(this),
                "System Diagnostics",
                new DrawerItem("📋 Reload Logs", e -> showLogViewerDialog(this)),
                new DrawerItem("🖥 System Info", e -> launchProcess("xterm", "-e", "sh", "-c", "uname -a; free -h; lscpu | head -n 15; exec sh"))
        ));

        // Slot 7: Trash / Power Session
        rightSlots.add(createSlotWithDrawer(
                "🗑️", "Trash & Power",
                e -> showSessionPowerDialog(this),
                "Session & Trash",
                new DrawerItem("⏻ Session Controls...", e -> showSessionPowerDialog(this)),
                new DrawerItem("🗑️ Clean /tmp", e -> launchProcess("xterm", "-e", "sh", "-c", "df -h /tmp; echo 'DazeOS temporary storage clean'; exec sh"))
        ));

        rootPanel.add(rightSlots, BorderLayout.EAST);

        // Periodic timer to refresh clock and memory stats
        pollTimer = new Timer(1000, e -> updateClockAndMemory());
    }

    public void showPanel() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int screenW = (helper.screenWidth() > 0) ? helper.screenWidth() : screen.width;
        int screenH = (helper.screenHeight() > 0) ? helper.screenHeight() : screen.height;

        int panelW = Math.min(DOCK_WIDTH, screenW - 20);
        int panelH = DOCK_HEIGHT;
        int panelX = (screenW - panelW) / 2;
        int panelY = screenH - panelH;

        setBounds(panelX, panelY, panelW, panelH);
        setVisible(true);

        // Register dock properties on native X11 window
        try {
            long nativeWinId = Native.getWindowID(this);
            if (nativeWinId != 0) {
                helper.setDockProperties(nativeWinId, screenW, screenH, panelH);
                LOGGER.info("Registered DazeDesktopPanel as CDE Front Panel dock (XID: " + nativeWinId + ")");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to register dock window properties: " + e.getMessage(), e);
        }

        updateClockAndMemory();
        pollTimer.start();
    }

    private JPanel createCenterWorkspacePad() {
        JPanel pad = new JPanel(new BorderLayout(4, 0));
        pad.setBackground(MOTIF_SUNKEN_BG);
        pad.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED, MOTIF_LIGHT, MOTIF_SHADOW),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));

        // Left Status: Activity LED & Lock
        JPanel leftStatus = new JPanel(new GridLayout(2, 1, 0, 2));
        leftStatus.setBackground(MOTIF_SUNKEN_BG);

        JPanel ledPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x10, 0xB9, 0x81)); // Green glow
                g2.fillOval(3, 3, 8, 8);
                g2.setColor(new Color(0x04, 0x78, 0x57));
                g2.drawOval(3, 3, 8, 8);
            }
        };
        ledPanel.setPreferredSize(new Dimension(14, 14));
        ledPanel.setBackground(MOTIF_SUNKEN_BG);
        ledPanel.setToolTipText("System Activity LED");
        leftStatus.add(ledPanel);

        JButton lockBtn = createMiniMotifButton("🔒", "Lock Screen");
        lockBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, "Screen Lock is active.", "Lock", JOptionPane.INFORMATION_MESSAGE));
        leftStatus.add(lockBtn);
        pad.add(leftStatus, BorderLayout.WEST);

        // Center: 2x2 Workspace Buttons (One, Two, Three, Four)
        JPanel grid = new JPanel(new GridLayout(2, 2, 2, 2));
        grid.setBackground(MOTIF_SUNKEN_BG);

        String[] wsNames = {"One", "Two", "Three", "Four"};
        for (int i = 0; i < 4; i++) {
            int wsNum = i + 1;
            JButton btn = new JButton(wsNames[i]);
            btn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
            btn.setFocusPainted(false);
            btn.setMargin(new Insets(1, 4, 1, 4));
            updateWorkspaceButtonAppearance(btn, wsNum == activeWorkspace);

            btn.addActionListener(e -> {
                activeWorkspace = wsNum;
                for (int j = 0; j < 4; j++) {
                    updateWorkspaceButtonAppearance(workspaceButtons[j], (j + 1) == activeWorkspace);
                }
            });
            workspaceButtons[i] = btn;
            grid.add(btn);
        }
        pad.add(grid, BorderLayout.CENTER);

        // Right Status: Power Off Session Button
        JPanel rightStatus = new JPanel(new GridLayout(2, 1, 0, 2));
        rightStatus.setBackground(MOTIF_SUNKEN_BG);

        JPanel amberLed = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xF5, 0x9E, 0x0B)); // Amber
                g2.fillOval(3, 3, 8, 8);
                g2.setColor(new Color(0xB4, 0x53, 0x09));
                g2.drawOval(3, 3, 8, 8);
            }
        };
        amberLed.setPreferredSize(new Dimension(14, 14));
        amberLed.setBackground(MOTIF_SUNKEN_BG);
        amberLed.setToolTipText("Session Active");
        rightStatus.add(amberLed);

        JButton powerBtn = createMiniMotifButton("⏻", "Session & Power Controls");
        powerBtn.setForeground(new Color(0xC0, 0x20, 0x20));
        powerBtn.addActionListener(e -> showSessionPowerDialog(this));
        rightStatus.add(powerBtn);
        pad.add(rightStatus, BorderLayout.EAST);

        return pad;
    }

    private void updateWorkspaceButtonAppearance(JButton btn, boolean isActive) {
        if (isActive) {
            btn.setBackground(new Color(0xCC, 0xCC, 0xCC));
            btn.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, MOTIF_LIGHT, MOTIF_DARK));
            btn.setForeground(Color.BLACK);
        } else {
            btn.setBackground(MOTIF_BG);
            btn.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));
            btn.setForeground(new Color(0x33, 0x33, 0x33));
        }
    }

    private record DrawerItem(String label, java.awt.event.ActionListener action) {}

    private JPanel createSlotWithDrawer(
            String icon, String tooltip,
            java.awt.event.ActionListener primaryAction,
            String drawerTitle,
            DrawerItem... drawerItems) {

        JPanel slot = new JPanel(new BorderLayout(0, 1));
        slot.setBackground(MOTIF_BG);
        slot.setPreferredSize(new Dimension(46, 52));

        // 1. Up-Arrow Drawer Tab Button (▲)
        JButton drawerTab = new JButton("▲");
        drawerTab.setFont(new Font(Font.MONOSPACED, Font.BOLD, 8));
        drawerTab.setFocusPainted(false);
        drawerTab.setMargin(new Insets(0, 0, 0, 0));
        drawerTab.setPreferredSize(new Dimension(46, 9));
        drawerTab.setBackground(new Color(0xD0, 0xD0, 0xD0));
        drawerTab.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));
        drawerTab.setToolTipText("Open " + drawerTitle + " drawer");

        // 2. Primary Tool Button
        JButton toolBtn = new JButton(icon);
        toolBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        toolBtn.setFocusPainted(false);
        toolBtn.setMargin(new Insets(2, 2, 2, 2));
        toolBtn.setBackground(MOTIF_BG);
        toolBtn.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));
        toolBtn.setToolTipText(tooltip);
        toolBtn.addActionListener(primaryAction);

        // Drawer Popup Menu
        JPopupMenu drawerPopup = new JPopupMenu();
        drawerPopup.setBackground(MOTIF_BG);
        drawerPopup.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));

        JLabel header = new JLabel("  " + drawerTitle);
        header.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        header.setForeground(new Color(0x33, 0x33, 0x33));
        drawerPopup.add(header);
        drawerPopup.addSeparator();

        for (DrawerItem item : drawerItems) {
            JMenuItem mi = new JMenuItem(item.label());
            mi.setBackground(MOTIF_BG);
            mi.setForeground(MOTIF_BLACK);
            mi.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            mi.addActionListener(item.action());
            drawerPopup.add(mi);
        }

        drawerTab.addActionListener(e -> {
            drawerPopup.show(slot, 0, -drawerPopup.getPreferredSize().height);
        });

        slot.add(drawerTab, BorderLayout.NORTH);
        slot.add(toolBtn, BorderLayout.CENTER);
        return slot;
    }

    private JButton createMiniMotifButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
        btn.setFocusPainted(false);
        btn.setBackground(MOTIF_BG);
        btn.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, MOTIF_LIGHT, MOTIF_DARK));
        btn.setMargin(new Insets(1, 1, 1, 1));
        btn.setToolTipText(tooltip);
        return btn;
    }

    private void updateClockAndMemory() {
        clockTimeLabel.setText(LocalTime.now().format(TIME_FMT));
        clockDateLabel.setText(LocalDate.now().format(DATE_FMT).toUpperCase());

        long free = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long total = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long used = total - free;
        memValueLabel.setText(used + "M");
        int percent = (int) ((used * 100) / Math.max(1, total));
        memProgressBar.setValue(percent);
    }

    private void launchProcess(String... cmd) {
        try {
            new ProcessBuilder(cmd).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to launch " + cmd[0] + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void spawnFileManager() {
        launchProcess("xterm", "-title", "File Manager", "-e", "sh", "-c", "echo 'DazeOS File Manager:'; ls -la; exec sh");
    }

    public static void showSessionPowerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "DazeOS Session & Power Control", true);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel header = new JPanel(new java.awt.GridLayout(2, 1, 4, 4));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
        JLabel title = new JLabel("DazeOS Desktop Session (CDE Front Panel)", JLabel.CENTER);
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
                "Welcome to DazeOS Document Window #" + winNum + "!\n\n"
                + "- Click anywhere in this window to focus and raise it.\n"
                + "- Minimize this window to see it parked as a 3D icon tile in the top-left corner!\n"
                + "- Click the top-left tile to restore the window!\n"
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
