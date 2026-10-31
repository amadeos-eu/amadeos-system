package eu.amadeos.system.notify;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/notifications.conf}. The Settings and the daemon (Do not disturb from
 * the Front Panel's bell) write it; the daemon notices changes through {@link #changedSince(FileTime)}.
 * Programs are kept as {@code program.<key>=popup|history|off} and {@code name.<key>=<name>}.
 */
public final class NotificationSettingsStore {
    public static final String FILE_NAME = "notifications.conf";

    private final Path file;

    public NotificationSettingsStore(Path file) {
        this.file = file;
    }

    public static NotificationSettingsStore user() {
        return new NotificationSettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public Path file() {
        return file;
    }

    public NotificationSettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return NotificationSettings.DEFAULTS;
        }
        NotificationSettings d = NotificationSettings.DEFAULTS;
        Map<String, NotificationSettings.Mode> programs = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("program.")) {
                try {
                    programs.put(key.substring(8), NotificationSettings.Mode.valueOf(
                            p.getProperty(key).strip().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // unknown mode: pop-ups
                }
            } else if (key.startsWith("name.")) {
                names.put(key.substring(5), p.getProperty(key).strip());
            }
        }
        return new NotificationSettings(corner(p.getProperty("popup.corner"), d.corner()),
                integer(p, "popup.seconds", d.seconds()),
                bool(p, "popup.beep", d.beep()),
                dnd(p.getProperty("dnd.until")),
                bool(p, "dnd.schedule", d.schedule()),
                time(p.getProperty("dnd.from"), d.from()),
                time(p.getProperty("dnd.to"), d.to()),
                bool(p, "dnd.fullscreen", d.fullscreen()),
                programs, names);
    }

    public void save(NotificationSettings s) {
        Properties p = new Properties();
        p.setProperty("popup.corner", s.corner().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        p.setProperty("popup.seconds", Integer.toString(s.seconds()));
        p.setProperty("popup.beep", Boolean.toString(s.beep()));
        p.setProperty("dnd.until", s.dndUntil() == NotificationSettings.DND_ON ? "on"
                : s.dndUntil() == NotificationSettings.DND_OFF ? "off" : Long.toString(s.dndUntil()));
        p.setProperty("dnd.schedule", Boolean.toString(s.schedule()));
        p.setProperty("dnd.from", s.from().toString());
        p.setProperty("dnd.to", s.to().toString());
        p.setProperty("dnd.fullscreen", Boolean.toString(s.fullscreen()));
        s.programs().forEach((k, v) -> p.setProperty("program." + k, v.name().toLowerCase(Locale.ROOT)));
        s.names().forEach((k, v) -> p.setProperty("name." + k, v));
        PropertiesFile.store(file, p, "amadeOS notifications (Settings > Notifications)");
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

    private static NotificationSettings.Corner corner(String v, NotificationSettings.Corner fallback) {
        if (v == null) return fallback;
        try {
            return NotificationSettings.Corner.valueOf(v.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static long dnd(String v) {
        if (v == null) return NotificationSettings.DND_OFF;
        v = v.strip();
        if (v.equalsIgnoreCase("on")) return NotificationSettings.DND_ON;
        try {
            return Math.max(0, Long.parseLong(v));
        } catch (NumberFormatException e) {
            return NotificationSettings.DND_OFF;
        }
    }

    private static LocalTime time(String v, LocalTime fallback) {
        try {
            return v == null ? fallback : LocalTime.parse(v.strip());
        } catch (DateTimeParseException e) {
            return fallback;
        }
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
