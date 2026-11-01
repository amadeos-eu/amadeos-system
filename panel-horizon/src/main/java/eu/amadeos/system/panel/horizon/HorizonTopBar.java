package eu.amadeos.system.panel.horizon;

import com.sun.jna.Native;
import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.bluetooth.BluezBluetoothService;
import eu.amadeos.system.base.net.NmcliNetworkService;
import eu.amadeos.system.base.notify.NotificationClient;
import eu.amadeos.system.base.panel.PanelSettings;
import eu.amadeos.system.base.panel.PanelSettingsStore;
import eu.amadeos.system.base.panel.PanelSwitch;
import eu.amadeos.system.base.power.PowerSupplies;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.sound.PactlSoundService;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.ui.SessionDialogHelper;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgCategory;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.base.xdg.XdgDirectories;
import eu.amadeos.commons.config.ConfigWatcher;
import eu.amadeos.system.packages.AptPackageService;
import eu.amadeos.system.panel.cde.PanelTexts;
import eu.amadeos.system.panel.cde.status.BatteryCell;
import eu.amadeos.system.panel.cde.status.BluetoothCell;
import eu.amadeos.system.panel.cde.status.CameraCell;
import eu.amadeos.system.panel.cde.status.CameraUsers;
import eu.amadeos.system.panel.cde.status.NetworkCell;
import eu.amadeos.system.panel.cde.status.NotificationCell;
import eu.amadeos.system.panel.cde.status.SoundCell;
import eu.amadeos.system.panel.cde.status.StatusCell;
import eu.amadeos.system.panel.cde.status.UpdatesCell;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The top bar of the Horizon look: along the top edge of the screen, the system menu (programs, Settings,
 * Software, lock, log out) and the name of the active program on the left; the workspaces, the status cells of
 * the Front Panel and the clock with the date on the right. It reserves its height at the top of the screen,
 * so windows stay below it, and hands over to the Front Panel when the look changes back to classic.
 */
