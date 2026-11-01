package eu.amadeos.system.base.theme;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.theme.HorizonThemes;
import eu.amadeos.commons.theme.ThemeRegistry;
import eu.amadeos.commons.theme.ThemeStore;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The desktop's side of theming: selects the theme, stores it in {@code ~/.config/amadeos/theme.conf},
 * {@code icon_theme.conf} and {@code font.conf} and notifies the desktop processes (window manager, front panel, settings),
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

    // --8<-- [start:apply]
    public static synchronized void applyTheme(String themeId) {
        DesktopTheme found = REGISTRY.find(themeId).orElse(null);
        if (found == null) {
            LOGGER.warning("Theme '" + themeId + "' not found, keeping current: " + currentTheme.id());
            return;
        }
        if (currentTheme.id().equals(found.id())) {
            return;
        }
        DesktopTheme theme = found.withFontSize(currentTheme.fontSize());
        currentTheme = theme;
        AmadeUI.install(theme);
        IconManager.clearCache();
        LOGGER.info(() -> "Applied desktop theme: " + theme.name() + " (" + theme.id() + ")");
        persistLook(theme);
        notifyListeners(theme);
    }
    // --8<-- [end:apply]

    /** The look of the current theme: classic or Horizon. */
    public static DesktopLook currentLook() {
        return currentTheme.look();
    }

    // --8<-- [start:look]
    /**
     * Switches the desktop look: Horizon with the scheme and accent last chosen for it, or classic with the
     * classic palette last chosen. The window manager, the panels and the applications follow.
     */
    public static synchronized void applyLook(DesktopLook look) {
        if (look == currentLook()) return;
        Path dir = XdgDirectories.amadeosConfigDir();
        if (look == DesktopLook.HORIZON) {
            Properties p = ThemeStore.readLook(dir);
            applyTheme(HorizonThemes.id(HorizonThemes.Scheme.of(p.getProperty("scheme")),
                    HorizonThemes.Accent.of(p.getProperty("accent"))));
        } else {
            String classic = readLine(dir.resolve(ThemeStore.THEME_FILE));
            applyTheme(REGISTRY.find(classic).filter(t -> t.look() == DesktopLook.CLASSIC)
                    .orElse(DesktopTheme.WARM_MOTIF).id());
        }
    }
    // --8<-- [end:look]

    /** Horizon's colours: the scheme (dark or light) and the accent. */
    public static void applyHorizonColors(HorizonThemes.Scheme scheme, HorizonThemes.Accent accent) {
        applyTheme(HorizonThemes.id(scheme, accent));
    }

    /**
     * Stores the theme: a classic palette in theme.conf, and the look (with Horizon's scheme and accent) in
     * look.conf, so that switching the look back and forth keeps both choices.
     */
    private static void persistLook(DesktopTheme theme) {
        Path dir = XdgDirectories.amadeosConfigDir();
        if (theme.look() == DesktopLook.CLASSIC) {
            persist(ThemeStore.THEME_FILE, theme.id());
        }
        Properties previous = ThemeStore.readLook(dir);
        boolean hadLook = !previous.isEmpty();
        if (hadLook || theme.look() != DesktopLook.CLASSIC) {
            persist(ThemeStore.LOOK_FILE, ThemeStore.lookFile(theme, previous));
        }
    }

    private static String readLine(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8).strip() : null;
        } catch (IOException e) {
            return null;
        }
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

    /** Sets the font size (1 to 7, CDE's font groups) of the desktop and every application. */
    public static synchronized void applyFontSize(int fontSize) {
        DesktopTheme theme = currentTheme.withFontSize(fontSize);
        if (theme.fontSize() == currentTheme.fontSize()) return;
        currentTheme = theme;
        AmadeUI.install(theme);
        persist(ThemeStore.FONT_FILE, Integer.toString(theme.fontSize()));
        LOGGER.info(() -> "Applied font size: " + theme.fontSize());
        notifyListeners(theme);
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

    // --8<-- [start:persist]
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
    // --8<-- [end:persist]
}
