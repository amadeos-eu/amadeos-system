package eu.amadeos.system.base.shortcuts;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Reads and writes {@code ~/.config/amadeos/shortcuts.conf}. Only keys that differ from the defaults are written
 * ({@code terminal=Ctrl+Alt+Return}, {@code search=none} for one that is switched off), so a later release can
 * change a default for everyone who kept it. The user's own commands are {@code custom.N.name},
 * {@code custom.N.command} and {@code custom.N.keys}, N from 1. The Settings write the file; the window manager
 * watches it and grabs the new keys at once.
 */
public final class ShortcutStore {
    public static final String FILE_NAME = "shortcuts.conf";
    private static final String NONE = "none";

    private final Path file;

    public ShortcutStore(Path file) {
        this.file = file;
    }

    public static ShortcutStore user() {
        return new ShortcutStore(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public Path file() {
        return file;
    }

    public Shortcuts load() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return Shortcuts.DEFAULTS;
        }
        Map<ShortcutAction, Optional<KeyCombo>> bindings = new EnumMap<>(ShortcutAction.class);
        for (ShortcutAction a : ShortcutAction.values()) {
            String value = p.getProperty(a.id());
            if (value == null) continue;
            if (value.strip().equalsIgnoreCase(NONE)) {
                bindings.put(a, Optional.empty());
            } else {
                KeyCombo.parse(value).ifPresent(k -> bindings.put(a, Optional.of(k)));
            }
        }
        List<CustomShortcut> custom = new ArrayList<>();
        for (int i = 1; p.getProperty("custom." + i + ".command") != null; i++) {
            Optional<KeyCombo> keys = KeyCombo.parse(p.getProperty("custom." + i + ".keys"));
            String command = p.getProperty("custom." + i + ".command");
            if (keys.isPresent() && !command.isBlank()) {
                custom.add(new CustomShortcut(p.getProperty("custom." + i + ".name"), command, keys.get()));
            }
        }
        return new Shortcuts(bindings, custom);
    }

    public void save(Shortcuts s) {
        Properties p = new Properties();
        for (ShortcutAction a : ShortcutAction.values()) {
            Optional<KeyCombo> keys = s.keys(a);
            if (keys.isEmpty()) {
                p.setProperty(a.id(), NONE);
            } else if (!keys.get().equals(a.defaultKeys())) {
                p.setProperty(a.id(), keys.get().text());
            }
        }
        for (int i = 0; i < s.custom().size(); i++) {
            CustomShortcut c = s.custom().get(i);
            String prefix = "custom." + (i + 1) + ".";
            p.setProperty(prefix + "name", c.name());
            p.setProperty(prefix + "command", c.command());
            p.setProperty(prefix + "keys", c.keys().text());
        }
        PropertiesFile.store(file, p, "amadeOS keyboard shortcuts (Settings > Keyboard Shortcuts)");
    }
}
