package eu.amadeos.system.panel.horizon;

import com.sun.jna.Native;
import eu.amadeos.commons.config.ConfigWatcher;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.system.base.panel.DockSettings;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgCategory;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.panel.cde.PanelTexts;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Horizon's dock: a floating bar at the bottom of the screen with the favourite programs and the running ones,
 * the program drawers (the groups of the Front Panel), the Downloads and Documents folders and the Trash. A
 * dot under an icon marks a running program. Minimised windows stay in their program's icon instead of
 * becoming icons on the desktop: the dock tells the window manager so with {@code _AMADEOS_DOCK_TAKES_MINIMIZED}.
 * It runs in the top bar's program, which starts it.
 */
public final class HorizonDock extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(HorizonDock.class.getName());
    public static final String WINDOW_TITLE = "amadeOS Horizon Dock";
    /** The space between the dock and the bottom edge of the screen. */
    static final int GAP = 6;
    private static final int PADDING = 6;
    private static final int DRAWER_ICON = 32;

    // --8<-- [start:drawers]
    /** The program drawers, the groups of the Front Panel's drawers. */
    enum Drawer {
        OFFICE("editor", "text-editor", XdgCategory.TEXT_EDITOR, XdgCategory.OFFICE),
        INTERNET("mail", "mail", XdgCategory.MAIL, XdgCategory.NETWORK),
        MEDIA("multimedia", "multimedia", XdgCategory.AUDIO_VIDEO),
        GRAPHICS("graphics", "graphics", XdgCategory.GRAPHICS),
        GAMES("games", "games", XdgCategory.GAMES),
        TOOLS("tools", "app-manager", XdgCategory.TERMINAL, XdgCategory.SETTINGS, XdgCategory.MONITOR,
                XdgCategory.UTILITY, XdgCategory.DEVELOPMENT, XdgCategory.FILE_MANAGER, XdgCategory.HELP);

        final String textKey;
        final String iconKey;
        final List<XdgCategory> categories;

        Drawer(String textKey, String iconKey, XdgCategory... categories) {
            this.textKey = textKey;
            this.iconKey = iconKey;
            this.categories = List.of(categories);
        }

        String title() {
            return PanelTexts.get("panel.drawer." + textKey);
        }

        /** The installed programs of the drawer, each once, sorted by name. */
        List<XdgDesktopEntry> programs(XdgApplicationManager apps) {
            Set<XdgDesktopEntry> set = new LinkedHashSet<>();
            for (XdgCategory c : categories) set.addAll(apps.getApplicationsByCategory(c));
            List<XdgDesktopEntry> list = new ArrayList<>(set);
            list.sort(Comparator.comparing(e -> e.name().toLowerCase()));
            return list;
        }
    }
    // --8<-- [end:drawers]

    private final X11EwmhHelper helper;
    private final JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    private final Map<String, DockButton> programButtons = new HashMap<>();
    private final Map<String, Icon> windowIcons = new HashMap<>();
    private final Timer pollTimer;
    private DockSettings settings = DockSettings.load(DockSettings.userFile());
    private List<DockModel.Program> programs = List.of();
    private String windowsSignature = "";
    private long activeWindow;
    private int registeredStrut = -1;

    public HorizonDock(X11EwmhHelper helper) {
        super(WINDOW_TITLE);
        this.helper = Objects.requireNonNull(helper);
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setFocusableWindowState(false);
        setType(Type.UTILITY);

        JPanel root = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                BarColors c = colors();
                int arc = arc();
                g.setColor(c.bar());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(BarColors.mix(c.bar(), c.text(), 0.07));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g.setColor(BarColors.mix(c.bar(), c.text(), 0.2));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(PADDING / 2, PADDING, PADDING / 2, PADDING));
        row.setOpaque(false);
        root.add(row);
        setContentPane(root);
        // The right mouse button on the dock itself, between and around the icons
        root.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu menu = dockMenu();
                    menu.show(root, e.getX(), -menu.getPreferredSize().height - 4);
                }
            }
        });

        rebuild();
        ConfigWatcher watch = new ConfigWatcher(eu.amadeos.system.base.xdg.XdgDirectories.amadeosConfigDir(),
                Set.of(DockSettings.FILE_NAME), "dock-settings", () -> SwingUtilities.invokeLater(this::reloadSettings));
        watch.start();
        ThemeManager.addListener(theme -> SwingUtilities.invokeLater(() -> {
            windowIcons.clear();
            THEME_ICONS.clear();
            rebuild();
        }));

        // --8<-- [start:poll]
        pollTimer = new Timer(700, e -> poll());
        pollTimer.start();
        // --8<-- [end:poll]
    }

    /** The menu of the dock itself: show the desktop, and the dock's settings. */
    JPopupMenu dockMenu() {
        JPopupMenu menu = new JPopupMenu();
        boolean shown = helper.isShowingDesktop();
        JMenuItem desktop = new JMenuItem(TopBarTexts.get(shown ? "desktop.back" : "desktop.show"));
        desktop.addActionListener(e -> helper.setShowingDesktop(!shown));
        menu.add(desktop);
        if (new java.io.File(HorizonTopBar.WIDGETS).canExecute()) {
            JMenuItem widgets = new JMenuItem(TopBarTexts.get("system.widgets"));
            widgets.addActionListener(e -> launch(HorizonTopBar.WIDGETS, "--gallery"));
            menu.add(widgets);
        }
        menu.addSeparator();
        JMenuItem settingsItem = new JMenuItem(TopBarTexts.get("dock.settings"));
        settingsItem.addActionListener(e -> launch("amadeos-settings", "look"));
        menu.add(settingsItem);
        return menu;
    }

    private static BarColors colors() {
        return BarColors.of(ThemeManager.currentTheme());
    }

    private int arc() {
        return settings.iconSize() / 2 + 6;
    }

    /** Height of the dock for the icon size. */
    int dockHeight() {
        return DockButton.cellHeight(settings.iconSize()) + PADDING;
    }

    private void reloadSettings() {
        settings = DockSettings.load(DockSettings.userFile());
        windowIcons.clear();
        rebuild();
        registerAsDock();
    }

    // --8<-- [start:windows]
    /** The program windows on the screen, read again every 0.7 seconds; the dock is rebuilt when they change. */
    private void poll() {
        List<DockModel.Window> windows = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (long win : helper.getClientList()) {
            String title = helper.getWindowTitle(win);
            if (title != null && title.startsWith("amadeOS Horizon")) continue;
            if (helper.isDialog(win)) continue;
            boolean minimized = helper.isWindowMinimized(win);
            String cls = helper.getWindowClass(win);
            windows.add(new DockModel.Window(win, cls, title, minimized));
            sig.append(win).append(cls).append(minimized ? 'm' : 'n').append(';');
        }
        long active = helper.getActiveWindow();
        if (!sig.toString().equals(windowsSignature)) {
            windowsSignature = sig.toString();
            currentWindows = windows;
            rebuild();
        } else if (active != activeWindow) {
            activeWindow = active;
            updateMarks();
        }
        activeWindow = active;
        if (getWidth() > 0 && registeredStrut >= 0 && !getBounds().equals(placement())) place();
    }
    // --8<-- [end:windows]

    private List<DockModel.Window> currentWindows = List.of();

    private void rebuild() {
        XdgApplicationManager apps = XdgApplicationManager.getInstance();
        programs = DockModel.programs(settings.favourites(), apps.getAllApplications(), currentWindows);
        int size = settings.iconSize();
        row.removeAll();
        programButtons.clear();
        for (DockModel.Program p : programs) {
            DockButton b = new DockButton(programIcon(p, size), size, p.name(), HorizonDock::colors,
                    e -> programClicked(p, e));
            programButtons.put(p.key(), b);
            row.add(b);
        }
        if (settings.drawers()) {
            row.add(separator());
            for (Drawer d : Drawer.values()) {
                if (d.programs(apps).isEmpty()) continue;
                DockButton[] holder = new DockButton[1];
                holder[0] = new DockButton(themeIcon(d.iconKey, size), size, d.title(),
                        HorizonDock::colors, e -> showDrawer(d, holder[0]));
                holder[0].setMark(DockButton.Mark.DRAWER, false);
                row.add(holder[0]);
            }
        }
        row.add(separator());
        if (settings.folders()) {
            XdgDirectories xdg = XdgDirectories.system();
            row.add(folderButton(xdg.downloads(), FolderIcon.Kind.DOWNLOADS, TopBarTexts.get("dock.downloads"), size));
            row.add(folderButton(xdg.documents(), FolderIcon.Kind.DOCUMENTS, TopBarTexts.get("dock.documents"), size));
        }
        row.add(new DockButton(themeIcon("trash", size), size, TopBarTexts.get("dock.trash"),
                HorizonDock::colors, e -> launch("amade-files", "trash:")));
        updateMarks();
        row.revalidate();
        place();
        repaint();
    }

    private JComponent separator() {
        JComponent sep = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(BarColors.mix(colors().bar(), colors().text(), 0.25));
                int h = getHeight() * 3 / 5;
                g.fillRect(getWidth() / 2, (getHeight() - h) / 2 - 3, 1, h);
            }
        };
        sep.setPreferredSize(new Dimension(13, DockButton.cellHeight(settings.iconSize())));
        return sep;
    }

    private void updateMarks() {
        for (DockModel.Program p : programs) {
            DockButton b = programButtons.get(p.key());
            if (b == null) continue;
            DockButton.Mark mark = !p.running() ? DockButton.Mark.NONE
                    : p.allMinimized() ? DockButton.Mark.MINIMIZED : DockButton.Mark.RUNNING;
            boolean active = p.windows().stream().anyMatch(w -> w.id() == activeWindow && !w.minimized());
            b.setMark(mark, active);
        }
    }

    private Icon programIcon(DockModel.Program p, int size) {
        if (p.entry() != null && p.entry().icon() != null && !p.entry().icon().isBlank()) {
            return themeIcon(p.entry().icon(), size);
        }
        return windowIcons.computeIfAbsent(p.key() + ":" + size, k -> {
            BufferedImage img = p.windows().isEmpty() ? null : helper.getWindowIcon(p.windows().get(0).id(), size);
            return img != null ? new ImageIcon(img) : themeIcon("window", size);
        });
    }

    /**
     * A theme icon at {@code size}: looked up at 48 pixels, the size icon themes have most often, and scaled,
     * so that the in-between dock sizes find the same icons.
     */
    static Icon themeIcon(String key, int size) {
        return THEME_ICONS.computeIfAbsent(key + ":" + size, k -> scaledThemeIcon(key, size));
    }

    private static final Map<String, Icon> THEME_ICONS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Icon scaledThemeIcon(String key, int size) {
        Icon icon = IconManager.getIcon(key, 48);
        if (size == 48 || icon.getIconWidth() <= 0) return icon;
        BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, size, size, null);
        g.dispose();
        return new ImageIcon(scaled);
    }

    // --8<-- [start:click]
    /**
     * Left click: starts the program, or brings its window to the front; on the program in front it minimises
     * its window, or goes on to its next window. Middle click: a new window. Right click: the menu.
     */
    private void programClicked(DockModel.Program p, MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            programMenu(p).show(programButtons.get(p.key()), 0, -programMenu(p).getPreferredSize().height - 4);
            return;
        }
        if (SwingUtilities.isMiddleMouseButton(e) || !p.running()) {
            start(p);
            return;
        }
        long active = helper.getActiveWindow();
        boolean inFront = p.windows().stream().anyMatch(w -> w.id() == active && !w.minimized());
        if (!inFront) {
            DockModel.Window target = p.windows().stream().filter(w -> !w.minimized()).findFirst()
                    .orElse(p.windows().get(0));
            helper.activateWindow(target.id());
        } else if (p.windows().size() == 1) {
            helper.minimizeWindow(active);
        } else {
            helper.activateWindow(DockModel.next(p, active).id());
        }
    }
    // --8<-- [end:click]

    private JPopupMenu programMenu(DockModel.Program p) {
        JPopupMenu menu = new JPopupMenu();
        for (DockModel.Window w : p.windows()) {
            String title = w.title() == null || w.title().isBlank() ? p.name() : w.title();
            JMenuItem item = new JMenuItem(title.length() > 50 ? title.substring(0, 49) + "…" : title);
            item.addActionListener(ev -> helper.activateWindow(w.id()));
            menu.add(item);
        }
        if (p.running()) menu.addSeparator();
        if (p.entry() != null) {
            JMenuItem open = new JMenuItem(p.running() ? TopBarTexts.get("dock.new.window") : p.name());
            open.addActionListener(ev -> start(p));
            menu.add(open);
            boolean favourite = settings.favourites().contains(p.entry().id());
            JMenuItem keep = new JMenuItem(TopBarTexts.get(favourite ? "dock.remove" : "dock.keep"));
            keep.addActionListener(ev -> {
                settings = settings.withFavourite(p.entry().id(), !favourite);
                settings.save(DockSettings.userFile());
                rebuild();
            });
            menu.add(keep);
        }
        if (p.running()) {
            JMenuItem close = new JMenuItem(TopBarTexts.get(p.windows().size() > 1 ? "dock.close.all" : "dock.close"));
            close.addActionListener(ev -> p.windows().forEach(w -> helper.closeWindow(w.id())));
            menu.add(close);
        }
        return menu;
    }

    private void start(DockModel.Program p) {
        if (p.entry() == null) return;
        try {
            p.entry().launch();
        } catch (Exception ex) {
            launchFailed(p.name(), ex);
        }
    }

    // --8<-- [start:drawer-popup]
    /** A grid of the drawer's programs above its icon; a click starts one. */
    private void showDrawer(Drawer drawer, DockButton button) {
        JPopupMenu popup = new JPopupMenu();
        List<XdgDesktopEntry> apps = drawer.programs(XdgApplicationManager.getInstance());
        JLabel title = new JLabel(drawer.title(), SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        popup.add(title);
        if (apps.isEmpty()) {
            popup.add(new JLabel(TopBarTexts.get("dock.drawer.empty")));
        } else {
            int columns = Math.min(4, apps.size());
            JPanel grid = new JPanel(new GridLayout(0, columns, 4, 4));
            grid.setOpaque(false);
            grid.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
            for (XdgDesktopEntry app : apps) {
                JButton tile = new JButton(shortName(app.name()), themeIcon(app.icon(), DRAWER_ICON));
                tile.setToolTipText(app.name());
                tile.setVerticalTextPosition(SwingConstants.BOTTOM);
                tile.setHorizontalTextPosition(SwingConstants.CENTER);
                tile.setPreferredSize(new Dimension(96, DRAWER_ICON + 46));
                tile.setFocusable(false);
                tile.setContentAreaFilled(false);
                tile.setBorderPainted(false);
                tile.addActionListener(e -> {
                    popup.setVisible(false);
                    try {
                        app.launch();
                    } catch (Exception ex) {
                        launchFailed(app.name(), ex);
                    }
                });
                grid.add(tile);
            }
            popup.add(grid);
        }
        showAbove(popup, button);
    }
    // --8<-- [end:drawer-popup]

    /** A drawer tile's label: without a leading "amadeOS", on at most two lines. */
    static String shortName(String name) {
        String n = name.startsWith("amadeOS ") ? name.substring("amadeOS ".length()) : name;
        if (n.length() > 26) n = n.substring(0, 25) + "…";
        String text = escape(n);
        if (n.length() > 11) {
            // break at the space nearest the middle
            int best = -1;
            for (int i = n.indexOf(' '); i >= 0; i = n.indexOf(' ', i + 1)) {
                if (best < 0 || Math.abs(i - n.length() / 2) < Math.abs(best - n.length() / 2)) best = i;
            }
            if (best > 0) text = escape(n.substring(0, best)) + "<br>" + escape(n.substring(best + 1));
        }
        return "<html><center>" + text + "</center></html>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private DockButton folderButton(Path folder, FolderIcon.Kind kind, String name, int size) {
        DockButton[] holder = new DockButton[1];
        holder[0] = new DockButton(new FolderIcon(kind, size, HorizonDock::colors), size, name, HorizonDock::colors,
                e -> showFolder(folder, name, holder[0]));
        holder[0].setMark(DockButton.Mark.DRAWER, false);
        return holder[0];
    }

    // --8<-- [start:folder]
    /** The ten newest files of a folder, newest first, and Open folder. */
    private void showFolder(Path folder, String name, DockButton button) {
        JPopupMenu popup = new JPopupMenu();
        JLabel title = new JLabel(name);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        popup.add(title);
        List<Path> files = newestFiles(folder, 10);
        if (files.isEmpty()) {
            JMenuItem none = new JMenuItem(TopBarTexts.get("dock.folder.empty"));
            none.setEnabled(false);
            popup.add(none);
        }
        for (Path file : files) {
            JMenuItem item = new JMenuItem(file.getFileName().toString());
            item.addActionListener(e -> launch("xdg-open", file.toString()));
            popup.add(item);
        }
        popup.addSeparator();
        JMenuItem open = new JMenuItem(TopBarTexts.get("dock.open.folder"), IconManager.getIcon("file-manager", 16));
        open.addActionListener(e -> launch("amade-files", folder.toString()));
        popup.add(open);
        showAbove(popup, button);
    }

    static List<Path> newestFiles(Path folder, int max) {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> !p.getFileName().toString().startsWith("."))
                    .map(p -> Map.entry(p, modified(p)))
                    .sorted(Map.Entry.<Path, Long>comparingByValue().reversed())
                    .limit(max).map(Map.Entry::getKey).toList();
        } catch (IOException e) {
            return List.of();
        }
    }
    // --8<-- [end:folder]

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private void showAbove(JPopupMenu popup, JComponent button) {
        Dimension size = popup.getPreferredSize();
        int x = (button.getWidth() - size.width) / 2;
        popup.show(button, x, -size.height - 6);
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

    // --8<-- [start:place]
    /** Centred at the bottom, {@link #GAP} pixels above the edge, as wide as its icons. */
    private java.awt.Rectangle placement() {
        Dimension pref = getContentPane().getPreferredSize();
        int w = Math.min(pref.width, helper.screenWidth() - 2 * GAP);
        int h = dockHeight();
        return new java.awt.Rectangle((helper.screenWidth() - w) / 2, helper.screenHeight() - h - GAP, w, h);
    }

    private void place() {
        java.awt.Rectangle r = placement();
        if (!getBounds().equals(r)) setBounds(r);
        try {
            setShape(new RoundRectangle2D.Double(0, 0, r.width, r.height, arc(), arc()));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException e) {
            LOGGER.fine("No shaped dock: " + e.getMessage());
        }
        if (registeredStrut >= 0 && registeredStrut != r.height + GAP) registerAsDock();
    }

    /** A dock window with a strut at the bottom, and the mark that it shows the minimised windows. */
    public void registerAsDock() {
        long win = nativeWindowId();
        if (win == 0) {
            LOGGER.warning("Could not determine the dock's X11 window");
            return;
        }
        int strut = dockHeight() + GAP;
        helper.setDockProperties(win, helper.screenWidth(), helper.screenHeight(), strut);
        helper.setDockTakesMinimized(win);
        registeredStrut = strut;
    }
    // --8<-- [end:place]

    private long nativeWindowId() {
        try {
            return Native.getWindowID(this);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Native.getWindowID failed: " + e.getMessage());
            return 0;
        }
    }
}
