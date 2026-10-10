package org.datazuul.amadeos.wm.theme;

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
import org.datazuul.amadeos.wm.x11.LibX11;

/**
 * Manages desktop wallpapers and background rendering on the X11 Root Window.
 * Supports Fill, Fit, Stretch, Tile, and Center modes, solid color backdrops,
 * persistence to ~/.config/amadeos/wallpaper.conf, and Freedesktop/X11 root pixmap export.
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
    }

    public record WallpaperItem(String name, String path, boolean isClasspath) {
        @Override
        public String toString() { return name; }
    }

    @FunctionalInterface
    public interface WallpaperChangeListener {
        void onWallpaperChanged(String path, ScaleMode mode, Color bgColor);
    }

    private static final List<WallpaperChangeListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile String currentWallpaperPath = null;
    private static volatile ScaleMode currentMode = ScaleMode.FILL;
    private static volatile Color currentBgColor = new Color(0x4A, 0x69, 0x84); // Classic UNIX Slate

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

        // 1. Classpath default wallpapers
        list.add(new WallpaperItem("amadeOS Default (Classpath)", "classpath:/wallpapers/default.png", true));

        // 2. System backgrounds: /usr/share/backgrounds/
        File sysBg = new File("/usr/share/backgrounds");
        scanDirectoryForWallpapers(sysBg, list);

        // 3. User backgrounds: ~/.local/share/backgrounds/ and ~/Pictures/
        String home = System.getProperty("user.home", "/tmp");
        scanDirectoryForWallpapers(new File(home, ".local/share/backgrounds"), list);
        scanDirectoryForWallpapers(new File(home, "Pictures/Wallpapers"), list);
        scanDirectoryForWallpapers(new File(home, "Pictures"), list);

        // 4. Custom amadeOS config wallpapers
        scanDirectoryForWallpapers(new File(home, ".config/amadeos/wallpapers"), list);

        return Collections.unmodifiableList(list);
    }

    private static void scanDirectoryForWallpapers(File dir, List<WallpaperItem> out) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && !f.isHidden()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    out.add(new WallpaperItem(f.getName(), f.getAbsolutePath(), false));
                }
            } else if (f.isDirectory() && !f.isHidden() && ("amadeos".equalsIgnoreCase(f.getName()) || "dazeos".equalsIgnoreCase(f.getName()) || "backgrounds".equalsIgnoreCase(dir.getName()))) {
                scanDirectoryForWallpapers(f, out);
            }
        }
    }

    /**
     * Loads a wallpaper image from either a filesystem path or a classpath resource.
     */
    public static BufferedImage loadImage(String path) {
        if (path == null || path.isBlank() || "none".equalsIgnoreCase(path)) {
            return null;
        }
        try {
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
     * Applies wallpaper and background color to the X11 Root Window and saves configuration.
     */
    public static void applyWallpaper(String path, ScaleMode mode, Color bgColor) {
        currentWallpaperPath = path;
        currentMode = (mode != null) ? mode : ScaleMode.FILL;
        if (bgColor != null) {
            currentBgColor = bgColor;
        }

        persistConfig();

        // Apply directly to X11 Root Window if X11 display is accessible
        applyToX11Root();

        // Notify in-process listeners
        for (WallpaperChangeListener l : LISTENERS) {
            try {
                l.onWallpaperChanged(currentWallpaperPath, currentMode, currentBgColor);
            } catch (Exception ignored) {}
        }
    }

    private static void applyToX11Root() {
        LibX11 x11 = LibX11.INSTANCE;
        Pointer display = null;
        try {
            display = x11.XOpenDisplay(null);
            if (display == null) return;

            int screen = x11.XDefaultScreen(display);
            long root = x11.XRootWindow(display, screen).longValue();
            int screenW = x11.XDisplayWidth(display, screen);
            int screenH = x11.XDisplayHeight(display, screen);
            int depth = x11.XDefaultDepth(display, screen);
            Pointer visual = x11.XDefaultVisual(display, screen);

            applyToRootWindow(x11, display, root, screenW, screenH, depth, visual);
        } catch (Throwable t) {
            LOGGER.fine("Could not blit wallpaper to X11 display: " + t.getMessage());
        } finally {
            if (display != null) {
                try { x11.XCloseDisplay(display); } catch (Exception ignored) {}
            }
        }
    }

    public static void applyToRootWindow(
            LibX11 x11, Pointer display, long root, int screenW, int screenH, int depth, Pointer visual) {
        if (x11 == null || display == null || root == 0) return;
        try {
            BufferedImage img = loadImage(currentWallpaperPath);
            BufferedImage targetImg = renderToTarget(img, screenW, screenH, currentMode, currentBgColor);

            NativeLong pixmap = x11.XCreatePixmap(display, new NativeLong(root), screenW, screenH, depth);
            if (pixmap != null && pixmap.longValue() != 0) {
                Pointer gc = x11.XCreateGC(display, pixmap, new NativeLong(0), null);

                int[] pixels = new int[screenW * screenH];
                targetImg.getRGB(0, 0, screenW, screenH, pixels, 0, screenW);

                long dataSize = (long) screenW * screenH * 4L;
                Pointer nativeMem = org.datazuul.amadeos.wm.x11.LibC.INSTANCE.malloc(dataSize);
                if (nativeMem == null) {
                    x11.XFreeGC(display, gc);
                    return;
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
                        org.datazuul.amadeos.wm.x11.LibC.INSTANCE.free(nativeMem);
                    }
                }

                x11.XFreeGC(display, gc);

                x11.XSetWindowBackgroundPixmap(display, new NativeLong(root), pixmap);
                x11.XClearWindow(display, new NativeLong(root));

                NativeLong xrootpmapAtom = x11.XInternAtom(display, "_XROOTPMAP_ID", 0);
                NativeLong esetrootAtom = x11.XInternAtom(display, "ESETROOT_PMAP_ID", 0);
                NativeLong xaPixmap = x11.XInternAtom(display, "PIXMAP", 0);

                Memory propMem = new Memory(NativeLong.SIZE);
                propMem.setNativeLong(0, pixmap);
                x11.XChangeProperty(display, new NativeLong(root), xrootpmapAtom, xaPixmap, 32, LibX11.PropModeReplace, propMem, 1);
                x11.XChangeProperty(display, new NativeLong(root), esetrootAtom, xaPixmap, 32, LibX11.PropModeReplace, propMem, 1);

                if (activeRootPixmap != null && activeRootPixmap.longValue() != 0) {
                    x11.XFreePixmap(display, activeRootPixmap);
                }
                activeRootPixmap = pixmap;
                x11.XFlush(display);
                LOGGER.info("Applied wallpaper to root window: " + currentWallpaperPath + " (mode=" + currentMode + ")");
            }
        } catch (Throwable t) {
            LOGGER.fine("Could not apply wallpaper to root window: " + t.getMessage());
        }
    }

    private static void persistConfig() {
        try {
            String home = System.getProperty("user.home", "/tmp");
            File confDir = new File(home, ".config/amadeos");
            confDir.mkdirs();
            File conf = new File(confDir, "wallpaper.conf");

            Properties props = new Properties();
            if (currentWallpaperPath != null) {
                props.setProperty("wallpaper", currentWallpaperPath);
            }
            props.setProperty("mode", currentMode.name());
            props.setProperty("color", String.format("#%06X", (0xFFFFFF & currentBgColor.getRGB())));

            try (FileOutputStream out = new FileOutputStream(conf)) {
                props.store(out, "amadeOS Desktop Wallpaper Settings");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to persist wallpaper settings: " + e.getMessage());
        }
    }

    private static void loadPersistedConfig() {
        try {
            String home = System.getProperty("user.home", "/tmp");
            File conf = new File(home, ".config/amadeos/wallpaper.conf");
            if (conf.isFile()) {
                Properties props = new Properties();
                try (FileInputStream in = new FileInputStream(conf)) {
                    props.load(in);
                }
                String w = props.getProperty("wallpaper");
                if (w != null && !w.isBlank()) {
                    currentWallpaperPath = w.trim();
                }
                String m = props.getProperty("mode");
                if (m != null) {
                    try { currentMode = ScaleMode.valueOf(m.trim().toUpperCase()); } catch (Exception ignored) {}
                }
                String c = props.getProperty("color");
                if (c != null && c.startsWith("#")) {
                    try { currentBgColor = Color.decode(c); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    public static void generateDefaultWallpaper(File targetFile) {
        if (targetFile.exists()) return;
        targetFile.getParentFile().mkdirs();
        int w = 1920;
        int h = 1080;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Classic UNIX CDE slate blue gradient
        Color top = new Color(0x3B, 0x54, 0x6E);
        Color bottom = new Color(0x22, 0x33, 0x44);
        java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, top, 0, h, bottom);
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);

        // Subtle geometric grid pattern
        g.setColor(new Color(255, 255, 255, 14));
        for (int x = 0; x < w; x += 32) {
            g.drawLine(x, 0, x, h);
        }
        for (int y = 0; y < h; y += 32) {
            g.drawLine(0, y, w, y);
        }

        // Center workstation badge
        g.setColor(new Color(255, 255, 255, 25));
        g.fillRoundRect(w / 2 - 130, h / 2 - 45, 260, 90, 16, 16);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(w / 2 - 130, h / 2 - 45, 260, 90, 16, 16);

        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 36));
        g.setColor(new Color(255, 255, 255, 200));
        g.drawString("amadeOS", w / 2 - 68, h / 2 + 13);

        g.dispose();
        try {
            ImageIO.write(img, "png", targetFile);
            LOGGER.info("Generated default wallpaper: " + targetFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warning("Failed to save wallpaper " + targetFile + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        generateDefaultWallpaper(new File("src/main/resources/wallpapers/default.png"));
        generateDefaultWallpaper(new File("config/includes.chroot/usr/share/backgrounds/amadeos/wallpaper.png"));
    }
}
