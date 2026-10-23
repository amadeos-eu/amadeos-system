package eu.amadeos.system.base.window;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/window.conf}. The Settings write it; the window manager watches
 * the file and follows a change at once.
 */
public final class WindowSettingsStore {
    public static final String FILE_NAME = "window.conf";

    private final Path file;

    public WindowSettingsStore(Path file) {
        this.file = file;
    }

    public static WindowSettingsStore user() {
        return new WindowSettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public Path file() {
        return file;
    }

    public WindowSettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return WindowSettings.DEFAULTS;
        }
        WindowSettings d = WindowSettings.DEFAULTS;
        return new WindowSettings(
                focus(p.getProperty("focus"), d.focus()),
                bool(p, "raise.when.active", d.raiseWhenActive()),
                bool(p, "move.contents", d.showContentsDuringMove()));
    }

    public void save(WindowSettings s) {
        Properties p = new Properties();
        p.setProperty("focus", s.focus().name().toLowerCase(Locale.ROOT));
        p.setProperty("raise.when.active", Boolean.toString(s.raiseWhenActive()));
        p.setProperty("move.contents", Boolean.toString(s.showContentsDuringMove()));
        PropertiesFile.store(file, p, "amadeOS window behaviour (Settings > Window)");
    }

    private static WindowSettings.Focus focus(String value, WindowSettings.Focus fallback) {
        if (value == null) return fallback;
        try {
            return WindowSettings.Focus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.strip());
    }
}
