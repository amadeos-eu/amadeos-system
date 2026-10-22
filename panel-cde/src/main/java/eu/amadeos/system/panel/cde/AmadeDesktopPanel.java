package eu.amadeos.system.panel.cde;

import eu.amadeos.system.base.x11.X11EwmhHelper;

import com.sun.jna.Native;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import eu.amadeos.system.panel.cde.widget.AbstractDockWidget;
import eu.amadeos.system.panel.cde.widget.CalendarDockWidget;
import eu.amadeos.system.panel.cde.widget.CdePanelChrome;
import eu.amadeos.system.panel.cde.widget.ClockDockWidget;
import eu.amadeos.system.panel.cde.widget.DockWidget;
import eu.amadeos.system.panel.cde.widget.DrawerDockWidget;
import eu.amadeos.system.panel.cde.widget.DrawerDockWidget.DrawerTool;
import eu.amadeos.system.panel.cde.widget.SlotSpacerDockWidget;
import eu.amadeos.system.panel.cde.widget.WorkspaceSwitcherDockWidget;
import eu.amadeos.system.base.net.NmcliNetworkService;
import eu.amadeos.system.panel.cde.status.NetworkCell;
import eu.amadeos.system.panel.cde.status.StatusDockWidget;
import eu.amadeos.system.panel.cde.status.UpdatesCell;
import eu.amadeos.system.packages.AptPackageService;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgCategory;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;