public final class HorizonTopBar extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(HorizonTopBar.class.getName());
    public static final int BAR_HEIGHT = 30;
    public static final String WINDOW_TITLE = "amadeOS Horizon Top Bar";
    private static java.nio.channels.FileLock panelLock;

    private final X11EwmhHelper helper;
    private final BarButton systemButton;
    private final BarButton programButton;
    private final WorkspaceDots workspaces;
    private final BarButton clockButton;
    private final Map<String, StatusCell> cells = new LinkedHashMap<>();
    private final JPanel cellPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final PanelSettingsStore panelSettings = PanelSettingsStore.user();
    private final Timer pollTimer;
    private Dimension screenSize;
    private long activeWindow;

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (!acquireSingleInstanceLock()) {
            LOGGER.warning("Another panel is already running. Exiting.");
            return;
        }
        X11EwmhHelper helper;
        try {
            helper = X11EwmhHelper.open(args.length > 0 ? args[0] : null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to X11 display: " + e.getMessage(), e);
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                HorizonTopBar bar = new HorizonTopBar(helper);
                bar.addNotify();
                bar.registerAsDock();
                bar.setVisible(true);
                bar.registerAsDock();
                // The dock at the bottom, in the same program
                HorizonDock dock = new HorizonDock(helper);
                dock.addNotify();
                dock.registerAsDock();
                dock.setVisible(true);
                dock.registerAsDock();
                // Settings > Appearance back to classic: the session starts the Front Panel instead.
                PanelSwitch.watch(DesktopLook.HORIZON);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error initializing the top bar: " + ex.getMessage(), ex);
                System.exit(1);
            }
        });
    }
    // --8<-- [end:main]

    /** One panel at a time: the Front Panel and the top bar share the lock. */
    private static boolean acquireSingleInstanceLock() {
        try {
            File lockFile = new File(System.getProperty("user.home", "/tmp"), ".amadeos-panel.lock");
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(lockFile, "rw");
            panelLock = raf.getChannel().tryLock();
            return panelLock != null;
        } catch (Exception e) {
            return true;
        }
    }

    public HorizonTopBar(X11EwmhHelper helper) {
        super(WINDOW_TITLE);
        this.helper = Objects.requireNonNull(helper);
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setFocusableWindowState(false);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                BarColors c = BarColors.of(ThemeManager.currentTheme());
                g.setColor(c.bar());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(BarColors.mix(c.bar(), java.awt.Color.BLACK, 0.4));
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        root.setOpaque(true);
        setContentPane(root);

        JPanel left = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        systemButton = new BarButton("") {
            @Override
            int markWidth() {
                return 18;
            }

            @Override
            void paintMark(Graphics2D g, BarColors c) {
                // The amadeOS ring in the accent colour
                int d = 14;
                int x = getInsets().left + 1;
                int y = (getHeight() - d) / 2;
                g.setStroke(new BasicStroke(2.2f));
                g.setColor(c.accent());
                g.drawOval(x, y, d, d);
            }
        };
        systemButton.setToolTipText(TopBarTexts.get("system.tooltip"));
        systemButton.addActionListener(e -> showMenu(systemButton, systemMenu()));
        programButton = new BarButton(TopBarTexts.get("program.desktop"));
        programButton.setBold(true);
        programButton.addActionListener(e -> showMenu(programButton, programMenu()));
        left.add(systemButton);
        left.add(programButton);
        root.add(left, BorderLayout.WEST);

        JPanel right = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        workspaces = new WorkspaceDots(helper::setCurrentDesktop);
        workspaces.setActive(Math.max(0, helper.getCurrentDesktop()));
        right.add(workspaces);
        cellPanel.setOpaque(false);
        right.add(cellPanel);
        clockButton = new BarButton(clockText());
        clockButton.setToolTipText(TopBarTexts.get("clock.tooltip"));
        clockButton.addActionListener(e -> launch("amade-calendar"));
        right.add(clockButton);
        root.add(right, BorderLayout.EAST);

        createCells();
        applyPanelSettings();
        ConfigWatcher settingsWatch = new ConfigWatcher(XdgDirectories.amadeosConfigDir(),
                Set.of(PanelSettingsStore.FILE_NAME), "topbar-settings",
                () -> SwingUtilities.invokeLater(this::applyPanelSettings));
        settingsWatch.start();

        ThemeManager.addListener(theme -> SwingUtilities.invokeLater(() -> applyTheme(theme)));
        placeBar();

        // --8<-- [start:poll]
        pollTimer = new Timer(500, e -> poll());
        pollTimer.start();
        // --8<-- [end:poll]
    }

    private static JPanel transparent(FlowLayout layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /** The theme from the root window (Settings changed it), the active program, the workspace, the clock. */
    private void poll() {
        String remoteThemeId = helper.getDesktopTheme();
        if (remoteThemeId != null && !remoteThemeId.isBlank()
                && !remoteThemeId.equals(ThemeManager.currentTheme().id())) {
            ThemeManager.applyTheme(remoteThemeId);
        }
        long active = helper.getActiveWindow();
        if (active != activeWindow) {
            activeWindow = active;
            updateProgramName();
        }
        int desktop = helper.getCurrentDesktop();
        if (desktop >= 0) workspaces.setActive(desktop);
        String clock = clockText();
        if (!clock.equals(clockButton.getText())) {
            clockButton.setText(clock);
            clockButton.revalidate();
        }
        if (screenSize != null && !screenSize.equals(new Dimension(helper.screenWidth(), helper.screenHeight()))) {
            placeBar();
            registerAsDock();
        }
    }

    private void updateProgramName() {
        String name = activeWindow == 0 ? "" : ProgramNames.nameOf(helper.getWindowClass(activeWindow),
                helper.getWindowTitle(activeWindow), XdgApplicationManager.getInstance().getAllApplications());
        programButton.setText(name.isBlank() ? TopBarTexts.get("program.desktop") : name);
        programButton.revalidate();
        programButton.repaint();
    }

    // --8<-- [start:clock]
    /** "Sun 1 Nov  10:15" in the session's language. */
    static String clockText(LocalDateTime now, Locale locale) {
        String date = now.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)).replace(".", "");
        String time = now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale));
        return date + "   " + time;
    }
    // --8<-- [end:clock]

    private static String clockText() {
        return clockText(LocalDateTime.now(), Locale.getDefault());
    }

    // --8<-- [start:cells]
    /** The status cells of the Front Panel, in its order; Settings > Front Panel chooses which are shown. */
    private void createCells() {
        NotificationClient notifications = new NotificationClient();
        cells.put("network", new NetworkCell(new NmcliNetworkService(), () -> launch("amadeos-settings", "network")));
        cells.put("sound", new SoundCell(new PactlSoundService()));
        cells.put("battery", new BatteryCell(new PowerSupplies()));
        cells.put("bluetooth", new BluetoothCell(new BluezBluetoothService(), () -> launch("amadeos-settings", "bluetooth")));
        cells.put("notifications", new NotificationCell(notifications, () -> launch("amadeos-notifications"),
                () -> launch("amadeos-settings", "notifications")));
        cells.put("updates", new UpdatesCell(new AptPackageService(), software("--updates"), software("--settings")));
        cells.put("camera", new CameraCell(new CameraUsers()));
        DesktopTheme theme = ThemeManager.currentTheme();
        for (StatusCell cell : cells.values()) {
            cell.onThemeChanged(theme);
            cell.onAvailabilityChanged(this::layoutCells);
            cell.start();
        }
    }
    // --8<-- [end:cells]

    private void applyPanelSettings() {
        layoutCells();
    }

    private void layoutCells() {
        PanelSettings s = panelSettings.load();
        Map<String, Boolean> shown = Map.of("network", s.networkCell(), "sound", s.soundCell(),
                "battery", s.batteryCell(), "bluetooth", s.bluetoothCell(), "notifications", s.notificationsCell(),
                "updates", s.updatesCell(), "camera", true);
        cellPanel.removeAll();
        for (Map.Entry<String, StatusCell> e : cells.entrySet()) {
            StatusCell cell = e.getValue();
            if (!shown.getOrDefault(e.getKey(), true) || !cell.available()) continue;
            JComponent c = cell.component();
            c.setPreferredSize(new Dimension(28, BAR_HEIGHT - 4));
            JPanel bay = transparent(new FlowLayout(FlowLayout.CENTER, 0, 2));
            bay.add(c);
            cellPanel.add(bay);
        }
        cellPanel.revalidate();
        cellPanel.repaint();
    }

    private void applyTheme(DesktopTheme theme) {
        for (StatusCell cell : cells.values()) cell.onThemeChanged(theme);
        repaint();
    }

    // --8<-- [start:menus]
    /** The system menu: the programs by category, Files, Settings, Software, then lock and log out. */
    JPopupMenu systemMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(TopBarTexts.get("system.about"), "amadeos-sysinfo"));
        menu.addSeparator();
        menu.add(programsMenu());
        menu.add(item(TopBarTexts.get("system.files"), "amade-files"));
        menu.add(item(TopBarTexts.get("system.settings"), "amadeos-settings"));
        if (new File("/usr/bin/amadeos-software").canExecute()) {
            menu.add(item(TopBarTexts.get("system.software"), "amadeos-software"));
        }
        menu.add(item(TopBarTexts.get("system.taskmanager"), "amadeos-taskmanager"));
        menu.addSeparator();
        JMenuItem lock = new JMenuItem(TopBarTexts.get("system.lock"));
        lock.addActionListener(e -> SessionDialogHelper.lockScreen(this));
        menu.add(lock);
        JMenuItem exit = new JMenuItem(TopBarTexts.get("system.exit"));
        exit.addActionListener(e -> SessionDialogHelper.showSessionPowerDialog(this));
        menu.add(exit);
        return menu;
    }

    /** The installed programs, one submenu per category, read again each time the menu opens. */
    JMenu programsMenu() {
        JMenu programs = new JMenu(TopBarTexts.get("system.programs"));
        List<String> seen = new ArrayList<>();
        for (XdgCategory category : XdgCategory.values()) {
            List<XdgDesktopEntry> apps = new ArrayList<>();
            for (XdgDesktopEntry app : XdgApplicationManager.getInstance().getApplicationsByCategory(category)) {
                if (!seen.contains(app.id())) apps.add(app);
            }
            if (apps.isEmpty()) continue;
            JMenu sub = new JMenu(categoryName(category));
            sub.setIcon(IconManager.getIcon(category.iconKey(), 16));
            for (XdgDesktopEntry app : apps) {
                seen.add(app.id());
                JMenuItem item = new JMenuItem(app.name(), IconManager.getIcon(app.icon(), 16));
                item.addActionListener(e -> {
                    try {
                        app.launch();
                    } catch (Exception ex) {
                        launchFailed(app.name(), ex);
                    }
                });
                sub.add(item);
            }
            programs.add(sub);
        }
        return programs;
    }

    private static String categoryName(XdgCategory category) {
        try {
            return PanelTexts.get("category." + category.name().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return category.displayName();
        }
    }

    /** The menu of the active program: minimise and close its window, or the Task Manager. */
    JPopupMenu programMenu() {
        JPopupMenu menu = new JPopupMenu();
        long win = activeWindow;
        JMenuItem minimize = new JMenuItem(TopBarTexts.get("program.minimize"));
        minimize.setEnabled(win != 0);
        minimize.addActionListener(e -> helper.minimizeWindow(win));
        JMenuItem close = new JMenuItem(TopBarTexts.get("program.close"));
        close.setEnabled(win != 0);
        close.addActionListener(e -> helper.closeWindow(win));
        menu.add(minimize);
        menu.add(close);
        menu.addSeparator();
        menu.add(item(TopBarTexts.get("system.taskmanager"), "amadeos-taskmanager"));
        return menu;
    }
    // --8<-- [end:menus]

    private JMenuItem item(String text, String... command) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> launch(command));
        return item;
    }

    private void showMenu(BarButton button, JPopupMenu menu) {
        button.setMenuOpen(true);
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                button.setMenuOpen(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                button.setMenuOpen(false);
            }
        });
        menu.show(button, 0, BAR_HEIGHT - 2);
    }

    private Runnable software(String option) {
        if (!new File("/usr/bin/amadeos-software").canExecute()) return null;
        return () -> launch("/usr/bin/amadeos-software", option);
    }

    private void launch(String... command) {
        try {
            ProcessLauncher.start(List.of(command));
        } catch (Exception e) {
            launchFailed(command[0], e);
        }
    }

    private void launchFailed(String program, Exception e) {
        JOptionPane.showMessageDialog(this, PanelTexts.format("panel.launch.failed", program, e.getMessage()),
                PanelTexts.get("panel.error"), JOptionPane.ERROR_MESSAGE);
    }

    /** Along the whole top edge of the screen. */
    private void placeBar() {
        int w = helper.screenWidth();
        setBounds(0, 0, w, BAR_HEIGHT);
        screenSize = new Dimension(w, helper.screenHeight());
    }

    public void registerAsDock() {
        long winId = nativeWindowId();
        if (winId == 0) {
            LOGGER.warning("Could not determine the top bar's X11 window");
            return;
        }
        helper.setTopDockProperties(winId, helper.screenWidth(), BAR_HEIGHT);
    }

    private long nativeWindowId() {
        try {
            return Native.getWindowID(this);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Native.getWindowID failed: " + e.getMessage());
            return 0;
        }
    }
}
