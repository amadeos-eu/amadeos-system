package org.datazuul.amadeos.wm.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
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
 * Supports third-party icon packs (Adwaita, Papirus, Tango, elementary-xfce, etc.)
 * installed in standard XDG/system directories, custom amadeOS themes, classpath themes,
 * and built-in Java2D vector/pixel fallbacks.
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

    private static final Map<String, List<String>> FREEDESKTOP_NAMES = Map.of(
            "terminal", List.of("utilities-terminal", "terminal", "xterm", "gnome-terminal", "konsole"),
            "file-manager", List.of("system-file-manager", "file-manager", "user-home", "folder", "nautilus", "thunar"),
            "text-editor", List.of("accessories-text-editor", "text-editor", "text-x-generic", "document-edit", "gedit", "kate"),
            "lock", List.of("system-lock-screen", "changes-prevent", "dialog-password", "security-high", "lock"),
            "power", List.of("system-shutdown", "system-log-out", "system-reboot", "application-exit", "power"),
            "trash", List.of("user-trash", "user-trash-empty", "user-trash-full", "trash-empty", "trashcan_empty", "trash"),
            "diagnostics", List.of("utilities-system-monitor", "preferences-system", "system-help", "dialog-information", "diagnostics"),
            "window", List.of("window-new", "applications-other", "preferences-desktop", "preferences-desktop-theme")
    );

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

        // 3. User amadeOS themes ~/.config/amadeos/themes
        list.add(new File(userHome, ".config/amadeos/themes"));

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
     * Discovers all available icon themes installed across standard XDG and system paths.
     */
    public static List<IconThemeInfo> availableIconThemes() {
        Map<String, IconThemeInfo> found = new LinkedHashMap<>();

        // Always include built-in classic
        found.put("classic", new IconThemeInfo("classic", "Classic amadeOS / CDE", "Built-in authentic Motif/CDE icons", List.of("hicolor"), List.of(), null));

        for (File base : getIconBaseDirectories()) {
            if (!base.isDirectory()) continue;
            File[] subs = base.listFiles();
            if (subs == null) continue;
            for (File sub : subs) {
                if (sub.isDirectory() && !sub.isHidden()) {
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

        return Collections.unmodifiableList(new ArrayList<>(found.values()));
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

        // 3. Check classpath resources: /themes/<iconTheme>/icons/<key>.png or /themes/classic/icons/<key>.png
        String[] cpPaths = {
                "/themes/" + iconTheme + "/icons/" + key + ".png",
                "/themes/classic/icons/" + key + ".png"
        };
        for (String cp : cpPaths) {
            try (InputStream in = IconManager.class.getResourceAsStream(cp)) {
                if (in != null) {
                    BufferedImage cpImg = ImageIO.read(in);
                    if (cpImg != null) return scaleIcon(cpImg, size);
                }
            } catch (Exception ignored) {}
        }

        // 4. Fallback to programmatic pixel/vector generator
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

        // 1. Check directories specified in index.theme
        if (info != null && !info.directories().isEmpty()) {
            // First pass: directories matching requested size
            for (String sub : info.directories()) {
                if (sub.contains(sizeStr) || sub.contains(sizeXStr)) {
                    BufferedImage img = searchSubDirForCandidates(new File(themeDir, sub), candidates);
                    if (img != null) return img;
                }
            }
            // Second pass: directories sorted by closeness to requested size (e.g. 32, 24, 48, 16)
            List<String> sortedDirs = new ArrayList<>(info.directories());
            sortedDirs.sort((a, b) -> {
                int sa = extractSize(a);
                int sb = extractSize(b);
                return Integer.compare(Math.abs(sa - size), Math.abs(sb - size));
            });
            for (String sub : sortedDirs) {
                BufferedImage img = searchSubDirForCandidates(new File(themeDir, sub), candidates);
                if (img != null) return img;
            }
        }

        // 2. Check standard Freedesktop layout: <theme>/<size>x<size>/<context>/<candidate>.png
        for (String ctx : CONTEXT_DIRS) {
            File d1 = new File(themeDir, sizeXStr + "/" + ctx);
            BufferedImage img = searchSubDirForCandidates(d1, candidates);
            if (img != null) return img;

            File d2 = new File(themeDir, ctx + "/" + sizeStr);
            img = searchSubDirForCandidates(d2, candidates);
            if (img != null) return img;
        }

        // 3. Check flat icons subdirectory: <theme>/icons/<candidate>.png
        File flatIcons = new File(themeDir, "icons");
        BufferedImage img = searchSubDirForCandidates(flatIcons, candidates);
        if (img != null) return img;

        // 4. Check root theme folder: <theme>/<candidate>.png
        return searchSubDirForCandidates(themeDir, candidates);
    }

    private static BufferedImage searchSubDirForCandidates(File dir, List<String> candidates) {
        if (!dir.isDirectory()) return null;
        for (String cand : candidates) {
            File f = new File(dir, cand + ".png");
            if (f.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) return img;
                } catch (Exception ignored) {}
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
            case "power", "exit" -> {
                // Power button
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.fillOval(3, 3, 18, 18);
                g.setColor(new Color(0xD0, 0x30, 0x30));
                g.setStroke(new BasicStroke(2.0f));
                g.drawArc(6, 6, 12, 12, 135, 270);
                g.drawLine(12, 5, 12, 12);
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

    public static void saveDefaultIconsToDirectory(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String[] keys = {"file-manager", "text-editor", "terminal", "lock", "power", "trash", "diagnostics", "window"};
        for (String key : keys) {
            File target = new File(dir, key + ".png");
            if (!target.exists()) {
                try {
                    BufferedImage img = generatePixmaps(key, 32);
                    ImageIO.write(img, "png", target);
                    LOGGER.info("Generated default theme icon: " + target.getAbsolutePath());
                } catch (Exception e) {
                    LOGGER.warning("Failed to save icon " + key + ": " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            for (String dir : args) {
                saveDefaultIconsToDirectory(new File(dir));
            }
        } else {
            saveDefaultIconsToDirectory(new File("src/main/resources/themes/classic/icons"));
            saveDefaultIconsToDirectory(new File("config/includes.chroot/etc/amadeos/themes/classic/icons"));
        }
    }
}
