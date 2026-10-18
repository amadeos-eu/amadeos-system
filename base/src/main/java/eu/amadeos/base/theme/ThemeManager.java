package eu.amadeos.base.theme;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.theme.ThemeRegistry;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The desktop's side of theming: selects the theme, stores it in {@code ~/.config/amadeos/theme.conf}
 * and {@code icon_theme.conf} and notifies the desktop processes (window manager, front panel, settings),
 * which also exchange it through the X11 root window property {@value #X11_THEME_ATOM}.
 *
 * <p>The themes themselves, their discovery and the Motif look come from amade-commons
 * ({@link DesktopTheme}, {@link ThemeRegistry}, {@link AmadeUI}). Applications built on amade-commons read
 * the same files through {@link ThemeStore} and switch together with the desktop.
 */
public final class ThemeManager {
    private static final Logger LOGGER = Logger.getLogger(ThemeManager.class.getName());
    public static final String X11_THEME_ATOM = "_AMADEOS_THEME";

    @FunctionalInterface
    public interface ThemeChangeListener {
        void onThemeChanged(DesktopTheme newTheme);
    }

    private static final ThemeRegistry REGISTRY = ThemeRegistry.withCustomThemes(List.of(
            Path.of("/etc/amadeos/themes"), XdgDirectories.amadeosConfigDir().resolve("themes")));
    private static final List<ThemeChangeListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile DesktopTheme currentTheme;

    static {
        // Same reading rules as every amade-commons application: theme.conf, then icon_theme.conf.
        currentTheme = new ThemeStore(XdgDirectories.amadeosConfigDir(), REGISTRY).current();
        AmadeUI.install(currentTheme);
    }

    private ThemeManager() {}

    public static void registerTheme(DesktopTheme theme) {
        REGISTRY.register(theme);
    }

    public static List<DesktopTheme> availableThemes() {
        return REGISTRY.all();
    }

    public static DesktopTheme currentTheme() {
        return currentTheme;
    }

    public static DesktopTheme getTheme(String id) {
        return REGISTRY.find(id).orElse(currentTheme);
    }

    public static void addListener(ThemeChangeListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(ThemeChangeListener listener) {
        LISTENERS.remove(listener);
    }

    public static synchronized void applyTheme(String themeId) {
        DesktopTheme theme = REGISTRY.find(themeId).orElse(null);
        if (theme == null) {
            LOGGER.warning("Theme '" + themeId + "' not found, keeping current: " + currentTheme.id());
            return;
        }
        if (currentTheme.id().equals(theme.id())) {
            return;
        }
        currentTheme = theme;
        AmadeUI.install(theme);
        IconManager.clearCache();
        LOGGER.info(() -> "Applied desktop theme: " + theme.name() + " (" + theme.id() + ")");
        persist(ThemeStore.THEME_FILE, theme.id());
        notifyListeners(theme);
    }

    public static synchronized void applyTheme(DesktopTheme theme) {
        registerTheme(theme);
        applyTheme(theme.id());
    }

    public static synchronized void applyIconTheme(String iconThemeId) {
        if (iconThemeId == null || iconThemeId.isBlank()) return;
        currentTheme = currentTheme.withIconTheme(iconThemeId);
        IconManager.clearCache();
        persist(ThemeStore.ICON_THEME_FILE, iconThemeId);
        LOGGER.info(() -> "Applied icon theme: " + iconThemeId);
        notifyListeners(currentTheme);
    }

    private static void notifyListeners(DesktopTheme theme) {
        for (ThemeChangeListener l : LISTENERS) {
            try {
                l.onThemeChanged(theme);
            } catch (Exception e) {
                LOGGER.warning("Error notifying theme listener: " + e.getMessage());
            }
        }
    }

    /**
     * Writes the file atomically: applications watch these files and must never read a half-written id.
     */
    private static void persist(String fileName, String value) {
        try {
            Path dir = XdgDirectories.amadeosConfigDir();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, fileName, ".tmp");
            try {
                Files.writeString(temp, value, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            LOGGER.warning("Cannot save " + fileName + ": " + e.getMessage());
        }
    }
}
