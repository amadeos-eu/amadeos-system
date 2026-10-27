package eu.amadeos.system.base.panel;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/panel.conf}. The Settings and the clock's context menu write it;
 * the Front Panel watches the file and follows a change at once. The clock style was kept in
 * {@code clock.conf} before; a file without a clock entry still takes it from there.
 */
public final class PanelSettingsStore {
    public static final String FILE_NAME = "panel.conf";
    static final String LEGACY_CLOCK_FILE = "clock.conf";

    private final Path file;

    public PanelSettingsStore(Path file) {
        this.file = file;
    }

    public static PanelSettingsStore user() {
        return new PanelSettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public Path file() {
        return file;
    }

    public PanelSettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            p = new Properties();
        }
        PanelSettings d = PanelSettings.DEFAULTS;
        String clock = p.getProperty("clock");
        if (clock == null) clock = legacyClock();
        return new PanelSettings(clock(clock, d.clock()),
                bool(p, "cell.network", d.networkCell()),
                bool(p, "cell.sound", d.soundCell()),
                bool(p, "cell.battery", d.batteryCell()),
                bool(p, "cell.bluetooth", d.bluetoothCell()),
                bool(p, "cell.updates", d.updatesCell()),
                bool(p, "activity.meter", d.activityMeter()));
    }

    public void save(PanelSettings s) {
        Properties p = new Properties();
        p.setProperty("clock", s.clock().name().toLowerCase(Locale.ROOT));
        p.setProperty("cell.network", Boolean.toString(s.networkCell()));
        p.setProperty("cell.sound", Boolean.toString(s.soundCell()));
        p.setProperty("cell.battery", Boolean.toString(s.batteryCell()));
        p.setProperty("cell.bluetooth", Boolean.toString(s.bluetoothCell()));
        p.setProperty("cell.updates", Boolean.toString(s.updatesCell()));
        p.setProperty("activity.meter", Boolean.toString(s.activityMeter()));
        PropertiesFile.store(file, p, "amadeOS Front Panel (Settings > Front Panel)");
    }

    private String legacyClock() {
        Path legacy = file.resolveSibling(LEGACY_CLOCK_FILE);
        try {
            return Files.isRegularFile(legacy) ? Files.readString(legacy, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static PanelSettings.Clock clock(String value, PanelSettings.Clock fallback) {
        if (value == null) return fallback;
        try {
            return PanelSettings.Clock.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.strip());
    }
}
