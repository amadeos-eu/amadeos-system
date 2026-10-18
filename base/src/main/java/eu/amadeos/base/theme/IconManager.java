package eu.amadeos.base.theme;

import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.base.xdg.XdgDirectories;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Resolves desktop and panel icons compliant with the Freedesktop.org Icon Theme
 * and Icon Naming Specifications (https://specifications.freedesktop.org/icon-theme/latest/).
 *
 * The default icon theme is NsCDE ({@link DesktopTheme#DEFAULT_ICON_THEME}, package
 * {@code nscde-icon-theme}); other packs (Adwaita, Papirus, Tango, ...) installed in the standard XDG/system
 * directories and amadeOS theme folders can be chosen in the Style Manager. Names no theme provides fall
 * back to {@code /usr/share/pixmaps} and finally to icons drawn in Java2D, so the desktop stays usable
 * without any icon package.
 */
public final class IconManager {
    private static final Logger LOGGER = Logger.getLogger(IconManager.class.getName());

    public record IconThemeInfo(
            String id,
            String name,
            String comment,
            List<String> inherits,
            List<String> directories,
            File directory
    ) {}

    private static final Map<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, IconThemeInfo> THEME_INFO_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> FREEDESKTOP_NAMES;
    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("terminal", List.of("utilities-terminal", "terminal", "xterm", "gnome-terminal", "konsole"));
        map.put("file-manager", List.of("system-file-manager", "file-manager", "user-home", "folder", "nautilus", "thunar"));
        map.put("text-editor", List.of("accessories-text-editor", "text-editor", "text-x-generic", "document-edit", "gedit", "kate"));
        map.put("mail", List.of("Mail", "mail", "internet-mail", "evolution", "thunderbird", "kmail", "email"));
        map.put("multimedia", List.of("amadeos-multimedia", "applications-multimedia", "multimedia-volume-control", "audio-card", "audio-x-generic"));
        map.put("graphics", List.of("amadeos-graphics", "applications-graphics", "gnome-graphics", "package_graphics", "image-x-generic"));
        map.put("printer", List.of("printer", "document-print", "printmgr", "cups", "stock_printers", "gnome-dev-printer"));
        map.put("style", List.of("style", "preferences-desktop-theme", "preferences-desktop", "preferences-system"));
        map.put("app-manager", List.of("system-application-manager", "application", "applications-other", "gnome-applications", "application-menu"));
        map.put("help", List.of("help-browser", "system-help", "gnome-help", "khelpcenter", "help", "stock_help"));
        map.put("lock", List.of("system-lock-screen", "changes-prevent", "dialog-password", "security-high", "lock"));
        map.put("power", List.of("system-shutdown", "system-log-out", "system-reboot", "application-exit", "power"));
        map.put("shutdown", List.of("system-shutdown", "gnome-shutdown", "application-exit"));
        map.put("logout", List.of("system-log-out", "gnome-logout", "application-exit"));
        map.put("reboot", List.of("system-reboot", "view-refresh", "reload"));
        map.put("trash", List.of("user-trash", "trashcan_empty", "user-trash-empty", "user-trash-full", "trash-empty", "trash"));
        map.put("diagnostics", List.of("utilities-system-monitor", "preferences-system", "system-help", "dialog-information", "diagnostics"));
        map.put("window", List.of("window-new", "applications-other", "preferences-desktop", "preferences-desktop-theme"));
        FREEDESKTOP_NAMES = Collections.unmodifiableMap(map);
    }

    private static final String[] CONTEXT_DIRS = {
            "apps", "actions", "places", "status", "categories", "devices", "mimetypes", "emblems"
    };

    private IconManager() {}

    /**
     * Resolves an icon for the given logical key or Freedesktop icon name at requested size using active theme.
     */
    public static Icon getIcon(String key, int size) {
        return getIcon(ThemeManager.currentTheme().iconTheme(), key, size);
    }

    /**
     * Resolves an icon for the given logical key from the specified icon theme at requested size.
     */
    public static Icon getIcon(String theme, String key, int size) {
        String effectiveTheme = theme != null && !theme.isBlank() ? theme : ThemeManager.currentTheme().iconTheme();
        String cacheKey = effectiveTheme + ":" + key + ":" + size;
        return ICON_CACHE.computeIfAbsent(cacheKey, k -> loadOrGenerateIcon(effectiveTheme, key, size));
    }

    public static void clearCache() {
        ICON_CACHE.clear();
        THEME_INFO_CACHE.clear();
    }

    /**
     * Returns base directories searched for icon themes in accordance with XDG Base Directory specification.
     */
    public static List<File> getIconBaseDirectories() {
        List<File> list = new ArrayList<>();
        String userHome = System.getProperty("user.home", "/tmp");

        // 1. $XDG_DATA_HOME/icons (default ~/.local/share/icons)
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            list.add(new File(xdgDataHome, "icons"));
        } else {
            list.add(new File(userHome, ".local/share/icons"));
        }

        // 2. Legacy user icon directory ~/.icons
        list.add(new File(userHome, ".icons"));

        // 3. User amadeOS themes $XDG_CONFIG_HOME/amadeos/themes (default ~/.config/amadeos/themes)
        list.add(XdgDirectories.amadeosConfigDir().resolve("themes").toFile());

        // 4. $XDG_DATA_DIRS/icons (defaults to /usr/local/share/icons and /usr/share/icons)
        String xdgDataDirs = System.getenv("XDG_DATA_DIRS");
        if (xdgDataDirs != null && !xdgDataDirs.isBlank()) {
            for (String dir : xdgDataDirs.split(":")) {
                if (!dir.isBlank()) {
                    list.add(new File(dir, "icons"));
                }
            }
        } else {
            list.add(new File("/usr/local/share/icons"));
            list.add(new File("/usr/share/icons"));
        }

        // 5. System amadeOS themes
        list.add(new File("/etc/amadeos/themes"));

        return list;
    }

    /**
     * Discovers the icon themes installed across standard XDG and system paths: folders with an
     * {@code index.theme} or an {@code icons/} sub-folder. hicolor is only the common fallback and not
     * listed; the default theme (NsCDE) comes first when installed.
     */
    public static List<IconThemeInfo> availableIconThemes() {
        Map<String, IconThemeInfo> found = new LinkedHashMap<>();

        for (File base : getIconBaseDirectories()) {
            if (!base.isDirectory()) continue;
            File[] subs = base.listFiles();
            if (subs == null) continue;
            java.util.Arrays.sort(subs);
            for (File sub : subs) {
                if (sub.isDirectory() && !sub.isHidden() && isIconTheme(sub)) {
                    String themeId = sub.getName();
                    if (!found.containsKey(themeId)) {
                        IconThemeInfo info = getThemeInfo(themeId, sub);
                        if (info != null) {
                            found.put(themeId, info);
                        }
                    }
                }
            }
        }

        List<IconThemeInfo> list = new ArrayList<>(found.values());
        list.sort(java.util.Comparator.comparing(
                (IconThemeInfo t) -> !t.id().equals(DesktopTheme.DEFAULT_ICON_THEME)));
        return Collections.unmodifiableList(list);
    }

    private static boolean isIconTheme(File dir) {
        if ("hicolor".equalsIgnoreCase(dir.getName())) return false;
        return new File(dir, "index.theme").isFile() || new File(dir, "icons").isDirectory();
    }

    private static IconThemeInfo getThemeInfo(String themeId, File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        return THEME_INFO_CACHE.computeIfAbsent(themeId + ":" + dir.getAbsolutePath(), k -> parseThemeInfo(themeId, dir));
    }

    private static IconThemeInfo parseThemeInfo(String themeId, File themeDir) {
        File indexFile = new File(themeDir, "index.theme");
        String name = themeId;
        String comment = "";
        List<String> inherits = new ArrayList<>();
        List<String> directories = new ArrayList<>();

        if (indexFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(indexFile), StandardCharsets.UTF_8))) {
                String line;
                boolean inThemeSection = false;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("[") && line.endsWith("]")) {
                        inThemeSection = line.equalsIgnoreCase("[Icon Theme]");
                        continue;
                    }
                    if (!inThemeSection || line.startsWith("#") || !line.contains("=")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if ("Name".equalsIgnoreCase(key)) {
                        name = val;
                    } else if ("Comment".equalsIgnoreCase(key)) {
                        comment = val;
                    } else if ("Inherits".equalsIgnoreCase(key)) {
                        for (String part : val.split(",")) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) inherits.add(trimmed);
                        }
                    } else if ("Directories".equalsIgnoreCase(key)) {
                        for (String part : val.split(",")) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) directories.add(trimmed);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Freedesktop specification mandates fallback to hicolor
        if (!"hicolor".equalsIgnoreCase(themeId) && !inherits.contains("hicolor")) {
            inherits.add("hicolor");
        }

        return new IconThemeInfo(themeId, name, comment, inherits, directories, themeDir);
    }

    private static Icon loadOrGenerateIcon(String iconTheme, String key, int size) {
        if (key != null && (key.startsWith("/") || key.startsWith("."))) {
            File directFile = new File(key);
            if (directFile.isFile()) {
                try {
                    BufferedImage pi = ImageIO.read(directFile);
                    if (pi != null) return scaleIcon(pi, size);
                } catch (Exception ignored) {}
            }
            File directPng = new File(key + ".png");
            if (directPng.isFile()) {
                try {
                    BufferedImage pi = ImageIO.read(directPng);
                    if (pi != null) return scaleIcon(pi, size);
                } catch (Exception ignored) {}
            }
        }

        String normalizedKey = key;
        if (normalizedKey != null && (normalizedKey.endsWith(".png") || normalizedKey.endsWith(".xpm") || normalizedKey.endsWith(".svg"))) {
            normalizedKey = normalizedKey.substring(0, normalizedKey.lastIndexOf('.'));
        }

        List<String> candidates = new ArrayList<>();
        List<String> fdoNames = FREEDESKTOP_NAMES.get(key != null ? key.toLowerCase() : "");
        if (fdoNames != null) {
            candidates.addAll(fdoNames);
        }
        if (normalizedKey != null && !candidates.contains(normalizedKey)) {
            candidates.add(normalizedKey);
        }
        if (key != null && !candidates.contains(key)) {
            candidates.add(key);
        }

        Set<String> visitedThemes = new LinkedHashSet<>();

        // A theme that is not installed (e.g. the former built-in "classic") falls back to the default one.
        if (findThemeDirectory(iconTheme) == null) {
            iconTheme = DesktopTheme.DEFAULT_ICON_THEME;
        }

        // 1. Resolve through Freedesktop theme hierarchy
        BufferedImage img = resolveFreedesktopThemeHierarchy(iconTheme, candidates, size, visitedThemes);
        if (img != null) {
            return scaleIcon(img, size);
        }

        // 2. Check unthemed pixmaps /usr/share/pixmaps/
        File pixmapsDir = new File("/usr/share/pixmaps");
        if (pixmapsDir.isDirectory()) {
            for (String cand : candidates) {
                File file = new File(pixmapsDir, cand + ".png");
                if (file.isFile()) {
                    try {
                        BufferedImage pi = ImageIO.read(file);
                        if (pi != null) return scaleIcon(pi, size);
                    } catch (Exception ignored) {}
                }
                File fileXpm = new File(pixmapsDir, cand + ".xpm");
                if (fileXpm.isFile()) {
                    try {
                        BufferedImage pi = ImageIO.read(fileXpm);
                        if (pi != null) return scaleIcon(pi, size);
                    } catch (Exception ignored) {}
                }
            }
        }

        // 3. Fallback to programmatic pixel/vector generator
        BufferedImage generated = generatePixmaps(key, size);
        return new ImageIcon(generated);
    }

    private static BufferedImage resolveFreedesktopThemeHierarchy(
            String themeId, List<String> candidates, int size, Set<String> visitedThemes) {
        if (themeId == null || themeId.isBlank() || visitedThemes.contains(themeId)) {
            return null;
        }
        visitedThemes.add(themeId);

        // Find theme directory on disk
        File themeDir = findThemeDirectory(themeId);
        IconThemeInfo info = (themeDir != null) ? getThemeInfo(themeId, themeDir) : null;

        if (themeDir != null) {
            BufferedImage match = searchThemeDirectory(themeDir, info, candidates, size);
            if (match != null) {
                return match;
            }
        }

        // Search inherited themes
        if (info != null && info.inherits() != null) {
            for (String parentTheme : info.inherits()) {
                BufferedImage match = resolveFreedesktopThemeHierarchy(parentTheme, candidates, size, visitedThemes);
                if (match != null) {
                    return match;
                }
            }
        }

        // Ensure hicolor is checked before failing
        if (!"hicolor".equalsIgnoreCase(themeId) && !visitedThemes.contains("hicolor")) {
            return resolveFreedesktopThemeHierarchy("hicolor", candidates, size, visitedThemes);
        }

        return null;
    }

    private static File findThemeDirectory(String themeId) {
        for (File base : getIconBaseDirectories()) {
            if (!base.isDirectory()) continue;
            File dir = new File(base, themeId);
            if (dir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private static BufferedImage searchThemeDirectory(
            File themeDir, IconThemeInfo info, List<String> candidates, int size) {
        String sizeStr = String.valueOf(size);
        String sizeXStr = size + "x" + size;

        List<String> sortedDirs = new ArrayList<>();
        if (info != null && !info.directories().isEmpty()) {
            sortedDirs.addAll(info.directories());
            sortedDirs.sort((a, b) -> {
                int sa = extractSize(a);
                int sb = extractSize(b);
                int distA = Math.abs(sa - size);
                int distB = Math.abs(sb - size);
                if (distA != distB) {
                    return Integer.compare(distA, distB);
                }
                // Prefer 'apps' over 'actions' or 'categories' for the same size
                boolean aApp = a.contains("apps");
                boolean bApp = b.contains("apps");
                if (aApp && !bApp) return -1;
                if (!aApp && bApp) return 1;
                return 0;
            });
        }

        // Freedesktop specification: search for candidate[0] across directories, then candidate[1], etc.
        for (String cand : candidates) {
            // 1. Check directories specified in index.theme
            for (String sub : sortedDirs) {
                BufferedImage img = searchFileInDir(new File(themeDir, sub), cand);
                if (img != null) return img;
            }

            // 2. Check standard Freedesktop layout: <theme>/<size>x<size>/<context>/<candidate>.png
            for (String ctx : CONTEXT_DIRS) {
                BufferedImage img = searchFileInDir(new File(themeDir, sizeXStr + "/" + ctx), cand);
                if (img != null) return img;

                img = searchFileInDir(new File(themeDir, ctx + "/" + sizeStr), cand);
                if (img != null) return img;
            }

            // 3. Check flat icons subdirectory: <theme>/icons/<candidate>.png
            File flatIcons = new File(themeDir, "icons");
            BufferedImage img = searchFileInDir(flatIcons, cand);
            if (img != null) return img;

            // 4. Check root theme folder: <theme>/<candidate>.png
            img = searchFileInDir(themeDir, cand);
            if (img != null) return img;
        }

        return null;
    }

    private static BufferedImage searchFileInDir(File dir, String cand) {
        if (!dir.isDirectory()) return null;
        File f = new File(dir, cand + ".png");
        if (f.isFile()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) return img;
            } catch (Exception ignored) {}
        }
        File[] files = dir.listFiles();
        if (files != null) {
            String target = cand + ".png";
            for (File file : files) {
                if (file.getName().equalsIgnoreCase(target)) {
                    try {
                        BufferedImage img = ImageIO.read(file);
                        if (img != null) return img;
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private static int extractSize(String path) {
        if (path == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(path);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static Icon scaleIcon(BufferedImage img, int size) {
        if (img.getWidth() == size && img.getHeight() == size) {
            return new ImageIcon(img);
        }
        Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /**
     * Programmatic vector/pixel fallback for minimal systems lacking icon packages.
     */
    public static BufferedImage generatePixmaps(String key, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float scale = size / 24.0f;
        g.scale(scale, scale);

        switch (key.toLowerCase()) {
            case "file-manager", "files", "folder" -> {
                // Manila folder
                g.setColor(new Color(0xD8, 0x9E, 0x3A));
                g.fillRect(3, 4, 8, 4);
                g.setColor(new Color(0xE8, 0xB6, 0x54));
                g.fillRect(3, 6, 18, 14);
                g.setColor(new Color(0xFF, 0xCE, 0x6E));
                g.fillRect(2, 9, 20, 11);
                g.setColor(new Color(0x60, 0x40, 0x10));
                g.drawRect(2, 9, 19, 11);
                g.drawLine(3, 4, 11, 4);
                g.drawLine(11, 4, 13, 6);
                g.drawLine(13, 6, 20, 6);
                g.drawLine(20, 6, 20, 9);
            }
            case "text-editor", "editor", "doc" -> {
                // Document notepad with ruled lines
                g.setColor(Color.WHITE);
                g.fillRect(4, 3, 14, 18);
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.drawRect(4, 3, 14, 18);
                g.setColor(new Color(0xD0, 0xD0, 0xD0));
                g.fillPolygon(new int[]{14, 18, 14}, new int[]{3, 7, 7}, 3);
                g.setColor(new Color(0x50, 0x70, 0xA0));
                g.drawLine(7, 8, 13, 8);
                g.drawLine(7, 11, 15, 11);
                g.drawLine(7, 14, 15, 14);
                g.drawLine(7, 17, 12, 17);
                g.setColor(new Color(0xDD, 0x88, 0x22));
                g.fillRect(16, 12, 4, 8);
                g.setColor(Color.BLACK);
                g.drawRect(16, 12, 4, 8);
            }
            case "terminal", "term", "xterm" -> {
                // CRT monitor with phosphor green prompt
                g.setColor(new Color(0x38, 0x38, 0x38));
                g.fillRect(2, 3, 20, 15);
                g.setColor(Color.WHITE);
                g.drawRect(2, 3, 19, 14);
                g.setColor(Color.BLACK);
                g.fillRect(4, 5, 16, 11);
                g.setColor(new Color(0x60, 0x60, 0x60));
                g.fillRect(10, 18, 4, 2);
                g.fillRect(7, 20, 10, 2);
                g.setColor(new Color(0x33, 0xFF, 0x55));
                g.drawString(">", 5, 14);
                g.fillRect(11, 13, 4, 1);
            }
            case "lock" -> {
                // Brass padlock
                g.setColor(new Color(0xBB, 0xBB, 0xBB));
                g.setStroke(new BasicStroke(2.0f));
                g.drawArc(7, 3, 10, 11, 0, 180);
                g.setStroke(new BasicStroke(1.0f));
                g.setColor(new Color(0xD8, 0xA8, 0x30));
                g.fillRect(5, 9, 14, 12);
                g.setColor(new Color(0xF8, 0xC8, 0x50));
                g.drawLine(6, 10, 17, 10);
                g.drawLine(6, 10, 6, 19);
                g.setColor(new Color(0x70, 0x50, 0x10));
                g.drawRect(5, 9, 13, 11);
                g.setColor(Color.BLACK);
                g.fillOval(11, 12, 2, 2);
                g.fillRect(11, 14, 2, 4);
            }
            case "power", "exit", "shutdown" -> {
                // Power button
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.fillOval(3, 3, 18, 18);
                g.setColor(new Color(0xD0, 0x30, 0x30));
                g.setStroke(new BasicStroke(2.0f));
                g.drawArc(6, 6, 12, 12, 135, 270);
                g.drawLine(12, 5, 12, 12);
            }
            case "logout" -> {
                // Open door with an arrow leaving through it
                g.setColor(new Color(0x60, 0x60, 0x60));
                g.fillRect(4, 3, 10, 18);
                g.setColor(new Color(0xB0, 0x80, 0x40));
                g.fillPolygon(new int[]{4, 10, 10, 4}, new int[]{3, 5, 21, 21}, 4);
                g.setColor(Color.BLACK);
                g.drawRect(4, 3, 10, 18);
                g.setColor(new Color(0x30, 0x90, 0x30));
                g.setStroke(new BasicStroke(2.0f));
                g.drawLine(11, 12, 20, 12);
                g.drawLine(17, 9, 20, 12);
                g.drawLine(17, 15, 20, 12);
            }
            case "reboot" -> {
                // Circular arrow
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.fillOval(3, 3, 18, 18);
                g.setColor(new Color(0x40, 0xB0, 0x40));
                g.setStroke(new BasicStroke(2.0f));
                g.drawArc(6, 6, 12, 12, 60, 300);
                g.setStroke(new BasicStroke(1.0f));
                g.fillPolygon(new int[]{13, 19, 14}, new int[]{4, 7, 10}, 3);
            }
            case "trash", "waste" -> {
                // Wastebasket
                g.setColor(new Color(0xA0, 0xA0, 0xA0));
                g.fillRect(6, 7, 12, 14);
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.drawRect(6, 7, 12, 14);
                g.setColor(new Color(0xD0, 0xD0, 0xD0));
                g.fillRect(4, 5, 16, 3);
                g.setColor(Color.BLACK);
                g.drawRect(4, 5, 16, 3);
                g.setColor(new Color(0x60, 0x60, 0x60));
                g.drawLine(9, 9, 9, 19);
                g.drawLine(12, 9, 12, 19);
                g.drawLine(15, 9, 15, 19);
            }
            case "mail", "email" -> {
                // In-tray with mail envelope
                g.setColor(new Color(0xB0, 0xB0, 0xB0));
                g.fillRect(4, 13, 16, 8);
                g.setColor(new Color(0x60, 0x60, 0x60));
                g.drawRect(4, 13, 16, 8);
                // Mail envelope in tray
                g.setColor(Color.WHITE);
                g.fillRect(6, 6, 12, 9);
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.drawRect(6, 6, 12, 9);
                // Envelope flap
                g.setColor(new Color(0x80, 0x80, 0x80));
                g.drawLine(6, 6, 12, 11);
                g.drawLine(18, 6, 12, 11);
                // In-tray front lip notch
                g.setColor(new Color(0x90, 0x90, 0x90));
                g.fillRect(8, 13, 8, 3);
            }
            case "printer", "print" -> {
                // Beige desktop printer
                g.setColor(new Color(0xDD, 0xDC, 0xD6));
                g.fillRect(4, 9, 16, 9);
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.drawRect(4, 9, 16, 9);
                // Paper feed tray at top
                g.setColor(Color.WHITE);
                g.fillRect(7, 4, 10, 6);
                g.setColor(new Color(0x60, 0x60, 0x60));
                g.drawRect(7, 4, 10, 6);
                // Paper output slot
                g.setColor(new Color(0x20, 0x20, 0x20));
                g.fillRect(6, 15, 12, 2);
                // Output paper
                g.setColor(Color.WHITE);
                g.fillRect(8, 16, 8, 4);
                g.setColor(new Color(0x50, 0x50, 0x50));
                g.drawRect(8, 16, 8, 4);
                // Status LED
                g.setColor(new Color(0x20, 0xC0, 0x40));
                g.fillRect(17, 11, 2, 2);
            }
            case "style", "preferences" -> {
                // Color palette & brush
                g.setColor(new Color(0xE8, 0xD0, 0xA8));
                g.fillOval(3, 4, 18, 16);
                g.setColor(new Color(0x70, 0x50, 0x30));
                g.drawOval(3, 4, 18, 16);
                // Paint dots
                g.setColor(new Color(0xE0, 0x20, 0x20));
                g.fillOval(6, 7, 3, 3);
                g.setColor(new Color(0x20, 0x50, 0xE0));
                g.fillOval(11, 5, 3, 3);
                g.setColor(new Color(0xE0, 0xC0, 0x20));
                g.fillOval(15, 8, 3, 3);
                g.setColor(new Color(0x20, 0xA0, 0x30));
                g.fillOval(8, 12, 3, 3);
                // Palette thumb hole
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.fillOval(14, 13, 3, 4);
            }
            case "app-manager", "applications" -> {
                // Pencil-holder box with ruler and pencil
                g.setColor(new Color(0xB8, 0xB8, 0xB8));
                g.fillRect(5, 10, 14, 11);
                g.setColor(new Color(0x50, 0x50, 0x50));
                g.drawRect(5, 10, 14, 11);
                // Ruler
                g.setColor(new Color(0xE0, 0xD0, 0x80));
                g.fillRect(7, 3, 3, 8);
                g.setColor(Color.BLACK);
                g.drawRect(7, 3, 3, 8);
                g.drawLine(7, 5, 8, 5);
                g.drawLine(7, 7, 8, 7);
                g.drawLine(7, 9, 8, 9);
                // Pencil
                g.setColor(new Color(0xE0, 0x40, 0x40));
                g.fillRect(14, 2, 3, 9);
                g.setColor(Color.BLACK);
                g.drawRect(14, 2, 3, 9);
                // Calculator / card
                g.setColor(new Color(0x40, 0x60, 0x80));
                g.fillRect(11, 5, 3, 6);
            }
            case "help", "system-help" -> {
                // Books on shelf with ?
                g.setColor(new Color(0x30, 0x60, 0x90));
                g.fillRect(4, 5, 4, 15);
                g.setColor(new Color(0x80, 0x30, 0x30));
                g.fillRect(9, 3, 5, 17);
                g.setColor(new Color(0x30, 0x70, 0x50));
                g.fillRect(15, 6, 4, 14);
                g.setColor(new Color(0x20, 0x20, 0x20));
                g.drawRect(4, 5, 4, 15);
                g.drawRect(9, 3, 5, 17);
                g.drawRect(15, 6, 4, 14);
                // White ? on middle book
                g.setColor(Color.WHITE);
                g.drawString("?", 10, 15);
            }
            case "diagnostics", "tools" -> {
                // Red toolbox
                g.setColor(new Color(0xB8, 0x38, 0x28));
                g.fillRect(4, 8, 16, 12);
                g.setColor(Color.BLACK);
                g.drawRect(4, 8, 16, 12);
                g.setColor(new Color(0x50, 0x50, 0x50));
                g.drawRect(9, 4, 6, 4);
                g.setColor(new Color(0xE0, 0xE0, 0xE0));
                g.fillRect(10, 12, 4, 3);
                g.setColor(Color.BLACK);
                g.drawRect(10, 12, 4, 3);
            }
            default -> {
                // Workstation window
                g.setColor(new Color(0x60, 0x80, 0xA0));
                g.fillRect(3, 3, 18, 18);
                g.setColor(new Color(0xC0, 0xC0, 0xC0));
                g.fillRect(4, 4, 16, 4);
                g.setColor(Color.WHITE);
                g.fillRect(4, 9, 16, 11);
                g.setColor(Color.BLACK);
                g.drawRect(3, 3, 17, 17);
            }
        }

        g.dispose();
        return img;
    }
}
