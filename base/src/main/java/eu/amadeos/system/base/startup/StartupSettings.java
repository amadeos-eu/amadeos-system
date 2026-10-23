package eu.amadeos.system.base.startup;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Which scripts of {@code /etc/amadeos/autostart.d} the user switched off, kept in
 * {@code ~/.config/amadeos/startup.conf} as {@code disabled=40-amadeos-clipboard}. {@code amadeos-session}
 * reads the same line at login and skips those scripts.
 */
public final class StartupSettings {
    public static final String FILE_NAME = "startup.conf";

    private final Path file;

    public StartupSettings(Path file) {
        this.file = file;
    }

    public static StartupSettings user() {
        return new StartupSettings(XdgDirectories.amadeosConfigDir().resolve(FILE_NAME));
    }

    public Path file() {
        return file;
    }

    /** The names of the switched-off scripts. */
    public Set<String> disabled() {
        try {
            String value = PropertiesFile.load(file).getProperty("disabled", "");
            return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    public void saveDisabled(Set<String> names) {
        Properties p = new Properties();
        p.setProperty("disabled", String.join(",", new TreeSet<>(names)));
        PropertiesFile.store(file, p, "amadeOS startup (Settings > Startup)");
    }
}
