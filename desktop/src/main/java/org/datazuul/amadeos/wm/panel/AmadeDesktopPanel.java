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
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.datazuul.amadeos.wm.panel.widget.AbstractDockWidget;
import org.datazuul.amadeos.wm.panel.widget.CalendarDockWidget;
import org.datazuul.amadeos.wm.panel.widget.CdePanelChrome;
import org.datazuul.amadeos.wm.panel.widget.ClockDockWidget;
import org.datazuul.amadeos.wm.panel.widget.DockWidget;
import org.datazuul.amadeos.wm.panel.widget.DrawerDockWidget;
import org.datazuul.amadeos.wm.panel.widget.DrawerDockWidget.DrawerTool;
import org.datazuul.amadeos.wm.panel.widget.WorkspaceSwitcherDockWidget;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.wm.theme.IconManager;
import org.datazuul.amadeos.wm.theme.ThemeManager;
import org.datazuul.amadeos.wm.xdg.XdgApplicationManager;
import org.datazuul.amadeos.wm.xdg.XdgCategory;
import org.datazuul.amadeos.wm.xdg.XdgDesktopEntry;

public final class AmadeDesktopPanel extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(AmadeDesktopPanel.class.getName());

    public static final int DOCK_HEIGHT = 85;

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
    private final WorkspaceSwitcherDockWidget switcher;
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

        // Outer CDE frame: gray chiseled frame with side grip handles (pixel-exact to original CDE)
        rootPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                CdePanelChrome.paintFrame(g, getWidth(), getHeight(), ThemeManager.currentTheme());
            }

            @Override
            public void paint(Graphics g) {
                super.paint(g);
                // Left/right edges of the raised button plate (below the drawer tab row)
                DesktopTheme t = ThemeManager.currentTheme();
                int top = CdePanelChrome.FRAME_INSET_V + CdePanelChrome.TAB_HEIGHT;
                int bottom = getHeight() - CdePanelChrome.FRAME_INSET_V - 1;
                int lx = CdePanelChrome.FRAME_INSET_H;
                int rx = getWidth() - CdePanelChrome.FRAME_INSET_H - 1;
                g.setColor(t.highlight());
                g.drawLine(lx, top, lx, bottom - 1);
                g.setColor(t.darkShadow());
                g.drawLine(rx, top, rx, bottom);
            }
        };
        rootPanel.setBackground(initialTheme.base());
        rootPanel.setBorder(BorderFactory.createEmptyBorder(
                CdePanelChrome.FRAME_INSET_V, CdePanelChrome.FRAME_INSET_H,
                CdePanelChrome.FRAME_INSET_V, CdePanelChrome.FRAME_INSET_H));
        setContentPane(rootPanel);

        // 1. LEFT SLOTS: Clock, File Manager, Text Editor, Terminal
        // 1. LEFT SLOTS (Slots 1-6): Clock, Calendar, File Manager, Text Editor, Mail, Multimedia
        leftSlots = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftSlots.setBackground(initialTheme.base());

        // Slot 1: Analog/Digital Clock (flat tab, no drawer)
        ClockDockWidget clockWidget = new ClockDockWidget();
        widgets.add(clockWidget);
        leftSlots.add(clockWidget.component());

        // Slot 2: Tear-off Paper Calendar (flat tab, no drawer)
        CalendarDockWidget calendarWidget = new CalendarDockWidget();
        widgets.add(calendarWidget);
        leftSlots.add(calendarWidget.component());

        // Slot 3: File Manager (with drawer ▲)
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

        // Slot 4: Text Editor (with drawer ▲)
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

        // Slot 5: Mail / Mailer (with drawer ▲)
        DrawerDockWidget mailDrawer = createCategoryDrawer(
                "mail", "Electronic Mail", "mail",
                XdgCategory.MAIL,
                e -> launchProcess("xterm", "-title", "Mail", "-e", "sh", "-c", "mailx 2>/dev/null || mutt 2>/dev/null || mail 2>/dev/null || (echo 'No terminal mail client installed. (Install thunderbird, mutt, or s-nail)'; exec sh)"),
                List.of(
                        new DrawerTool("Read Mail", "mail", e -> launchProcess("xterm", "-title", "Mail", "-e", "sh", "-c", "mailx 2>/dev/null || mutt 2>/dev/null || mail 2>/dev/null || (echo 'No terminal mail client installed.'; exec sh)")),
                        new DrawerTool("Terminal (xterm)", "terminal", e -> launchProcess("xterm")),
                        new DrawerTool("Root Console (sudo)", "terminal", e -> launchProcess("xterm", "-title", "Root Shell", "-e", "sudo", "sh"))
                ),
                true
        );
        widgets.add(mailDrawer);
        leftSlots.add(mailDrawer.component());

        // Slot 6: Multimedia (with drawer ▲), filled from AudioVideo desktop entries
        DrawerDockWidget multimediaDrawer = createCategoryDrawer(
                "multimedia", "Sound & Video", "multimedia",
                XdgCategory.AUDIO_VIDEO,
                e -> showApplicationManagerDialog(this),
                List.of(),
                true
        );
        widgets.add(multimediaDrawer);
        leftSlots.add(multimediaDrawer.component());

        rootPanel.add(leftSlots, BorderLayout.WEST);

        // 2. CENTER: Iconic CDE 4-way Workspace Switcher Pad
        this.switcher = new WorkspaceSwitcherDockWidget(
                wsNum -> helper.setCurrentDesktop(wsNum - 1),
                () -> org.datazuul.amadeos.wm.ui.SessionDialogHelper.showSessionPowerDialog(this)
        );
        int initialDesktop = helper.getCurrentDesktop();
        if (initialDesktop >= 0 && initialDesktop < 4) {
            switcher.setActiveWorkspace(initialDesktop + 1);
        }
        widgets.add(switcher);
        rootPanel.add(switcher.component(), BorderLayout.CENTER);

        // 3. RIGHT SLOTS (Slots 7-11): Printer, Style Manager, Application Manager, Help Viewer, Trash
        rightSlots = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightSlots.setBackground(initialTheme.base());

        // Slot 7: Printer Manager (replaces Memory widget, with drawer ▲)
        DrawerDockWidget printerDrawer = createCategoryDrawer(
                "printer", "Printer Manager", "printer",
                XdgCategory.PRINTING,
                e -> launchProcess("xterm", "-title", "Printer Queue", "-e", "sh", "-c", "lpstat -p -d 2>/dev/null || echo 'No CUPS printers configured.'; echo ''; lpq 2>/dev/null; exec sh"),
                List.of(
                        new DrawerTool("Print Queue & Status", "printer", e -> launchProcess("xterm", "-title", "Printer Queue", "-e", "sh", "-c", "lpq; lpstat -o; exec sh")),
                        new DrawerTool("CUPS Web Administration", "window", e -> launchProcess("xdg-open", "http://localhost:631/")),
                        new DrawerTool("Printer Settings", "printer", e -> launchProcess("system-config-printer"))
                ),
                true
        );
        widgets.add(printerDrawer);
        rightSlots.add(printerDrawer.component());

        // Slot 8: Style Manager (flat notch tab, no drawer ▲)
        DrawerDockWidget styleWidget = new DrawerDockWidget(
                "style", "Style Manager", "style",
                e -> org.datazuul.amadeos.style.StyleManager.open(),
                List.of(),
                false
        );
        widgets.add(styleWidget);
        rightSlots.add(styleWidget.component());

        // Slot 9: Application Manager (flat notch tab, no drawer ▲)
        DrawerDockWidget appManagerWidget = new DrawerDockWidget(
                "app-manager", "Application Manager", "app-manager",
                e -> showApplicationManagerDialog(this),
                List.of(),
                false
        );
        widgets.add(appManagerWidget);
        rightSlots.add(appManagerWidget.component());

        // Slot 10: Help Viewer (with drawer ▲)
        DrawerDockWidget helpDrawer = createCategoryDrawer(
                "help", "Help & Information Viewer", "help",
                XdgCategory.HELP,
                e -> showHelpDialog(this),
                List.of(
                        new DrawerTool("amadeOS Desktop Help", "help", e -> showHelpDialog(this)),
                        new DrawerTool("Front Panel Overview", "help", e -> showHelpDialog(this)),
                        new DrawerTool("Session Diagnostics & Logs", "diagnostics", e -> showLogViewerDialog(this)),
                        new DrawerTool("Hardware & System Info", "terminal", e -> launchProcess("xterm", "-e", "sh", "-c", "uname -a; free -h; lscpu | head -n 15; exec sh"))
                ),
                true
        );
        widgets.add(helpDrawer);
        rightSlots.add(helpDrawer.component());

        // Slot 11: Trash Can (flat notch tab, no drawer ▲)
        DrawerDockWidget trashWidget = new DrawerDockWidget(
                "trash", "Trash Can", "trash",
                e -> launchProcess("xterm", "-title", "Trash Can", "-e", "sh", "-c", "echo 'amadeOS Trash Can:'; ls -la ~/.local/share/Trash/files 2>/dev/null || echo '(Empty)'; exec sh"),
                List.of(),
                false
        );
        widgets.add(trashWidget);
        rightSlots.add(trashWidget.component());

        rootPanel.add(rightSlots, BorderLayout.EAST);

        // Start all widgets
        for (DockWidget w : widgets) {
            w.start();
        }

        // Listen for internal ThemeManager changes
        ThemeManager.addListener(this::applyTheme);

        // Position dock centered at bottom (or custom position for testing)
        int screenW = helper.screenWidth();
        int screenH = helper.screenHeight();
        // Width follows the slots: adding or removing a widget resizes the dock
        int dockWidth = Math.min(rootPanel.getPreferredSize().width, screenW);
        int dockX = Math.max(0, (screenW - dockWidth) / 2);
        int dockY = screenH - DOCK_HEIGHT;

        if (Boolean.getBoolean("amadeos.dock.center")) {
            dockY = Math.max(0, (screenH - DOCK_HEIGHT) / 2);
        } else {
            String customY = System.getProperty("amadeos.dock.y");
            if (customY != null) {
                try {
                    dockY = Integer.parseInt(customY);
                } catch (Exception ignored) {}
            }
        }
        setBounds(dockX, dockY, dockWidth, DOCK_HEIGHT);

        // Press Escape to exit standalone panel
        rootPanel.registerKeyboardAction(
                e -> System.exit(0),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Check for theme and workspace updates on root window
        pollTimer = new Timer(500, e -> {
            String remoteThemeId = helper.getDesktopTheme();
            if (remoteThemeId != null && !remoteThemeId.isBlank()
                    && !remoteThemeId.equals(ThemeManager.currentTheme().id())) {
                ThemeManager.applyTheme(remoteThemeId);
            }
            int remoteDesktop = helper.getCurrentDesktop();
            if (remoteDesktop >= 0 && remoteDesktop < 4) {
                switcher.setActiveWorkspace(remoteDesktop + 1);
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
        return createCategoryDrawer(id, title, iconKey, category, fallbackPrimaryAction, defaultTools, true);
    }

    private DrawerDockWidget createCategoryDrawer(
            String id,
            String title,
            String iconKey,
            XdgCategory category,
            java.awt.event.ActionListener fallbackPrimaryAction,
            List<DrawerTool> defaultTools,
            boolean hasDrawer) {

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

        return new DrawerDockWidget(id, title, iconKey, primaryAction, tools, hasDrawer);
    }

    private void launchProcess(String... cmd) {
        try {
            org.datazuul.amadeos.wm.process.ProcessLauncher.start(java.util.List.of(cmd));
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

    private void showApplicationManagerDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "amadeOS Application Manager", false);
        dialog.setLayout(new BorderLayout(8, 8));
        DesktopTheme t = ThemeManager.currentTheme();
        dialog.getContentPane().setBackground(t.base());

        JPanel header = new JPanel(new GridLayout(2, 1, 2, 2));
        header.setBackground(t.base());
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
        JLabel title = new JLabel("amadeOS Application Manager", JLabel.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14.0f));
        title.setForeground(t.textActive());
        JLabel subtitle = new JLabel("Registered applications and desktop tools:", JLabel.LEFT);
        subtitle.setForeground(t.textActive());
        header.add(title);
        header.add(subtitle);
        dialog.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(t.base());

        for (XdgCategory cat : XdgCategory.values()) {
            List<XdgDesktopEntry> catApps = XdgApplicationManager.getInstance().getApplicationsByCategory(cat);
            if (catApps.isEmpty() && cat != XdgCategory.OTHER) continue;

            JPanel listPanel = new JPanel(new GridLayout(0, 1, 4, 4));
            listPanel.setBackground(t.base());
            listPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            for (XdgDesktopEntry app : catApps) {
                JPanel item = new JPanel(new BorderLayout(8, 0));
                item.setBackground(t.base());
                item.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, t.shadow()),
                        BorderFactory.createEmptyBorder(4, 4, 4, 4)
                ));

                JLabel iconLbl = new JLabel(IconManager.getIcon(app.icon(), 24));
                JLabel textLbl = new JLabel("<html><b>" + app.name() + "</b>"
                        + (app.comment() != null && !app.comment().isBlank() ? "<br><font color='#707070'>" + app.comment() + "</font>" : "")
                        + "</html>");
                textLbl.setForeground(t.textActive());

                JButton launchBtn = new JButton("Launch");
                launchBtn.addActionListener(e -> {
                    try {
                        app.launch();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog, "Failed to launch " + app.name() + ": " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });

                item.add(iconLbl, BorderLayout.WEST);
                item.add(textLbl, BorderLayout.CENTER);
                item.add(launchBtn, BorderLayout.EAST);
                listPanel.add(item);
            }

            JScrollPane scroll = new JScrollPane(listPanel);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            tabs.addTab(cat.displayName(), scroll);
        }

        dialog.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(t.base());
        footer.setBorder(BorderFactory.createEmptyBorder(4, 14, 10, 14));

        JButton reloadBtn = new JButton("Reload Cache");
        reloadBtn.addActionListener(e -> {
            XdgApplicationManager.getInstance().reload();
            dialog.dispose();
            showApplicationManagerDialog(parent);
        });
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());

        footer.add(reloadBtn);
        footer.add(closeBtn);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.setPreferredSize(new Dimension(600, 450));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void showHelpDialog(JFrame parent) {
        JDialog dialog = new JDialog(parent, "amadeOS Front Panel & Desktop Help", false);
        dialog.setLayout(new BorderLayout(8, 8));
        DesktopTheme t = ThemeManager.currentTheme();
        dialog.getContentPane().setBackground(t.base());

        JPanel header = new JPanel(new GridLayout(2, 1, 2, 2));
        header.setBackground(t.base());
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
        JLabel title = new JLabel("amadeOS CDE Front Panel Guide", JLabel.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14.0f));
        title.setForeground(t.textActive());
        JLabel subtitle = new JLabel("Common Desktop Environment controls and features:", JLabel.LEFT);
        subtitle.setForeground(t.textActive());
        header.add(title);
        header.add(subtitle);
        dialog.add(header, BorderLayout.NORTH);

        JEditorPane helpPane = new JEditorPane();
        helpPane.setContentType("text/html");
        helpPane.setEditable(false);
        helpPane.setText("""
                <html>
                <body style='font-family: sans-serif; font-size: 11pt; padding: 10px;'>
                <h3>Front Panel Overview (11 Slots):</h3>
                <ul>
                  <li><b>Slot 1: Clock</b> &mdash; Analog clock face displaying system time.</li>
                  <li><b>Slot 2: Calendar</b> &mdash; Daily tear-off calendar sheet.</li>
                  <li><b>Slot 3: File Manager</b> &mdash; Home folder navigation and file drawer.</li>
                  <li><b>Slot 4: Text Editor</b> &mdash; Document notepad and text editor tools.</li>
                  <li><b>Slot 5: Mail</b> &mdash; Electronic mail client and console tools.</li>
                  <li><b>Slot 6: Sound &amp; Video</b> &mdash; Audio and video players from the AudioVideo category.</li>
                  <li><b>Center: Workspace Switcher</b> &mdash; Workspaces One..Four, screen lock, and session exit.</li>
                  <li><b>Slot 7: Printer Manager</b> &mdash; CUPS print jobs and printer queue.</li>
                  <li><b>Slot 8: Style Manager</b> &mdash; Desktop color themes, icon themes, and wallpaper.</li>
                  <li><b>Slot 9: Application Manager</b> &mdash; Categorized desktop application catalog.</li>
                  <li><b>Slot 10: Help Viewer</b> &mdash; System documentation and diagnostics.</li>
                  <li><b>Slot 11: Trash Can</b> &mdash; Wastebasket storage and session disposal.</li>
                </ul>
                <p>Click the <b>▲</b> arrow above drawer slots to open the subpanel tools.</p>
                </body>
                </html>
                """);
        helpPane.setCaretPosition(0);
        dialog.add(new JScrollPane(helpPane), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(t.base());
        footer.setBorder(BorderFactory.createEmptyBorder(4, 14, 10, 14));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        footer.add(closeBtn);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.setPreferredSize(new Dimension(540, 400));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
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
