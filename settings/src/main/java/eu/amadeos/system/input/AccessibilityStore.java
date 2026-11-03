package eu.amadeos.system.input;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/accessibility.conf}. The Settings write it; {@code amadeos.jar input}
 * applies it at login with the keyboard, mouse and bell settings.
 */
public final class AccessibilityStore {
    public static final String FILE_NAME = "accessibility.conf";

    private final Path file;

    public AccessibilityStore(Path file) {
        this.file = file;
    }

    public static AccessibilityStore user() {
        return new AccessibilityStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public AccessibilitySettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return AccessibilitySettings.DEFAULTS;
        }
        AccessibilitySettings d = AccessibilitySettings.DEFAULTS;
        return new AccessibilitySettings(
                enumValue(AccessibilitySettings.Pointer.class, p.getProperty("pointer"), d.pointer()),
                integer(p, "pointer.size", d.pointerSize()),
                bool(p, "keys.sticky", d.stickyKeys()),
                bool(p, "keys.sticky.lock", d.stickyLock()),
                bool(p, "keys.sticky.twokeys", d.stickyTwoKeys()),
                bool(p, "keys.slow", d.slowKeys()),
                integer(p, "keys.slow.ms", d.slowKeysMs()),
                bool(p, "keys.bounce", d.bounceKeys()),
                integer(p, "keys.bounce.ms", d.bounceKeysMs()),
                bool(p, "keys.mouse", d.mouseKeys()),
                bool(p, "keys.gestures", d.gestures()),
                bool(p, "keys.feedback", d.feedback()),
                enumValue(AccessibilitySettings.Contrast.class, p.getProperty("contrast"), d.contrast()),
                p.getProperty("contrast.previous", ""));
    }

    public void save(AccessibilitySettings s) {
        Properties p = new Properties();
        p.setProperty("pointer", s.pointer().id());
        p.setProperty("pointer.size", Integer.toString(s.pointerSize()));
        p.setProperty("keys.sticky", Boolean.toString(s.stickyKeys()));
        p.setProperty("keys.sticky.lock", Boolean.toString(s.stickyLock()));
        p.setProperty("keys.sticky.twokeys", Boolean.toString(s.stickyTwoKeys()));
        p.setProperty("keys.slow", Boolean.toString(s.slowKeys()));
        p.setProperty("keys.slow.ms", Integer.toString(s.slowKeysMs()));
        p.setProperty("keys.bounce", Boolean.toString(s.bounceKeys()));
        p.setProperty("keys.bounce.ms", Integer.toString(s.bounceKeysMs()));
        p.setProperty("keys.mouse", Boolean.toString(s.mouseKeys()));
        p.setProperty("keys.gestures", Boolean.toString(s.gestures()));
        p.setProperty("keys.feedback", Boolean.toString(s.feedback()));
        p.setProperty("contrast", s.contrast().id());
        if (!s.previousTheme().isEmpty()) p.setProperty("contrast.previous", s.previousTheme());
        PropertiesFile.store(file, p, "amadeOS accessibility (Settings > Accessibility)");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
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
