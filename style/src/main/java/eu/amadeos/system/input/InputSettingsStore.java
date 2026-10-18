package eu.amadeos.system.input;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/input.conf}.
 */
public final class InputSettingsStore {
    public static final String FILE_NAME = "input.conf";

    private final Path file;

    public InputSettingsStore(Path file) {
        this.file = file;
    }

    public static InputSettingsStore user() {
        return new InputSettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public InputSettings load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return InputSettings.DEFAULTS;
        }
        InputSettings.Keyboard k = InputSettings.Keyboard.DEFAULTS;
        InputSettings.Mouse m = InputSettings.Mouse.DEFAULTS;
        InputSettings.Beep b = InputSettings.Beep.DEFAULTS;
        return new InputSettings(
                new InputSettings.Keyboard(
                        bool(p, "keyboard.autoRepeat", k.autoRepeat()),
                        integer(p, "keyboard.repeatDelayMs", k.repeatDelayMs()),
                        integer(p, "keyboard.repeatRate", k.repeatRate()),
                        integer(p, "keyboard.clickVolume", k.clickVolume())),
                new InputSettings.Mouse(
                        bool(p, "mouse.leftHanded", m.leftHanded()),
                        integer(p, "mouse.doubleClickMs", m.doubleClickMs()),
                        integer(p, "mouse.acceleration", m.acceleration()),
                        integer(p, "mouse.threshold", m.threshold())),
                new InputSettings.Beep(
                        integer(p, "beep.volume", b.volume()),
                        integer(p, "beep.pitchHz", b.pitchHz()),
                        integer(p, "beep.durationMs", b.durationMs())));
    }

    public void save(InputSettings s) {
        Properties p = new Properties();
        p.setProperty("keyboard.autoRepeat", Boolean.toString(s.keyboard().autoRepeat()));
        p.setProperty("keyboard.repeatDelayMs", Integer.toString(s.keyboard().repeatDelayMs()));
        p.setProperty("keyboard.repeatRate", Integer.toString(s.keyboard().repeatRate()));
        p.setProperty("keyboard.clickVolume", Integer.toString(s.keyboard().clickVolume()));
        p.setProperty("mouse.leftHanded", Boolean.toString(s.mouse().leftHanded()));
        p.setProperty("mouse.doubleClickMs", Integer.toString(s.mouse().doubleClickMs()));
        p.setProperty("mouse.acceleration", Integer.toString(s.mouse().acceleration()));
        p.setProperty("mouse.threshold", Integer.toString(s.mouse().threshold()));
        p.setProperty("beep.volume", Integer.toString(s.beep().volume()));
        p.setProperty("beep.pitchHz", Integer.toString(s.beep().pitchHz()));
        p.setProperty("beep.durationMs", Integer.toString(s.beep().durationMs()));
        PropertiesFile.store(file, p, "amadeOS keyboard, mouse and bell (Style Manager)");
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
