package eu.amadeos.system.base.theme;

import eu.amadeos.system.base.xdg.XdgDirectories;
import eu.amadeos.system.base.theme.backdrop.Backdrops;
import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import eu.amadeos.system.base.x11.LibX11;
import eu.amadeos.system.base.x11.X11EwmhHelper;

/**
 * Manages desktop wallpapers and background rendering on the X11 Root Window.
 * Supports Fill, Fit, Stretch, Tile, and Center modes, solid color backdrops, CDE pattern backdrops
 * (X pixmaps and bitmaps coloured from the backdrop colour, see {@link Backdrops}),
 * persistence to $XDG_CONFIG_HOME/amadeos/wallpaper.conf (default ~/.config), and Freedesktop/X11 root pixmap export.
 * As in CDE, each of the four workspaces can have a backdrop of its own ({@code workspace.2.wallpaper} and so
 * on in the same file); a workspace without one shows the common backdrop. The window manager swaps them
 * when the workspace changes.
 */
public final class WallpaperManager {
    private static final Logger LOGGER = Logger.getLogger(WallpaperManager.class.getName());

    public enum ScaleMode {
        FILL("Fill Screen (Crop)"),
        FIT("Fit to Screen (Letterbox)"),
        STRETCH("Stretch to Screen"),
        TILE("Tile Pattern"),
        CENTER("Center (Original Size)");

        private final String label;
        ScaleMode(String label) { this.label = label; }
        public String label() { return label; }

        /** The label, so lists and combo boxes show "Fill Screen (Crop)" rather than FILL. */
        @Override
        public String toString() { return label; }
    }

    /** The list entry for a plain colour without image, CDE's "NoBackdrop". */
    public static final String NO_BACKDROP = "none";

    /** The backdrop of a new session: the amadeOS picture installed with the image, else the built-in one. */
    static final String SYSTEM_WALLPAPER = "/usr/share/backgrounds/amadeos/wallpaper.png";
    static final String CLASSPATH_WALLPAPER = "classpath:/wallpapers/default.png";

    public record WallpaperItem(String name, String path, boolean isClasspath) {
        /** Whether this is a CDE-style pattern that is coloured and tiled. */
        public boolean isPattern() {
            return Backdrops.isPattern(path);
        }

        @Override
        public String toString() { return name; }
    }

    @FunctionalInterface
    public interface WallpaperChangeListener {
        void onWallpaperChanged(String path, ScaleMode mode, Color bgColor);
    }

    private static final List<WallpaperChangeListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile String currentWallpaperPath =
            new File(SYSTEM_WALLPAPER).isFile() ? SYSTEM_WALLPAPER : CLASSPATH_WALLPAPER;
    private static volatile ScaleMode currentMode = ScaleMode.FILL;
    private static final Color DEFAULT_COLOR = new Color(0x4A, 0x69, 0x84); // Classic UNIX Slate
    private static volatile Color currentBgColor = DEFAULT_COLOR;

    private static NativeLong activeRootPixmap = null;

    static {
        loadPersistedConfig();
    }

    private WallpaperManager() {}