public final class AmadeDesktopPanel extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(AmadeDesktopPanel.class.getName());

    public static final int DOCK_HEIGHT = 85;

    /** The desktop help, installed with the image and shown in amade-web. */
    /** The CUPS web interface, where printers are added and configured. */
    private static final String CUPS_ADMIN = "http://localhost:631/admin";
    /** The amadeOS User's Handbook of the package amadeos-handbook, and the same book on the web. */
    private static final String DESKTOP_HELP = "/usr/share/doc/amadeos-handbook/html/index.html";
    private static final String DESKTOP_HELP_ONLINE = "https://datazuul.github.io/amadeos/";
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

    /** The panel's window title; the window manager recognises the panel by it if the dock type is missing. */
    public static final String WINDOW_TITLE = "amadeOS CDE Front Panel";

    private final X11EwmhHelper helper;
    private final List<DockWidget> widgets = new ArrayList<>();
    private final JPanel rootPanel;
    private final JPanel leftSlots;
    private final JPanel rightSlots;
    private final WorkspaceSwitcherDockWidget switcher;
    private final Timer pollTimer;
    /** When the panel last started an application; the window manager's busy flag may lag behind. */
    private long launchedAt;

    // --8<-- [start:main]
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
                // Under a window manager, AWT's XToolkit writes _NET_WM_WINDOW_TYPE_NORMAL while it maps
                // the window; the window manager takes the strut as the dock's sign meanwhile, and the
                // type is put right here for everyone else who reads it.
                panel.registerAsDock();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error initializing desktop panel: " + ex.getMessage(), ex);
                System.exit(1);
            }
        });
    }
    // --8<-- [end:main]

    public AmadeDesktopPanel(X11EwmhHelper helper) {
        super(WINDOW_TITLE);
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

        // 1. LEFT SLOTS (Slots 1-8): Clock, Calendar, File Manager, Text Editor, Mail, Multimedia, Graphics, Games
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
                e -> openInFileManager(System.getProperty("user.home", "/")),
                List.of(
                        new DrawerTool("Home Directory (~)", "file-manager", e -> openInFileManager(System.getProperty("user.home", "/"))),
                        new DrawerTool("Root Filesystem (/)", "file-manager", e -> openInFileManager("/"))
                )
        );
        widgets.add(fmDrawer);
        leftSlots.add(fmDrawer.component());

        // Slot 4: Text & Office (with drawer ▲): the TextEditor and WordProcessor desktop entries, then the
        // other Office ones (calendar, spreadsheet, presentation, ...), as Mail & Internet does with Network
        DrawerDockWidget editorDrawer = createCategoryDrawer(
                "editor", "Text & Office", "text-editor",
                XdgCategory.TEXT_EDITOR,
                e -> launchProcess("amade-pad"),
                xdgTools(XdgCategory.OFFICE, XdgCategory.TEXT_EDITOR)
        );
        widgets.add(editorDrawer);
        leftSlots.add(editorDrawer.component());

        // Slot 5: Mail & Internet (with drawer ▲): the Email desktop entries, then the other
        // Network ones such as the web browser
        DrawerDockWidget mailDrawer = createCategoryDrawer(
                "mail", "Mail & Internet", "mail",
                XdgCategory.MAIL,
                e -> launchProcess("amade-mail"),
                xdgTools(XdgCategory.NETWORK, XdgCategory.MAIL)
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

        // Slot 7: Graphics (with drawer ▲), filled from Graphics desktop entries
        DrawerDockWidget graphicsDrawer = createCategoryDrawer(
                "graphics", "Graphics", "graphics",
                XdgCategory.GRAPHICS,
                e -> showApplicationManagerDialog(this),
                List.of(),
                true
        );
        widgets.add(graphicsDrawer);
        leftSlots.add(graphicsDrawer.component());

        // Slot 9: Games (with drawer ▲), filled from Game desktop entries
        DrawerDockWidget gamesDrawer = createCategoryDrawer(
                "games", "Games", "games",
                XdgCategory.GAMES,
                e -> showApplicationManagerDialog(this),
                List.of(),
                true
        );
        widgets.add(gamesDrawer);
        leftSlots.add(gamesDrawer.component());

        // The narrow empty bay before the workspace switch, as in CDE
        SlotSpacerDockWidget leftSpacer = new SlotSpacerDockWidget("spacer-left");
        widgets.add(leftSpacer);
        leftSlots.add(leftSpacer.component());

        rootPanel.add(leftSlots, BorderLayout.WEST);

        // 2. CENTER: Iconic CDE 4-way Workspace Switcher Pad
        this.switcher = new WorkspaceSwitcherDockWidget(
                wsNum -> helper.setCurrentDesktop(wsNum - 1),
                () -> eu.amadeos.system.base.ui.SessionDialogHelper.showSessionPowerDialog(this)
        );
        int initialDesktop = helper.getCurrentDesktop();
        if (initialDesktop >= 0 && initialDesktop < 4) {
            switcher.setActiveWorkspace(initialDesktop + 1);
        }
        widgets.add(switcher);
        rootPanel.add(switcher.component(), BorderLayout.CENTER);

        // 3. RIGHT SLOTS (Slots 9-13): Printer, Tools, Help, Trash, Status
        rightSlots = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightSlots.setBackground(initialTheme.base());

        // The narrow empty bay after the workspace switch, as in CDE
        SlotSpacerDockWidget rightSpacer = new SlotSpacerDockWidget("spacer-right");
        widgets.add(rightSpacer);
        rightSlots.add(rightSpacer.component());

        // Slot 9: Printers (with drawer ▲): amadeos-print with the printers and their jobs, the other installed
        // Printing applications, and the CUPS web interface for adding and configuring printers
        List<DrawerTool> printerTools = new ArrayList<>(xdgTools(XdgCategory.PRINTING));
        printerTools.add(new DrawerTool("Add or Configure Printers", "printer",
                e -> launchProcess("xdg-open", CUPS_ADMIN)));
        DrawerDockWidget printerDrawer = new DrawerDockWidget(
                "printer", "Print Manager", "printer",
                e -> launchProcess("amadeos-print"),
                printerTools,
                true
        );
        widgets.add(printerDrawer);
        rightSlots.add(printerDrawer.component());

        // Slot 10: Tools (with drawer ▲): desktop settings, application catalog, terminals and diagnostics,
        // like CDE's Tools subpanel; then the other installed Settings and System applications (the
        // installer of the live image, for example), except those the fixed entries already start.
        List<DrawerTool> toolsTools = new ArrayList<>(List.of(
                new DrawerTool("Style Manager", "style", e -> launchProcess("amadeos-style")),
                new DrawerTool("Application Manager", "app-manager", e -> showApplicationManagerDialog(this)),
                new DrawerTool("Terminal (xterm)", "terminal", e -> launchProcess("xterm")),
                new DrawerTool("Root Console (sudo)", "terminal", e -> launchProcess("xterm", "-title", "Root Shell", "-e", "sudo", "sh")),
                new DrawerTool("Session Diagnostics & Logs", "diagnostics", e -> launchProcess("amadeos-logviewer")),
                new DrawerTool("Hardware & System Info", "diagnostics", e -> launchProcess("amadeos-sysinfo"))
        ));
        toolsTools.addAll(xdgTools(List.of(XdgCategory.SETTINGS, XdgCategory.MONITOR),
                Set.of("amadeos-style", "amadeos-logviewer", "amadeos-sysinfo", "xterm")));
        DrawerDockWidget toolsDrawer = new DrawerDockWidget(
                "tools", "Tools", "app-manager",
                e -> showApplicationManagerDialog(this),
                toolsTools,
                true
        );
        widgets.add(toolsDrawer);
        rightSlots.add(toolsDrawer.component());

        // Slot 11: Help (with drawer ▲): the User's Handbook in the web browser, then the Documentation
        // entries (other handbooks such as the Game Developer's Handbook bring their own)
        List<DrawerTool> helpTools = new ArrayList<>();
        helpTools.add(new DrawerTool("amadeOS User's Handbook", "help", e -> openDesktopHelp()));
        helpTools.addAll(xdgTools(XdgCategory.HELP));
        DrawerDockWidget helpDrawer = new DrawerDockWidget(
                "help", "Help & Information Viewer", "help",
                e -> openDesktopHelp(),
                helpTools,
                true
        );
        widgets.add(helpDrawer);
        rightSlots.add(helpDrawer.component());

        // Slot 12: Trash Can (flat notch tab, no drawer ▲): the trash folder in amade-files
        DrawerDockWidget trashWidget = new DrawerDockWidget(
                "trash", "Trash Can", "trash",
                e -> openTrash(),
                List.of(),
                false
        );
        widgets.add(trashWidget);
        rightSlots.add(trashWidget.component());

        // Slot 13: Status (network and software updates now; sound, battery and Bluetooth to follow),
        // outermost on the right. The updates cell shows only while updates are waiting.
        StatusDockWidget statusWidget = new StatusDockWidget(List.of(
                new NetworkCell(new NmcliNetworkService(),
                        () -> launchProcess("xterm", "-title", "Network Settings", "-e", "nmtui")),
                new UpdatesCell(new AptPackageService(), updateWindow("--updates"), updateWindow("--settings"))));
        widgets.add(statusWidget);
        rightSlots.add(statusWidget.component());

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
            showBusy(helper.isLaunchBusy() || System.currentTimeMillis() - launchedAt < 1000);
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

    // --8<-- [start:drawer]
    private DrawerDockWidget createCategoryDrawer(
            String id,
            String title,
            String iconKey,
            XdgCategory category,
            java.awt.event.ActionListener fallbackPrimaryAction,
            List<DrawerTool> defaultTools,
            boolean hasDrawer) {

        List<XdgDesktopEntry> apps = XdgApplicationManager.getInstance().getApplicationsByCategory(category);

        // 1. Add discovered XDG applications for this category
        List<DrawerTool> tools = new ArrayList<>(xdgTools(category));

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
    // --8<-- [end:drawer]

    /** The watch over the panel while an application starts, as the window manager shows it elsewhere. */
    private void showBusy(boolean busy) {
        Cursor cursor = Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR);
        if (getCursor().getType() != cursor.getType()) {
            setCursor(cursor);
        }
    }

    private void launched() {
        launchedAt = System.currentTimeMillis();
        showBusy(true);
    }

    /** The Software program (amadeos-software) with the given option, or null while it is not installed. */
    private Runnable updateWindow(String... args) {
        String program = "/usr/bin/amadeos-software";
        if (!new File(program).canExecute()) return null;
        String[] command = new String[args.length + 1];
        command[0] = program;
        System.arraycopy(args, 0, command, 1, args.length);
        return () -> launchProcess(command);
    }

    private void launchProcess(String... cmd) {
        try {
            eu.amadeos.system.base.process.ProcessLauncher.start(java.util.List.of(cmd));
            launched();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to launch " + cmd[0] + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The installed applications of a category as drawer entries. */
    private List<DrawerTool> xdgTools(XdgCategory category) {
        return xdgTools(category, null);
    }

    /** The installed applications of {@code category} that are not also in {@code except}. */
    private List<DrawerTool> xdgTools(XdgCategory category, XdgCategory except) {
        List<DrawerTool> tools = new ArrayList<>();
        for (XdgDesktopEntry app : XdgApplicationManager.getInstance().getApplicationsByCategory(category)) {
            if (except != null && app.matchesCategory(except)) {
                continue;
            }
            tools.add(xdgTool(app));
        }
        return tools;
    }

    /**
     * The installed applications of the categories, each once, without those whose program (the first
     * word of Exec, without its directory) is one of {@code programs}: the drawer has fixed entries for them.
     */
    private List<DrawerTool> xdgTools(List<XdgCategory> categories, Set<String> programs) {
        List<DrawerTool> tools = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (XdgCategory category : categories) {
            for (XdgDesktopEntry app : XdgApplicationManager.getInstance().getApplicationsByCategory(category)) {
                if (programs.contains(programOf(app)) || !seen.add(app.id())) {
                    continue;
                }
                tools.add(xdgTool(app));
            }
        }
        return tools;
    }

    /** The program of a desktop entry: {@code /usr/bin/amadeos-sysinfo %F} gives {@code amadeos-sysinfo}. */
    static String programOf(XdgDesktopEntry app) {
        String exec = app.exec() == null ? "" : app.exec().strip();
        String first = exec.isEmpty() ? "" : exec.split("\\s+", 2)[0];
        int slash = first.lastIndexOf('/');
        return slash >= 0 ? first.substring(slash + 1) : first;
    }

    private DrawerTool xdgTool(XdgDesktopEntry app) {
        return new DrawerTool(app.name(), app.icon(), e -> {
            try {
                app.launch();
                launched();
            } catch (Exception ex) {
                LOGGER.warning("Failed to launch XDG app " + app.name() + ": " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Failed to launch " + app.name() + ": " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openInFileManager(String folder) {
        launchProcess("amade-files", folder);
    }

    /** Opens the trash view of amade-files, which knows where the trash is and creates it on first use. */
    private void openTrash() {
        openInFileManager("trash:");
    }

    /**
     * Opens the User's Handbook with xdg-open, so in the web browser (Firefox in amadeOS): its MkDocs
     * pages need JavaScript and CSS 3, which amade-web does not have. Without the package, the online copy.
     */
    private void openDesktopHelp() {
        launchProcess("xdg-open", new File(DESKTOP_HELP).isFile() ? DESKTOP_HELP : DESKTOP_HELP_ONLINE);
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
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

}
