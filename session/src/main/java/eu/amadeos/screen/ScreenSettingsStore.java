package eu.amadeos.screen;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/screen.conf}. The Style Manager writes, the screen saver
 * daemon notices changes through {@link #changedSince(FileTime)} without a restart.
 */
public final class ScreenSettingsStore {
    public static final String FILE_NAME = "screen.conf";

    private final Path file;

    public ScreenSettingsStore(Path file) {
        this.file = file;
    }

    public static ScreenSettingsStore user() {
        return new ScreenSettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public ScreenSettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return ScreenSettings.DEFAULTS;
        }
        ScreenSettings d = ScreenSettings.DEFAULTS;
        return new ScreenSettings(
                bool(p, "saver.enabled", d.saverEnabled()),
                integer(p, "saver.minutes", d.saverMinutes()),
                bool(p, "lock.enabled", d.lockEnabled()),
                integer(p, "lock.minutes", d.lockMinutes()),
                p.getProperty("saver.module", d.module()));
    }

    public void save(ScreenSettings s) {
        Properties p = new Properties();
        p.setProperty("saver.enabled", Boolean.toString(s.saverEnabled()));
        p.setProperty("saver.minutes", Integer.toString(s.saverMinutes()));
        p.setProperty("saver.module", s.module());
        p.setProperty("lock.enabled", Boolean.toString(s.lockEnabled()));
        p.setProperty("lock.minutes", Integer.toString(s.lockMinutes()));
        PropertiesFile.store(file, p, "amadeOS screen saver and screen lock (Style Manager > Screen)");
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