    public static void addListener(WallpaperChangeListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(WallpaperChangeListener listener) {
        LISTENERS.remove(listener);
    }

    public static String currentWallpaperPath() {
        return currentWallpaperPath;
    }

    public static ScaleMode currentScaleMode() {
        return currentMode;
    }

    public static Color currentBgColor() {
        return currentBgColor;
    }

    /**
     * Discovers all available wallpapers across system paths, user folders, and classpath.
     */
    public static List<WallpaperItem> availableWallpapers() {
        List<WallpaperItem> list = new ArrayList<>();

        // 1. Plain colour and the amadeOS default picture
        list.add(new WallpaperItem("No Backdrop (color only)", NO_BACKDROP, true));
        list.add(new WallpaperItem("amadeOS Default (Classpath)", CLASSPATH_WALLPAPER, true));
        // The original CDE backdrops, if the amadeos-backdrops-cde package is installed
        for (java.nio.file.Path file : Backdrops.systemBackdrops()) {
            list.add(new WallpaperItem(Backdrops.displayName(file.toString()), file.toString(), false));
        }

        // 2. System backgrounds: /usr/share/backgrounds/
        File sysBg = new File("/usr/share/backgrounds");
        scanDirectoryForWallpapers(sysBg, list);

        // 3. User backgrounds: ~/.local/share/backgrounds/ and ~/Pictures/
        String home = System.getProperty("user.home", "/tmp");
        scanDirectoryForWallpapers(new File(home, ".local/share/backgrounds"), list);
        scanDirectoryForWallpapers(new File(home, "Pictures/Wallpapers"), list);
        scanDirectoryForWallpapers(new File(home, "Pictures"), list);

        // 4. Custom amadeOS config wallpapers
        scanDirectoryForWallpapers(XdgDirectories.amadeosConfigDir().resolve("wallpapers").toFile(), list);
        // 5. Personal CDE backdrops (.pm/.bm), in CDE's own place and in the amadeOS config
        scanDirectoryForWallpapers(new File(home, ".dt/backdrops"), list);
        scanDirectoryForWallpapers(XdgDirectories.amadeosConfigDir().resolve("backdrops").toFile(), list);

        return Collections.unmodifiableList(list);
    }

    private static void scanDirectoryForWallpapers(File dir, List<WallpaperItem> out) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && !f.isHidden()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || Backdrops.isPattern(name)) {
                    out.add(new WallpaperItem(f.getName(), f.getAbsolutePath(), false));
                }
            } else if (f.isDirectory() && !f.isHidden() && ("amadeos".equalsIgnoreCase(f.getName()) || "backgrounds".equalsIgnoreCase(dir.getName()))) {
                scanDirectoryForWallpapers(f, out);
            }
        }
    }

    /**
     * Loads a wallpaper image from either a filesystem path or a classpath resource; patterns are
     * coloured with the current backdrop colour.
     */
    public static BufferedImage loadImage(String path) {
        return loadImage(path, currentBgColor);
    }

    /**
     * Loads a wallpaper image; X pixmap and bitmap patterns are coloured with {@code backdropColor}.
     */
    public static BufferedImage loadImage(String path, Color backdropColor) {
        if (path == null || path.isBlank() || NO_BACKDROP.equalsIgnoreCase(path)) {
            return null;
        }
        try {
            if (Backdrops.isPattern(path)) {
                return Backdrops.load(path, backdropColor != null ? backdropColor : currentBgColor);
            }
            if (path.startsWith("classpath:")) {
                String resPath = path.substring("classpath:".length());
                try (InputStream in = WallpaperManager.class.getResourceAsStream(resPath)) {
                    if (in != null) return ImageIO.read(in);
                }
            } else {
                File f = new File(path);
                if (f.isFile()) {
                    return ImageIO.read(f);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to load wallpaper image " + path + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Renders a wallpaper onto an image of target width & height using the chosen scale mode.
     */
    public static BufferedImage renderToTarget(BufferedImage src, int targetW, int targetH, ScaleMode mode, Color bgColor) {
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Fill background
        g.setColor(bgColor != null ? bgColor : currentBgColor);
        g.fillRect(0, 0, targetW, targetH);

        if (src == null) {
            g.dispose();
            return out;
        }

        int sw = src.getWidth();
        int sh = src.getHeight();

        switch (mode) {
            case FILL -> {
                // Proportionally scale to completely cover target, centered and cropped
                double scale = Math.max((double) targetW / sw, (double) targetH / sh);
                int scaledW = (int) Math.round(sw * scale);
                int scaledH = (int) Math.round(sh * scale);
                int x = (targetW - scaledW) / 2;
                int y = (targetH - scaledH) / 2;
                g.drawImage(src, x, y, scaledW, scaledH, null);
            }
            case FIT -> {
                // Proportionally scale to fit completely inside target, letterboxed
                double scale = Math.min((double) targetW / sw, (double) targetH / sh);
                int scaledW = (int) Math.round(sw * scale);
                int scaledH = (int) Math.round(sh * scale);
                int x = (targetW - scaledW) / 2;
                int y = (targetH - scaledH) / 2;
                g.drawImage(src, x, y, scaledW, scaledH, null);
            }
            case STRETCH -> {
                g.drawImage(src, 0, 0, targetW, targetH, null);
            }
            case TILE -> {
                for (int x = 0; x < targetW; x += sw) {
                    for (int y = 0; y < targetH; y += sh) {
                        g.drawImage(src, x, y, null);
                    }
                }
            }
            case CENTER -> {
                int x = (targetW - sw) / 2;
                int y = (targetH - sh) / 2;
                g.drawImage(src, x, y, null);
            }
        }

        g.dispose();
        return out;
    }

    /**
     * A workspace's backdrop: the picture or pattern ({@link #NO_BACKDROP} for colour only), its scaling and
     * the backdrop colour.
     */
    public record Backdrop(String path, ScaleMode mode, Color color) {
        public Backdrop {
            if (path == null || path.isBlank()) path = NO_BACKDROP;
            if (mode == null) mode = ScaleMode.FILL;
            if (color == null) color = DEFAULT_COLOR;
        }
    }

    /**
     * The backdrop of all workspaces that have none of their own: the one {@link #applyWallpaper(String,
     * ScaleMode, Color)} set last.
     */
    public static Backdrop defaultBackdrop() {
        return new Backdrop(currentWallpaperPath, currentMode, currentBgColor);
    }

    /**
     * The backdrop of workspace {@code workspace} (0 to 3) as stored now, read from the file each time: the
     * Style Manager is a program of its own, and the window manager must see what it stored.
     */
    public static Backdrop backdropOf(int workspace) {
        return backdropOf(readConfig(), workspace);
    }

    /** Whether workspace {@code workspace} has a backdrop of its own rather than the common one. */
    public static boolean hasOwnBackdrop(int workspace) {
        return hasOwnBackdrop(readConfig(), workspace);
    }

    static Backdrop backdropOf(Properties props, int workspace) {
        String prefix = workspacePrefix(workspace);
        Backdrop common = new Backdrop(
                props.getProperty("wallpaper", initialWallpaper()),
                parseMode(props.getProperty("mode"), ScaleMode.FILL),
                parseColor(props.getProperty("color"), DEFAULT_COLOR));
        if (!hasOwnBackdrop(props, workspace)) return common;
        return new Backdrop(
                props.getProperty(prefix + "wallpaper", common.path()),
                parseMode(props.getProperty(prefix + "mode"), common.mode()),
                parseColor(props.getProperty(prefix + "color"), common.color()));
    }

    static boolean hasOwnBackdrop(Properties props, int workspace) {
        return workspace >= 0 && props.getProperty(workspacePrefix(workspace) + "wallpaper") != null;
    }

    private static String workspacePrefix(int workspace) {
        return "workspace." + (workspace + 1) + ".";
    }

    private static String initialWallpaper() {
        return new File(SYSTEM_WALLPAPER).isFile() ? SYSTEM_WALLPAPER : CLASSPATH_WALLPAPER;
    }

    /**
     * Applies wallpaper and background color to all workspaces (dropping the backdrops of their own), paints
     * the X11 Root Window and saves the configuration.
     */
    public static void applyWallpaper(String path, ScaleMode mode, Color bgColor) {
        currentWallpaperPath = path;
        currentMode = (mode != null) ? mode : ScaleMode.FILL;
        if (bgColor != null) {
            currentBgColor = bgColor;
        }

        Properties props = readConfig();
        storeCommon(props);
        for (int i = 0; i < WORKSPACES; i++) {
            for (String key : new String[]{"wallpaper", "mode", "color"}) {
                props.remove(workspacePrefix(i) + key);
            }
        }
        writeConfig(props);

        // Apply directly to X11 Root Window if X11 display is accessible
        applyToX11Root(defaultBackdrop(), -1);
        notifyListeners(defaultBackdrop());
    }

    /**
     * Gives workspace {@code workspace} (0 to 3) a backdrop of its own, as CDE's Style Manager does; the
     * root window changes at once if that workspace is the current one, otherwise when the window manager
     * switches to it.
     */
    public static void applyWallpaper(int workspace, String path, ScaleMode mode, Color bgColor) {
        if (workspace < 0 || workspace >= WORKSPACES) {
            applyWallpaper(path, mode, bgColor);
            return;
        }
        Backdrop backdrop = new Backdrop(path, mode, bgColor);
        Properties props = readConfig();
        if (props.getProperty("wallpaper") == null) {
            storeCommon(props); // keep the other workspaces on what they show now
        }
        String prefix = workspacePrefix(workspace);
        props.setProperty(prefix + "wallpaper", backdrop.path());
        props.setProperty(prefix + "mode", backdrop.mode().name());
        props.setProperty(prefix + "color", hex(backdrop.color()));
        writeConfig(props);

        applyToX11Root(backdrop, workspace);
        notifyListeners(backdrop);
    }

    private static final int WORKSPACES = eu.amadeos.system.base.workspace.WorkspaceNames.COUNT;

    private static void notifyListeners(Backdrop backdrop) {
        for (WallpaperChangeListener l : LISTENERS) {
            try {
                l.onWallpaperChanged(backdrop.path(), backdrop.mode(), backdrop.color());
            } catch (Exception ignored) {}
        }
    }

    /**
     * Paints {@code backdrop} on the root window through a display connection of its own, if
     * {@code workspace} is -1 (all) or the current workspace.
     */
    private static void applyToX11Root(Backdrop backdrop, int workspace) {
        LibX11 x11 = LibX11.INSTANCE;
        Pointer display = null;
        try {
            display = x11.XOpenDisplay(null);
            if (display == null) return;

            int screen = x11.XDefaultScreen(display);
            long root = x11.XRootWindow(display, screen).longValue();
            if (workspace >= 0) {
                X11EwmhHelper helper = new X11EwmhHelper(x11, display, screen, root, false);
                if (helper.getCurrentDesktop() != workspace) return;
            }
            int screenW = x11.XDisplayWidth(display, screen);
            int screenH = x11.XDisplayHeight(display, screen);
            int depth = x11.XDefaultDepth(display, screen);
            Pointer visual = x11.XDefaultVisual(display, screen);

            applyToRootWindow(x11, display, root, screenW, screenH, depth, visual, backdrop);
        } catch (Throwable t) {
            LOGGER.fine("Could not blit wallpaper to X11 display: " + t.getMessage());
        } finally {
            if (display != null) {
                try { x11.XCloseDisplay(display); } catch (Exception ignored) {}
            }
        }
    }

    /** Paints the common backdrop on the root window. */
    public static void applyToRootWindow(
            LibX11 x11, Pointer display, long root, int screenW, int screenH, int depth, Pointer visual) {
        applyToRootWindow(x11, display, root, screenW, screenH, depth, visual, defaultBackdrop());
    }

    /** Paints {@code backdrop} on the root window, freeing the pixmap this method painted before. */
    public static void applyToRootWindow(LibX11 x11, Pointer display, long root, int screenW, int screenH,
                                         int depth, Pointer visual, Backdrop backdrop) {
        if (x11 == null || display == null || root == 0) return;
        try {
            NativeLong pixmap = renderPixmap(x11, display, root, screenW, screenH, depth, visual, backdrop);
            if (pixmap != null) {
                showPixmap(x11, display, root, pixmap);
                if (activeRootPixmap != null && activeRootPixmap.longValue() != 0) {
                    x11.XFreePixmap(display, activeRootPixmap);
                }
                activeRootPixmap = pixmap;
                x11.XFlush(display);
                LOGGER.info("Applied wallpaper to root window: " + backdrop.path() + " (mode=" + backdrop.mode() + ")");
            }
        } catch (Throwable t) {
            LOGGER.fine("Could not apply wallpaper to root window: " + t.getMessage());
        }
    }

    /**
     * Renders {@code backdrop} into a new server pixmap of the screen's size; the caller frees it. The
     * window manager keeps one per workspace, so a workspace switch only swaps pixmaps.
     *
     * @return the pixmap, or null if the server could not make one
     */
    public static NativeLong renderPixmap(LibX11 x11, Pointer display, long root, int screenW, int screenH,
                                          int depth, Pointer visual, Backdrop backdrop) {
        BufferedImage img = loadImage(backdrop.path(), backdrop.color());
        BufferedImage targetImg = renderToTarget(img, screenW, screenH, backdrop.mode(), backdrop.color());

        NativeLong pixmap = x11.XCreatePixmap(display, new NativeLong(root), screenW, screenH, depth);
        if (pixmap == null || pixmap.longValue() == 0) return null;
        Pointer gc = x11.XCreateGC(display, pixmap, new NativeLong(0), null);

        int[] pixels = new int[screenW * screenH];
        targetImg.getRGB(0, 0, screenW, screenH, pixels, 0, screenW);

        long dataSize = (long) screenW * screenH * 4L;
        Pointer nativeMem = eu.amadeos.system.base.x11.LibC.INSTANCE.malloc(dataSize);
        if (nativeMem == null) {
            x11.XFreeGC(display, gc);
            x11.XFreePixmap(display, pixmap);
            return null;
        }

        boolean imageCreated = false;
        try {
            nativeMem.write(0, pixels, 0, pixels.length);
            Pointer ximage = x11.XCreateImage(
                    display, visual, depth, LibX11.ZPixmap, 0,
                    nativeMem, screenW, screenH, 32, screenW * 4);
            if (ximage != null) {
                imageCreated = true;
                x11.XPutImage(display, pixmap, gc, ximage, 0, 0, 0, 0, screenW, screenH);
                x11.XDestroyImage(ximage);
            }
        } finally {
            if (!imageCreated) {
                eu.amadeos.system.base.x11.LibC.INSTANCE.free(nativeMem);
            }
        }
        x11.XFreeGC(display, gc);
        return pixmap;
    }

    /** Makes {@code pixmap} the root window's background and announces it to other programs. */
    public static void showPixmap(LibX11 x11, Pointer display, long root, NativeLong pixmap) {
        x11.XSetWindowBackgroundPixmap(display, new NativeLong(root), pixmap);
        x11.XClearWindow(display, new NativeLong(root));

        NativeLong xrootpmapAtom = x11.XInternAtom(display, "_XROOTPMAP_ID", 0);
        NativeLong esetrootAtom = x11.XInternAtom(display, "ESETROOT_PMAP_ID", 0);
        NativeLong xaPixmap = x11.XInternAtom(display, "PIXMAP", 0);

        Memory propMem = new Memory(NativeLong.SIZE);
        propMem.setNativeLong(0, pixmap);
        x11.XChangeProperty(display, new NativeLong(root), xrootpmapAtom, xaPixmap, 32, LibX11.PropModeReplace, propMem, 1);
        x11.XChangeProperty(display, new NativeLong(root), esetrootAtom, xaPixmap, 32, LibX11.PropModeReplace, propMem, 1);
        x11.XFlush(display);
    }

    private static File configFile() {
        return XdgDirectories.amadeosConfigDir().resolve("wallpaper.conf").toFile();
    }

    private static Properties readConfig() {
        Properties props = new Properties();
        File conf = configFile();
        if (conf.isFile()) {
            try (FileInputStream in = new FileInputStream(conf)) {
                props.load(in);
            } catch (Exception ignored) {}
        }
        return props;
    }

    private static void storeCommon(Properties props) {
        if (currentWallpaperPath != null) {
            props.setProperty("wallpaper", currentWallpaperPath);
        }
        props.setProperty("mode", currentMode.name());
        props.setProperty("color", hex(currentBgColor));
    }

    private static void writeConfig(Properties props) {
        try {
            File conf = configFile();
            conf.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(conf)) {
                props.store(out, "amadeOS Desktop Wallpaper Settings");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to persist wallpaper settings: " + e.getMessage());
        }
    }

    private static String hex(Color color) {
        return String.format("#%06X", (0xFFFFFF & color.getRGB()));
    }

    private static ScaleMode parseMode(String value, ScaleMode fallback) {
        if (value == null) return fallback;
        try {
            return ScaleMode.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Color parseColor(String value, Color fallback) {
        if (value == null || !value.startsWith("#")) return fallback;
        try {
            return Color.decode(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void loadPersistedConfig() {
        Properties props = readConfig();
        if (props.isEmpty()) return;
        Backdrop common = backdropOf(props, -1);
        currentWallpaperPath = common.path();
        currentMode = common.mode();
        currentBgColor = common.color();
    }
}
