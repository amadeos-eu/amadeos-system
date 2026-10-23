package eu.amadeos.system.base.startup;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.base.xdg.XdgDesktopEntryParser;
import eu.amadeos.system.base.xdg.XdgDirectories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The user's programs started at login: desktop entries in {@code ~/.config/autostart}, the place the XDG
 * autostart specification gives them. Settings > Startup adds a program by copying its desktop entry there
 * and removes it by deleting the copy; {@code amadeos-session autostart} starts them at login.
 */
public final class UserAutostart {
    private final Path dir;

    public UserAutostart(Path dir) {
        this.dir = dir;
    }

    public static UserAutostart user() {
        return new UserAutostart(XdgDirectories.autostartDir());
    }

    public Path dir() {
        return dir;
    }

    /** The entries to start, sorted by name; entries marked Hidden=true are switched off by the spec. */
    public List<XdgDesktopEntry> entries() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".desktop"))
                    .map(XdgDesktopEntryParser::parse).flatMap(Optional::stream)
                    .filter(e -> !e.hidden())
                    .sorted(Comparator.comparing(XdgDesktopEntry::name, String.CASE_INSENSITIVE_ORDER)).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Starts the program of {@code entry} at every login, by copying its desktop entry. */
    public void add(XdgDesktopEntry entry) throws IOException {
        if (entry.filePath() == null) throw new IOException("No desktop file for " + entry.id());
        Files.createDirectories(dir);
        Files.copy(entry.filePath(), dir.resolve(fileName(entry)), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Stops starting the program of {@code entry}; an entry of another directory is left alone. */
    public void remove(XdgDesktopEntry entry) throws IOException {
        Path file = entry.filePath() != null && dir.equals(entry.filePath().getParent())
                ? entry.filePath() : dir.resolve(fileName(entry));
        Files.deleteIfExists(file);
    }

    private static String fileName(XdgDesktopEntry entry) {
        String name = entry.filePath() != null ? entry.filePath().getFileName().toString() : entry.id();
        return name.endsWith(".desktop") ? name : name + ".desktop";
    }
}
