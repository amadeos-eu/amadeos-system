package eu.amadeos.system.widgets;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.system.base.process.InstanceChannel;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.xdg.XdgDirectories;

import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The widget host, {@code amadeos-widgets}: one program for all desktop widgets, so that five widgets do not
 * cost five Java programs. It runs while the look is Horizon (the top bar starts it and it ends when the look
 * changes to classic), keeps one per user and display and opens the gallery when started again with
 * {@code --gallery}.
 */
public final class WidgetHost {
    private static final Logger LOGGER = Logger.getLogger(WidgetHost.class.getName());
    /** The socket of the running host; a second start hands its arguments over. */
    public static final String CHANNEL = "amadeos-widgets";

    private static WidgetHost instance;

    private final X11EwmhHelper helper;
    private final WidgetCatalog catalog;
    private final WidgetLayout layout;
    private final Map<String, WidgetWindow> windows = new LinkedHashMap<>();
    private Gallery gallery;

    WidgetHost(X11EwmhHelper helper, WidgetCatalog catalog, WidgetLayout layout) {
        this.helper = helper;
        this.catalog = catalog;
        this.layout = layout;
    }

    static WidgetHost instance() {
        return instance;
    }

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("--help")) {
            System.out.println("Usage: amadeos-widgets [--gallery]");
            return;
        }
        Path socket = InstanceChannel.defaultPath(CHANNEL);
        if (InstanceChannel.send(socket, arguments)) return;
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("amadeos-widgets: no X display");
            System.exit(5);
        }
        X11EwmhHelper helper;
        try {
            helper = X11EwmhHelper.open(null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "No X display", e);
            System.exit(5);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            followDesktopTheme(helper);
            AmadeUI.install(ThemeManager.currentTheme());
            Path layoutFile = XdgDirectories.amadeosConfigDir().resolve(WidgetLayout.FILE_NAME);
            instance = new WidgetHost(helper, WidgetCatalog.load(WidgetCatalog.defaultDirs()),
                    WidgetLayout.load(layoutFile));
            instance.start();
            if (arguments.contains("--gallery")) instance.showGallery();
        });
        try {
            InstanceChannel.listen(socket, a -> SwingUtilities.invokeLater(() -> {
                if (a.contains("--gallery")) instance.showGallery();
            }));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "A second start cannot reach the widget host", e);
        }
    }

    /** The colours the desktop has now (the window manager keeps their name on the root window). */
    private static void followDesktopTheme(X11EwmhHelper helper) {
        String id = helper.getDesktopTheme();
        if (id != null && !id.isBlank() && !id.equals(ThemeManager.currentTheme().id())) ThemeManager.applyTheme(id);
    }

    void start() {
        // Settings changes the colours: follow them, as the top bar does
        new javax.swing.Timer(1500, e -> followDesktopTheme(helper)).start();
        for (WidgetLayout.Placement p : layout.placed()) {
            catalog.find(p.type()).ifPresentOrElse(w -> show(p, w),
                    () -> LOGGER.info(() -> "Widget " + p.type() + " is not installed; " + p.id() + " stays hidden"));
        }
        ThemeManager.addListener(theme -> SwingUtilities.invokeLater(() -> {
            if (theme.look() != DesktopLook.HORIZON) {
                LOGGER.info("The look is classic: the widgets leave the desktop");
                System.exit(0);
            }
            AmadeUI.apply(theme);
            for (WidgetWindow w : windows.values()) {
                w.context().themeChanged();
                w.repaint();
            }
        }));
    }

    private void show(WidgetLayout.Placement p, DesktopWidget widget) {
        HostContext context = new HostContext(WidgetLayout.settingsFile(layout.file(), p.id()));
        WidgetWindow window = new WidgetWindow(p.id(), widget, context, p.bounds(),
                bounds -> layout.move(p.id(), bounds), () -> remove(p.id()));
        windows.put(p.id(), window);
        window.showOnDesktop(helper);
    }

    /** Puts a new copy of {@code widget} on the desktop, in the first free place along the right edge. */
    void add(DesktopWidget widget) {
        Dimension size = widget.defaultSize();
        WidgetLayout.Placement p = layout.add(widget.id(), layout.freeSpot(workArea(), size.width, size.height));
        show(p, widget);
    }

    void remove(String id) {
        WidgetWindow w = windows.remove(id);
        if (w != null) w.close();
        layout.remove(id);
    }

    /** The screen without the top bar and the dock. */
    Rectangle workArea() {
        int[] edges = helper.reservedEdges(); // left, right, top, bottom
        return new Rectangle(edges[0], edges[2], helper.screenWidth() - edges[0] - edges[1],
                helper.screenHeight() - edges[2] - edges[3]);
    }

    void showGallery() {
        if (gallery == null) gallery = new Gallery(catalog.all(), this::add);
        gallery.setVisible(true);
        gallery.toFront();
    }
}
