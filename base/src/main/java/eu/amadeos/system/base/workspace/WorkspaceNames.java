package eu.amadeos.system.base.workspace;

import eu.amadeos.system.base.xdg.XdgDirectories;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * The names the user gave the four workspaces, kept in {@code $XDG_CONFIG_HOME/amadeos/workspaces.conf}
 * as {@code workspace.1.name=Mail} and so on. A workspace without a name of its own is called One to Four in
 * the desktop language, as in CDE; the caller supplies that default. The Front Panel renames workspaces and
 * publishes the names in the root window's {@code _NET_DESKTOP_NAMES}, where the window manager's root menu
 * and the Settings read them.
 */
public final class WorkspaceNames {
    /** The number of workspaces, as in CDE's default. */
    public static final int COUNT = 4;
    /** The longest name a workspace button takes; longer input is cut. */
    public static final int MAX_LENGTH = 24;
    public static final String FILE_NAME = "workspaces.conf";

    private final Path file;

    /** @param configDir the amadeOS config directory, {@code ~/.config/amadeos} for the user */
    public WorkspaceNames(Path configDir) {
        this.file = configDir.resolve(FILE_NAME);
    }

    /** The names of the current user. */
    public static WorkspaceNames user() {
        return new WorkspaceNames(XdgDirectories.amadeosConfigDir());
    }

    /** The name the user gave workspace {@code index} (0 to 3), if any. */
    public Optional<String> custom(int index) {
        checkIndex(index);
        String name = load().getProperty(key(index));
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(clean(name));
    }

    /** The user's name for workspace {@code index}, else {@code fallback}. */
    public String name(int index, String fallback) {
        return custom(index).orElse(fallback);
    }

    /**
     * Renames workspace {@code index}; a blank name gives it back its default name.
     *
     * @return the stored name, empty for the default
     */
    public Optional<String> rename(int index, String name) throws IOException {
        checkIndex(index);
        Properties props = load();
        String cleaned = clean(name);
        if (cleaned.isEmpty()) {
            props.remove(key(index));
        } else {
            props.setProperty(key(index), cleaned);
        }
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "amadeOS workspace names");
        }
        return cleaned.isEmpty() ? Optional.empty() : Optional.of(cleaned);
    }

    /** A name as a button shows it: one line without control characters, trimmed, at most {@link #MAX_LENGTH}. */
    public static String clean(String name) {
        if (name == null) return "";
        String line = name.replaceAll("\\p{Cntrl}", " ").strip();
        return line.length() > MAX_LENGTH ? line.substring(0, MAX_LENGTH).strip() : line;
    }

    private Properties load() {
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
                // An unreadable file means default names.
            }
        }
        return props;
    }

    private static String key(int index) {
        return "workspace." + (index + 1) + ".name";
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("No workspace " + index);
        }
    }
}
