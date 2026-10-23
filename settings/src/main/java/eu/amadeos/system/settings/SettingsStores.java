package eu.amadeos.system.settings;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.window.WindowSettingsStore;
import eu.amadeos.system.base.xdg.XdgDirectories;
import eu.amadeos.system.clipboard.ClipboardSettingsStore;
import eu.amadeos.system.input.InputSettingsStore;
import eu.amadeos.system.screen.ScreenSettingsStore;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Where the Settings keep what they change, and their own view in {@code ~/.config/amadeos/settings.conf}. The
 * theme, the font size and the backdrops go through {@code ThemeManager} and {@code WallpaperManager} in base.
 */
record SettingsStores(ScreenSettingsStore screen, ClipboardSettingsStore clipboard, InputSettingsStore input,
                      WindowSettingsStore window, Path viewFile) {
    static final String VIEW_FILE = "settings.conf";

    /** The sidebar with one page per control, or CDE's row of buttons that open a dialog each. */
    enum View {SIDEBAR, COMPACT}

    static SettingsStores user() {
        return new SettingsStores(ScreenSettingsStore.user(), ClipboardSettingsStore.user(), InputSettingsStore.user(),
                WindowSettingsStore.user(), XdgDirectories.amadeosConfigDir().resolve(VIEW_FILE));
    }

    /** The stores of one directory, for tests. */
    static SettingsStores in(Path dir) {
        return new SettingsStores(new ScreenSettingsStore(dir.resolve("screen.conf")),
                new ClipboardSettingsStore(dir.resolve("clipboard.conf")), new InputSettingsStore(dir.resolve("input.conf")),
                new WindowSettingsStore(dir.resolve(WindowSettingsStore.FILE_NAME)), dir.resolve(VIEW_FILE));
    }

    View view() {
        try {
            String value = PropertiesFile.load(viewFile).getProperty("view", View.SIDEBAR.name());
            return View.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return View.SIDEBAR;
        }
    }

    void saveView(View view) {
        Properties p = new Properties();
        p.setProperty("view", view.name().toLowerCase(Locale.ROOT));
        PropertiesFile.store(viewFile, p, "amadeOS Settings");
    }
}
