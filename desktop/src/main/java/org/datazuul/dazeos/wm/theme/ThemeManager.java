package org.datazuul.dazeos.wm.theme;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public final class ThemeManager {
    private static final Logger LOGGER = Logger.getLogger(ThemeManager.class.getName());
    public static final String X11_THEME_ATOM = "_DAZEOS_THEME";

    @FunctionalInterface
    public interface ThemeChangeListener {
        void onThemeChanged(DesktopTheme newTheme);
    }

    private static final Map<String, DesktopTheme> THEMES = new LinkedHashMap<>();
    private static final List<ThemeChangeListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile DesktopTheme currentTheme;

    static {
        registerTheme(DesktopTheme.WARM_MOTIF);
        registerTheme(DesktopTheme.CDE_BROCADE);
        registerTheme(DesktopTheme.CDE_DESERT);
        registerTheme(DesktopTheme.CLASSIC_GRAY);
        registerTheme(DesktopTheme.DARK_SLATE);

        currentTheme = DesktopTheme.WARM_MOTIF;

        // Discover on-disk custom themes
        discoverThemesFromDisk();

        // Load persisted preference if available
        loadPersistedTheme();
    }

    private ThemeManager() {}

    public static synchronized void registerTheme(DesktopTheme theme) {
        THEMES.put(theme.id(), theme);
    }

    public static List<DesktopTheme> availableThemes() {
        return Collections.unmodifiableList(new ArrayList<>(THEMES.values()));
    }

    public static DesktopTheme currentTheme() {
        return currentTheme;
    }

    public static DesktopTheme getTheme(String id) {
        if (id == null) return currentTheme;
        DesktopTheme found = THEMES.get(id);
        return found != null ? found : currentTheme;
    }

    public static void addListener(ThemeChangeListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(ThemeChangeListener listener) {
        LISTENERS.remove(listener);
    }

    public static synchronized void applyTheme(String themeId) {
        DesktopTheme theme = THEMES.get(themeId);
        if (theme == null) {
            LOGGER.warning("Theme '" + themeId + "' not found, keeping current: " + currentTheme.id());
            return;
        }
        if (currentTheme.id().equals(theme.id())) {
            return;
        }

        currentTheme = theme;
        IconManager.clearCache();
        LOGGER.info(() -> "Applied desktop theme: " + theme.name() + " (" + theme.id() + ")");

        // Save preference
        persistTheme(theme.id());

        // Notify in-process listeners
        for (ThemeChangeListener l : LISTENERS) {
            try {
                l.onThemeChanged(theme);
            } catch (Exception e) {
                LOGGER.warning("Error notifying theme listener: " + e.getMessage());
            }
        }
    }

    public static synchronized void applyTheme(DesktopTheme theme) {
        registerTheme(theme);
        applyTheme(theme.id());
    }

    private static void discoverThemesFromDisk() {
        String userHome = System.getProperty("user.home", "/tmp");
        File[] searchDirs = {
                new File("/etc/dazeos/themes"),
                new File(userHome + "/.config/dazeos/themes")
        };

        for (File dir : searchDirs) {
            if (dir.isDirectory()) {
                File[] subdirs = dir.listFiles(File::isDirectory);
                if (subdirs != null) {
                    for (File themeDir : subdirs) {
                        loadThemeFromDirectory(themeDir);
                    }
                }
            }
        }
    }

    private static void loadThemeFromDirectory(File dir) {
        File propFile = new File(dir, "theme.properties");
        if (!propFile.isFile()) {
            return;
        }

        try (InputStream in = new FileInputStream(propFile)) {
            Properties p = new Properties();
            p.load(in);

            String id = dir.getName();
            String name = p.getProperty("name", id);
            String baseHex = p.getProperty("base", "#CEC2BB");
            Color base = Color.decode(baseHex);

            Color hl = p.containsKey("highlight") ? Color.decode(p.getProperty("highlight")) : null;
            Color softHl = p.containsKey("softHighlight") ? Color.decode(p.getProperty("softHighlight")) : null;
            Color sh = p.containsKey("shadow") ? Color.decode(p.getProperty("shadow")) : null;
            Color darkSh = p.containsKey("darkShadow") ? Color.decode(p.getProperty("darkShadow")) : null;
            Color sunken = p.containsKey("sunkenBg") ? Color.decode(p.getProperty("sunkenBg")) : null;
            Color activeTitle = p.containsKey("activeTitleBg") ? Color.decode(p.getProperty("activeTitleBg")) : null;
            Color inactiveTitle = p.containsKey("inactiveTitleBg") ? Color.decode(p.getProperty("inactiveTitleBg")) : null;
            Color txtAct = p.containsKey("textActive") ? Color.decode(p.getProperty("textActive")) : null;
            Color txtInact = p.containsKey("textInactive") ? Color.decode(p.getProperty("textInactive")) : null;
            String iconTheme = p.getProperty("iconTheme", id);

            DesktopTheme custom = new DesktopTheme(id, name, base, hl, softHl, sh, darkSh, sunken, activeTitle, inactiveTitle, txtAct, txtInact, iconTheme);
            registerTheme(custom);
            LOGGER.info("Discovered custom theme from " + propFile.getAbsolutePath() + ": " + name);
        } catch (Exception e) {
            LOGGER.warning("Failed to load theme from " + propFile + ": " + e.getMessage());
        }
    }

    private static void loadPersistedTheme() {
        try {
            String userHome = System.getProperty("user.home", "/tmp");
            Path conf = Path.of(userHome, ".config", "dazeos", "theme.conf");
            if (Files.exists(conf)) {
                String id = Files.readString(conf, StandardCharsets.UTF_8).trim();
                if (THEMES.containsKey(id)) {
                    currentTheme = THEMES.get(id);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void persistTheme(String themeId) {
        try {
            String userHome = System.getProperty("user.home", "/tmp");
            Path dir = Path.of(userHome, ".config", "dazeos");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("theme.conf"), themeId, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}
