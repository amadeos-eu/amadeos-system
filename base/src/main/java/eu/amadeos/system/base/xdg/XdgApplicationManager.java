package eu.amadeos.system.base.xdg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Discovers, indexes, and queries XDG desktop applications installed across standard system and user paths.
 */
public final class XdgApplicationManager {
    private static final Logger LOGGER = Logger.getLogger(XdgApplicationManager.class.getName());

    private static final XdgApplicationManager INSTANCE = new XdgApplicationManager();

    public static XdgApplicationManager getInstance() {
        return INSTANCE;
    }

    private final Map<String, XdgDesktopEntry> entries = new LinkedHashMap<>();

    public XdgApplicationManager() {
        reload();
    }

    public synchronized void reload() {
        entries.clear();
        List<Path> searchDirs = getApplicationDirectories();

        for (Path dir : searchDirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> stream = Files.walk(dir, 2)) {
                stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".desktop"))
                        .forEach(p -> {
                            String id = p.getFileName().toString();
                            // User entries take precedence over system entries (first encountered wins)
                            if (!entries.containsKey(id)) {
                                XdgDesktopEntryParser.parse(p).ifPresent(entry -> {
                                    if (entry.isVisible() && entry.isInstalled()) {
                                        entries.put(id, entry);
                                    }
                                });
                            }
                        });
            } catch (Exception e) {
                LOGGER.warning("Error scanning XDG applications in " + dir + ": " + e.getMessage());
            }
        }
        LOGGER.info(() -> "Loaded " + entries.size() + " visible XDG desktop applications.");
    }

    /**
     * The folders of the desktop entries, the user's first: {@code $XDG_DATA_HOME/applications}, then
     * {@code applications} in each {@link XdgDirectories#systemDataDirs() system data folder}, which include the
     * Flatpak and snap folders.
     */
    public static List<Path> getApplicationDirectories() {
        List<Path> dirs = new ArrayList<>();
        String userHome = System.getProperty("user.home", "/tmp");
        dirs.add(XdgDirectories.dataHome(System.getenv(), userHome).resolve("applications"));
        for (Path d : XdgDirectories.systemDataDirs()) dirs.add(d.resolve("applications"));
        return dirs;
    }

    public synchronized List<XdgDesktopEntry> getAllApplications() {
        List<XdgDesktopEntry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing(XdgDesktopEntry::name, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(list);
    }

    public synchronized List<XdgDesktopEntry> getApplicationsByCategory(XdgCategory category) {
        List<XdgDesktopEntry> list = new ArrayList<>();
        for (XdgDesktopEntry entry : entries.values()) {
            if (entry.matchesCategory(category)) {
                list.add(entry);
            }
        }
        list.sort(Comparator.comparing(XdgDesktopEntry::name, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(list);
    }

    public synchronized Optional<XdgDesktopEntry> findById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public synchronized Optional<XdgDesktopEntry> findFirstByCategory(XdgCategory category) {
        List<XdgDesktopEntry> list = getApplicationsByCategory(category);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}

