package eu.amadeos.system.settings;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.commons.desktop.SessionPasswordStore;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.net.ProxyStore;
import eu.amadeos.system.base.panel.PanelSettingsStore;
import eu.amadeos.system.base.power.PowerSettingsStore;
import eu.amadeos.system.base.startup.DesktopServices;
import eu.amadeos.system.base.startup.StartupSettings;
import eu.amadeos.system.base.startup.UserAutostart;
import eu.amadeos.system.base.window.WindowSettingsStore;
import eu.amadeos.system.base.workspace.WorkspaceNames;
import eu.amadeos.system.base.xdg.XdgDirectories;
import eu.amadeos.system.clipboard.ClipboardSettingsStore;
import eu.amadeos.system.display.DisplaySettingsStore;
import eu.amadeos.system.input.InputSettingsStore;
import eu.amadeos.system.screen.ScreenSettingsStore;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Where the Settings keep what they change, and their own view in {@code ~/.config/amadeos/settings.conf}. The
 * theme, the font size and the backdrops go through {@code ThemeManager} and {@code WallpaperManager} in base.
 *
 * @param servicesDir the desktop services the session starts, {@code /etc/amadeos/autostart.d}
 */
record SettingsStores(ScreenSettingsStore screen, ClipboardSettingsStore clipboard, InputSettingsStore input,
                      WindowSettingsStore window, PanelSettingsStore panel, WorkspaceNames workspaces,
                      StartupSettings startup, UserAutostart autostart, Path servicesDir, LocaleStore locales,
                      DisplaySettingsStore display, ProxyStore proxy, Path viewFile, PowerSettingsStore power) {
    static final String VIEW_FILE = "settings.conf";

    /** The sidebar with one page per control, or CDE's row of buttons that open a dialog each. */
    enum View {SIDEBAR, COMPACT}

    static SettingsStores user() {
        return new SettingsStores(ScreenSettingsStore.user(), ClipboardSettingsStore.user(), InputSettingsStore.user(),
                WindowSettingsStore.user(), PanelSettingsStore.user(), WorkspaceNames.user(), StartupSettings.user(),
                UserAutostart.user(), DesktopServices.SYSTEM_DIR, new LocaleStore(XdgDirectories.amadeosConfigDir()),
                DisplaySettingsStore.user(), ProxyStore.system(), XdgDirectories.amadeosConfigDir().resolve(VIEW_FILE),
                PowerSettingsStore.user());
    }

    /** The stores of one directory, for tests. */
    static SettingsStores in(Path dir) {
        return new SettingsStores(new ScreenSettingsStore(dir.resolve("screen.conf")),
                new ClipboardSettingsStore(dir.resolve("clipboard.conf")), new InputSettingsStore(dir.resolve("input.conf")),
                new WindowSettingsStore(dir.resolve(WindowSettingsStore.FILE_NAME)),
                new PanelSettingsStore(dir.resolve(PanelSettingsStore.FILE_NAME)), new WorkspaceNames(dir),
                new StartupSettings(dir.resolve(StartupSettings.FILE_NAME)), new UserAutostart(dir.resolve("autostart")),
                dir.resolve("autostart.d"), new LocaleStore(dir),
                new DisplaySettingsStore(dir.resolve(DisplaySettingsStore.FILE_NAME)),
                new ProxyStore(dir, new SessionPasswordStore()), dir.resolve(VIEW_FILE),
                new PowerSettingsStore(dir.resolve(PowerSettingsStore.FILE_NAME)));
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
        save("view", view.name().toLowerCase(Locale.ROOT));
    }

    /** Whether Network's proxy is also apt's ("also for software"); the apt file itself only root can read. */
    boolean aptProxy() {
        try {
            return Boolean.parseBoolean(PropertiesFile.load(viewFile).getProperty("proxy.apt", "false").strip());
        } catch (RuntimeException e) {
            return false;
        }
    }

    void saveAptProxy(boolean on) {
        save("proxy.apt", String.valueOf(on));
    }

    private synchronized void save(String key, String value) {
        Properties p;
        try {
            p = PropertiesFile.load(viewFile);
        } catch (RuntimeException e) {
            p = new Properties();
        }
        p.setProperty(key, value);
        PropertiesFile.store(viewFile, p, "amadeOS Settings");
    }
}
