package eu.amadeos.system.base.startup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The scripts the session runs at every login from {@code /etc/amadeos/autostart.d}: the desktop's own
 * services (input settings, polkit agent, clipboard, screen saver), installed by its packages.
 */
public final class DesktopServices {
    public static final Path SYSTEM_DIR = Path.of("/etc/amadeos/autostart.d");

    private DesktopServices() {
    }

    /** The executable files of {@code dir} by name, as the session runs them; empty without the directory. */
    public static List<String> in(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).filter(Files::isExecutable)
                    .map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
