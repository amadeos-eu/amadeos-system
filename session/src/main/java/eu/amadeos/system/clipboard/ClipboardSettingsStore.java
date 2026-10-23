package eu.amadeos.system.clipboard;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/clipboard.conf}. The Settings write, the clipboard manager
 * notices changes through {@link #changedSince(FileTime)} without a restart. Only the settings live there:
 * the history itself is never written to disk.
 */
public final class ClipboardSettingsStore {
    public static final String FILE_NAME = "clipboard.conf";

    private final Path file;

    public ClipboardSettingsStore(Path file) {
        this.file = file;
    }

    public static ClipboardSettingsStore user() {
        return new ClipboardSettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public ClipboardSettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return ClipboardSettings.DEFAULTS;
        }
        ClipboardSettings d = ClipboardSettings.DEFAULTS;
        return new ClipboardSettings(
                bool(p, "history.enabled", d.history()),
                integer(p, "history.size", d.size()),
                bool(p, "keep.enabled", d.keep()),
                bool(p, "primary.enabled", d.primary()));
    }

    public void save(ClipboardSettings s) {
        Properties p = new Properties();
        p.setProperty("history.enabled", Boolean.toString(s.history()));
        p.setProperty("history.size", Integer.toString(s.size()));
        p.setProperty("keep.enabled", Boolean.toString(s.keep()));
        p.setProperty("primary.enabled", Boolean.toString(s.primary()));
        PropertiesFile.store(file, p, "amadeOS clipboard manager (Settings > Clipboard)");
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
