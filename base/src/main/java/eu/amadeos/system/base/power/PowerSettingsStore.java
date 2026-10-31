package eu.amadeos.system.base.power;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/power.conf}. The Settings write it; the session's screen and power
 * daemon and the window manager (power button) notice changes through {@link #changedSince(FileTime)}.
 */
public final class PowerSettingsStore {
    public static final String FILE_NAME = "power.conf";

    private final Path file;

    public PowerSettingsStore(Path file) {
        this.file = file;
    }

    public static PowerSettingsStore user() {
        return new PowerSettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public PowerSettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return PowerSettings.DEFAULTS;
        }
        PowerSettings d = PowerSettings.DEFAULTS;
        return new PowerSettings(profile(p, "battery", d.battery()), profile(p, "mains", d.mains()),
                PowerAction.parse(p.getProperty("power-button"), d.powerKey()),
                bool(p, "lid.ignore-docked", d.lidIgnoreDocked()),
                bool(p, "lock-on-wake", d.lockOnWake()),
                integer(p, "low-battery.warn", d.warnPercent()),
                integer(p, "low-battery.percent", d.lowPercent()),
                PowerAction.parse(p.getProperty("low-battery.action"), d.lowAction()));
    }

    public void save(PowerSettings s) {
        Properties p = new Properties();
        put(p, "battery", s.battery());
        put(p, "mains", s.mains());
        p.setProperty("power-button", s.powerKey().key());
        p.setProperty("lid.ignore-docked", Boolean.toString(s.lidIgnoreDocked()));
        p.setProperty("lock-on-wake", Boolean.toString(s.lockOnWake()));
        p.setProperty("low-battery.warn", Integer.toString(s.warnPercent()));
        p.setProperty("low-battery.percent", Integer.toString(s.lowPercent()));
        p.setProperty("low-battery.action", s.lowAction().key());
        PropertiesFile.store(file, p, "amadeOS power management (Settings > Power); minutes 0 = never");
    }

    /** Modification time of the file, or null if it does not exist. */
    public FileTime lastModified() {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public boolean changedSince(FileTime seen) {
        FileTime now = lastModified();
        return now == null ? seen != null : !now.equals(seen);
    }

    private static PowerSettings.Profile profile(Properties p, String prefix, PowerSettings.Profile d) {
        return new PowerSettings.Profile(integer(p, prefix + ".screen-off", d.screenOffMinutes()),
                integer(p, prefix + ".suspend", d.suspendMinutes()),
                PowerAction.parse(p.getProperty(prefix + ".lid"), d.lid()));
    }

    private static void put(Properties p, String prefix, PowerSettings.Profile profile) {
        p.setProperty(prefix + ".screen-off", Integer.toString(profile.screenOffMinutes()));
        p.setProperty(prefix + ".suspend", Integer.toString(profile.suspendMinutes()));
        p.setProperty(prefix + ".lid", profile.lid().key());
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.strip());
    }

    private static int integer(Properties p, String key, int fallback) {
        try {
            String v = p.getProperty(key);
            return v == null ? fallback : Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
