package org.datazuul.amadeos.wm.panel;

import com.sun.jna.Native;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.datazuul.amadeos.wm.panel.widget.AbstractDockWidget;
import org.datazuul.amadeos.wm.panel.widget.ClockDockWidget;
import org.datazuul.amadeos.wm.panel.widget.DockWidget;
import org.datazuul.amadeos.wm.panel.widget.DrawerDockWidget;
import org.datazuul.amadeos.wm.panel.widget.DrawerDockWidget.DrawerTool;
import org.datazuul.amadeos.wm.panel.widget.MemoryDockWidget;
import org.datazuul.amadeos.wm.panel.widget.WorkspaceSwitcherDockWidget;
import org.datazuul.amadeos.wm.theme.DesktopTheme;
import org.datazuul.amadeos.wm.theme.ThemeManager;
import org.datazuul.amadeos.wm.xdg.XdgApplicationManager;
import org.datazuul.amadeos.wm.xdg.XdgCategory;
import org.datazuul.amadeos.wm.xdg.XdgDesktopEntry;

public final class AmadeDesktopPanel extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(AmadeDesktopPanel.class.getName());

    public static final int DOCK_HEIGHT = 64;
    private static final int DOCK_WIDTH = 720;

    private static final AtomicInteger DOC_COUNTER = new AtomicInteger(0);
    private static java.nio.channels.FileLock panelLock = null;

    private static boolean acquireSingleInstanceLock() {
        try {
            String home = System.getProperty("user.home", "/tmp");
            File lockFile = new File(home, ".amadeos-panel.lock");
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(lockFile, "rw");
            panelLock = raf.getChannel().tryLock();
            return panelLock != null;
        } catch (Exception e) {
            return true;
        }
    }

    private final X11EwmhHelper helper;
    private final List<DockWidget> widgets = new ArrayList<>();
    private final JPanel rootPanel;
    private final JPanel leftSlots;
    private final JPanel rightSlots;
    private final Timer pollTimer;

    public static void main(String[] args) {
        if (!acquireSingleInstanceLock()) {
            LOGGER.warning("Another instance of AmadeDesktopPanel is already running. Exiting.");
            return;
        }

        String displayName = args.length > 0 ? args[0] : null;
        X11EwmhHelper ewmhHelper;
        try {
            ewmhHelper = X11EwmhHelper.open(displayName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to X11 display: " + e.getMessage(), e);
            System.exit(1);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                AmadeDesktopPanel panel = new AmadeDesktopPanel(ewmhHelper);
                panel.addNotify();
                panel.registerAsDock();
                panel.setVisible(true);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error initializing desktop panel: " + ex.getMessage(), ex);
                System.exit(1);
            }
        });
    }

    public AmadeDesktopPanel(X11EwmhHelper helper) {
        super("amadeOS CDE Front Panel");
        this.helper = Objects.requireNonNull(helper);

        DesktopTheme initialTheme = ThemeManager.currentTheme();

        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setBackground(initialTheme.base());
        setLayout(new BorderLayout());

        // Outer CDE 3D chiseled container
        rootPanel = new JPanel(new BorderLayout(4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                DesktopTheme t = ThemeManager.currentTheme();
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Top/Left 3D chisel highlight
                g2.setColor(t.highlight());
                g2.drawLine(0, 0, getWidth() - 1, 0);
                g2.drawLine(0, 0, 0, getHeight() - 1);
                g2.setColor(t.softHighlight());
                g2.drawLine(1, 1, getWidth() - 2, 1);
                g2.drawLine(1, 1, 1, getHeight() - 2);

                // Bottom/Right 3D chisel shadow
                g2.setColor(t.darkShadow());
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
                g2.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
                g2.setColor(t.shadow());
                g2.drawLine(getWidth() - 2, 1, getWidth() - 2, getHeight() - 2);
                g2.drawLine(1, getHeight() - 2, getWidth() - 2, getHeight() - 2);

                // Authentic CDE side grip handles (textured ribs & collapse arrow box)
                int leftX = 4;
                int rightX = getWidth() - 16;
                int topY = 4;
                int botY = getHeight() - 5;

                // Left & Right side groove boxes
                for (int gx : new int[]{leftX, rightX}) {
                    // Small top collapse toggle box
                    g2.setColor(t.darkShadow());
                    g2.drawRect(gx, topY, 11, 9);
                    g2.setColor(t.highlight());
                    g2.drawLine(gx + 1, topY + 1, gx + 10, topY + 1);
                    g2.drawLine(gx + 1, topY + 1, gx + 1, topY + 8);
                    // Minus/collapse dash inside
                    g2.setColor(t.darkShadow());
                    g2.drawLine(gx + 3, topY + 5, gx + 8, topY + 5);

                    // Vertical divider separating the grip handle from the slots
                    int divX = (gx == leftX) ? gx + 13 : gx - 2;
                    g2.setColor(t.darkShadow());
                    g2.drawLine(divX, 2, divX, getHeight() - 3);
                    g2.setColor(t.highlight());
                    g2.drawLine(divX + 1, 2, divX + 1, getHeight() - 3);

                    // Horizontal textured grip ribs
                    for (int ry = topY + 13; ry < botY - 2; ry += 3) {
                        g2.setColor(t.darkShadow());
                        g2.drawLine(gx + 1, ry, gx + 10, ry);
                        g2.setColor(t.highlight());
                        g2.drawLine(gx + 1, ry + 1, gx + 10, ry + 1);
                    }
                }
            }
        };
        rootPanel.setBackground(initialTheme.base());
        rootPanel.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 20));
        setContentPane(rootPanel);

        // 1. LEFT SLOTS: File Manager, Text Editor, Terminal
        leftSlots = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftSlots.setBackground(initialTheme.base());

        DrawerDockWidget fmDrawer = createCategoryDrawer(
                "file-manager", "File Manager", "file-manager",
                XdgCategory.FILE_MANAGER,
                e -> spawnFileManager(),
                List.of(
                        new DrawerTool("Home Directory (~)", "file-manager", e -> launchProcess("xterm", "-e", "sh", "-c", "cd $HOME && ls -la && exec sh")),
                        new DrawerTool("Root Filesystem (/)", "file-manager", e -> launchProcess("xterm", "-e", "sh", "-c", "cd / && ls -la && exec sh"))
                )
        );
        widgets.add(fmDrawer);
        leftSlots.add(fmDrawer.component());

        DrawerDockWidget editorDrawer = createCategoryDrawer(
                "editor", "Text Editor", "text-editor",
                XdgCategory.TEXT_EDITOR,
                e -> spawnDocumentWindow(),
                List.of(
                        new DrawerTool("New Document Window", "text-editor", e -> spawnDocumentWindow()),
                        new DrawerTool("Quick Scratchpad", "text-editor", e -> spawnDocumentWindow())
                )
        );
        widgets.add(editorDrawer);
        leftSlots.add(editorDrawer.component());

        DrawerDockWidget termDrawer = createCategoryDrawer(
                "terminal", "Terminal (xterm)", "terminal",
                XdgCategory.TERMINAL,
                e -> launchProcess("xterm"),
                List.of(
                        new DrawerTool("Launch xterm", "terminal", e -> launchProcess("xterm")),
                        new DrawerTool("Root Console (sudo)", "terminal", e -> launchProcess("xterm", "-title", "Root Shell", "-e", "sudo", "sh")),
                        new DrawerTool("Diagnostics Logs", "diagnostics", e -> showLogViewerDialog(this))
                )
        );
        widgets.add(termDrawer);
        leftSlots.add(termDrawer.component());

        rootPanel.add(leftSlots, BorderLayout.WEST);

        // 2. CENTER: Iconic CDE 4-way Workspace Switcher Pad
        WorkspaceSwitcherDockWidget switcher = new WorkspaceSwitcherDockWidget(
                wsNum -> LOGGER.info(() -> "Switched to workspace: " + wsNum),
                () -> showSessionPowerDialog(this)
        );
        widgets.add(switcher);
        rootPanel.add(switcher.component(), BorderLayout.CENTER);

        // 3. RIGHT SLOTS: Clock, Memory Meter, Tools, Trash/Power
        rightSlots = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightSlots.setBackground(initialTheme.base());

        ClockDockWidget clockWidget = new ClockDockWidget();
        widgets.add(clockWidget);
        rightSlots.add(clockWidget.component());

        MemoryDockWidget memoryWidget = new MemoryDockWidget();
        widgets.add(memoryWidget);
        rightSlots.add(memoryWidget.component());

        DrawerDockWidget diagDrawer = createCategoryDrawer(
                "diagnostics", "Session Diagnostics & System", "diagnostics",
                XdgCategory.MONITOR,
                e -> showLogViewerDialog(this),
                List.of(
                        new DrawerTool("Desktop & Wallpaper Settings...", "window", e -> {
                            new org.datazuul.amadeos.wm.settings.AmadeSettingsApplication().setVisible(true);
                        }),
                        new DrawerTool("View Session Diagnostics", "diagnostics", e -> showLogViewerDialog(this)),
                        new DrawerTool("Hardware & System Info", "terminal", e -> launchProcess("xterm", "-e", "sh", "-c", "uname -a; free -h; lscpu | head -n 15; exec sh")),
                        new DrawerTool("Reload Applications Cache", "window", e -> {
                            XdgApplicationManager.getInstance().reload();
                            JOptionPane.showMessageDialog(this, "Application cache reloaded. " + XdgApplicationManager.getInstance().getAllApplications().size() + " applications found.", "amadeOS Applications", JOptionPane.INFORMATION_MESSAGE);
                        })
                )
        );
        widgets.add(diagDrawer);
        rightSlots.add(diagDrawer.component());

        DrawerDockWidget trashDrawer = new DrawerDockWidget(
                "trash", "Trash & Session", "trash",
                e -> showSessionPowerDialog(this),
                List.of(
                        new DrawerTool("Session & Power Controls...", "power", e -> showSessionPowerDialog(this)),
                        new DrawerTool("Inspect /tmp Storage", "trash", e -> launchProcess("xterm", "-e", "sh", "-c", "df -h /tmp; echo 'amadeOS temporary storage clean'; exec sh"))
                )
        );
        widgets.add(trashDrawer);
        rightSlots.add(trashDrawer.component());

        rootPanel.add(rightSlots, BorderLayout.EAST);

        // Start all widgets
        for (DockWidget w : widgets) {
            w.start();
        }

        // Listen for internal ThemeManager changes
        ThemeManager.addListener(this::applyTheme);

        // Position dock centered at bottom
        int screenW = helper.screenWidth();
        int screenH = helper.screenHeight();
        int dockX = Math.max(0, (screenW - DOCK_WIDTH) / 2);
        int dockY = screenH - DOCK_HEIGHT;
        setBounds(dockX, dockY, DOCK_WIDTH, DOCK_HEIGHT);

        // Check for theme updates on root window
        pollTimer = new Timer(1000, e -> {
            String remoteThemeId = helper.getDesktopTheme();
            if (remoteThemeId != null && !remoteThemeId.isBlank()
                    && !remoteThemeId.equals(ThemeManager.currentTheme().id())) {
                ThemeManager.applyTheme(remoteThemeId);
            }
        });
        pollTimer.start();
    }

    public void applyTheme(DesktopTheme theme) {
        setBackground(theme.base());
        rootPanel.setBackground(theme.base());
        leftSlots.setBackground(theme.base());
        rightSlots.setBackground(theme.base());

        for (DockWidget w : widgets) {
            w.onThemeChanged(theme);
        }
        repaint();
    }

    public void registerAsDock() {
        long winId = getNativeWindowId();
        if (winId == 0) {
            LOGGER.warning("Could not determine native X11 window ID for desktop panel");
            return;
        }

        LOGGER.info(() -> "Registering CDE Front Panel native window: " + winId);
        int screenW = helper.screenWidth();
        int screenH = helper.screenHeight();
        helper.setDockProperties(winId, screenW, screenH, DOCK_HEIGHT);
    }

    private long getNativeWindowId() {
        try {
            java.lang.reflect.Method getPeer = java.awt.Component.class.getDeclaredMethod("getPeer");
            getPeer.setAccessible(true);
            Object peer = getPeer.invoke(this);
            if (peer != null) {
                java.lang.reflect.Method getWindow = peer.getClass().getMethod("getWindow");
                Object winObj = getWindow.invoke(peer);
                if (winObj instanceof Number num) {
                    return num.longValue();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return Native.getWindowID(this);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Native.getWindowID failed: " + e.getMessage());
            return 0;
        }
    }

    private DrawerDockWidget createCategoryDrawer(
            String id,
            String title,
            String iconKey,
            XdgCategory category,
            java.awt.event.ActionListener fallbackPrimaryAction,
            List<DrawerTool> defaultTools) {

        List<XdgDesktopEntry> apps = XdgApplicationManager.getInstance().getApplicationsByCategory(category);
        List<DrawerTool> tools = new ArrayList<>();

        // 1. Add discovered XDG applications for this category
        for (XdgDesktopEntry app : apps) {
            tools.add(new DrawerTool(app.name(), app.icon(), e -> {
                try {
                    app.launch();
                } catch (Exception ex) {
                    LOGGER.warning("Failed to launch XDG app " + app.name() + ": " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Failed to launch " + app.name() + ": " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }));
        }

        // 2. Add default/auxiliary tools
        if (defaultTools != null) {
            tools.addAll(defaultTools);
        }

        // 3. Primary action: if an external XDG application is installed for this category, launch it;
        // otherwise, use the fallback primary action!
        java.awt.event.ActionListener primaryAction;
        if (!apps.isEmpty()) {
            XdgDesktopEntry primaryApp = apps.get(0);
            primaryAction = e -> {
                try {
                    primaryApp.launch();
                } catch (Exception ex) {
                    LOGGER.warning("Failed to launch primary app " + primaryApp.name() + ": " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Failed to launch " + primaryApp.name() + ": " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            };
        } else {
            primaryAction = fallbackPrimaryAction;
        }

        return new DrawerDockWidget(id, title, iconKey, primaryAction, tools);
    }

    private void launchProcess(String... cmd) {
        try {
            new ProcessBuilder(cmd).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to launch " + cmd[0] + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void spawnFileManager() {
        launchProcess("xterm", "-title", "File Manager", "-e", "sh", "-c", "echo 'amadeOS File Manager:'; ls -la; exec sh");
    }

    private void spawnDocumentWindow() {
        int docId = DOC_COUNTER.incrementAndGet();
        JFrame docFrame = new JFrame("Document #" + docId);
        docFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        docFrame.setLayout(new BorderLayout(8, 8));

        DesktopTheme t = ThemeManager.currentTheme();
        docFrame.getContentPane().setBackground(t.base());

        JTextArea area = new JTextArea("amadeOS Document #" + docId + "\n\nType notes here...\n");
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        docFrame.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(t.base());
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> docFrame.dispose());
        btnPanel.add(closeBtn);
        docFrame.add(btnPanel, BorderLayout.SOUTH);

        docFrame.setSize(440, 280);
        docFrame.setLocationByPlatform(true);
        docFrame.setVisible(true);
    }

    private void showSessionPowerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "amadeOS Session & Power Control", true);
        dialog.setLayout(new BorderLayout(12, 12));
        DesktopTheme t = ThemeManager.currentTheme();
        dialog.getContentPane().setBackground(t.base());

        JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
        header.setBackground(t.base());
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
        JLabel title = new JLabel("amadeOS Desktop Session (M8)", JLabel.CENTER);
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
        footer.setBackground(t.base());
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

    private void requestSessionAction(String action) {
        try {
            String home = System.getProperty("user.home", "/tmp");
            Files.writeString(
                    new File(home, ".amadeos-session-action").toPath(),
                    action,
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        System.exit(0);
    }

    private void showLogViewerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "amadeOS Session Diagnostics & Logs", false);
        dialog.setLayout(new BorderLayout(8, 8));
        DesktopTheme t = ThemeManager.currentTheme();
        dialog.getContentPane().setBackground(t.base());

        String homeDir = System.getProperty("user.home", "/tmp");
        File sessionLog = new File(homeDir, ".amadeos-session.log");
        File wmLog = new File(homeDir, ".amadeos-wm.log");

        JTabbedPane tabbedPane = new JTabbedPane();
        JTextArea sessionArea = createLogTextArea(sessionLog);
        tabbedPane.addTab("Session Log (~/.amadeos-session.log)", new JScrollPane(sessionArea));

        JTextArea wmArea = createLogTextArea(wmLog);
        tabbedPane.addTab("WM Daemon Log (~/.amadeos-wm.log)", new JScrollPane(wmArea));

        dialog.add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(t.base());
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

    private JTextArea createLogTextArea(File file) {
        JTextArea area = new JTextArea(readLogContent(file));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(area.getDocument().getLength());
        return area;
    }

    private String readLogContent(File file) {
        if (!file.exists()) {
            return "(Log file " + file.getAbsolutePath() + " does not exist yet.)";
        }
        try {
            return Files.readString(file.toPath());
        } catch (Exception e) {
            return "Error reading " + file.getAbsolutePath() + ": " + e.getMessage();
        }
    }
}
