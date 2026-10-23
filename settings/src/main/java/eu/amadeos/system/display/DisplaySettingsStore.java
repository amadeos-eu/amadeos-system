package eu.amadeos.system.display;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Reads and writes {@code ~/.config/amadeos/display.conf}: one layout for each set of connected monitors, so a
 * laptop keeps one layout at the desk with its external monitor and another on the road. A line per monitor,
 * e.g. {@code eDP-1+HDMI-1.HDMI-1=1920x1080 60.00 1920 0 normal primary}, or {@code off}.
 */
public final class DisplaySettingsStore {
    public static final String FILE_NAME = "display.conf";

    private final Path file;

    public DisplaySettingsStore(Path file) {
        this.file = file;
    }

    public static DisplaySettingsStore user() {
        return new DisplaySettingsStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public Path file() {
        return file;
    }

    /** The key of a set of connected monitors: their names, sorted, joined by {@code +}. */
    public static String key(Collection<String> connected) {
        return String.join("+", new TreeSet<>(connected));
    }

    /** The layout saved for exactly these connected monitors, if there is one for each of them. */
    public Optional<List<OutputSetting>> load(Collection<String> connected) {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String key = key(connected);
        List<OutputSetting> layout = new ArrayList<>();
        for (String name : new TreeSet<>(connected)) {
            String value = p.getProperty(key + "." + name);
            if (value == null) return Optional.empty();
            Optional<OutputSetting> setting = parse(name, value);
            if (setting.isEmpty()) return Optional.empty();
            layout.add(setting.get());
        }
        return layout.isEmpty() ? Optional.empty() : Optional.of(layout);
    }

    /** Saves the layout for its monitors and keeps the layouts of other sets of monitors. */
    public void save(List<OutputSetting> layout) {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            p = new Properties();
        }
        String key = key(layout.stream().map(OutputSetting::name).toList());
        p.stringPropertyNames().stream().filter(k -> k.startsWith(key + ".")).toList().forEach(p::remove);
        for (OutputSetting s : layout) p.setProperty(key + "." + s.name(), format(s));
        PropertiesFile.store(file, p, "amadeOS monitors (Settings > Display), one layout per set of monitors");
    }

    static String format(OutputSetting s) {
        if (!s.enabled()) return "off";
        return String.format(Locale.ROOT, "%dx%d %.2f %d %d %s%s", s.width(), s.height(), s.rate(), s.x(), s.y(),
                s.rotation().xrandr(), s.primary() ? " primary" : "");
    }

    static Optional<OutputSetting> parse(String name, String value) {
        String[] parts = value.strip().split("\\s+");
        if (parts.length == 1 && parts[0].equals("off")) {
            return Optional.of(new OutputSetting(name, false, 0, 0, 0, 0, 0, Rotation.NORMAL, false));
        }
        try {
            String[] size = parts[0].split("x");
            return Optional.of(new OutputSetting(name, true, Integer.parseInt(size[0]), Integer.parseInt(size[1]),
                    Double.parseDouble(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Rotation.of(parts[4]), parts.length > 5 && parts[5].equals("primary")));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
