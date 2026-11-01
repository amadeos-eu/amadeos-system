package eu.amadeos.system.base.xdg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The default applications of the XDG MIME Applications specification: which program {@code xdg-open} starts
 * for a type of file or link. The user's choices are in {@code ~/.config/mimeapps.list}, amadeOS's in
 * {@code /etc/xdg/mimeapps.list}; the first file that names an installed program for a type wins.
 *
 * <p>Writing touches only the user's file and only its {@code [Default Applications]} lines of the given
 * types; other sections and lines, e.g. those written by Firefox or LibreOffice, stay as they are.
 */
public final class MimeApps {
    static final String DEFAULTS = "Default Applications";

    private final Path userFile;
    private final List<Path> systemFiles;
    private final Predicate<String> installed;

    /**
     * @param userFile    {@code ~/.config/mimeapps.list}
     * @param systemFiles the other files in the order of the specification, e.g. {@code /etc/xdg/mimeapps.list}
     * @param installed   true for the desktop file ids (e.g. {@code firefox-esr.desktop}) that are installed
     */
    public MimeApps(Path userFile, List<Path> systemFiles, Predicate<String> installed) {
        this.userFile = userFile;
        this.systemFiles = List.copyOf(systemFiles);
        this.installed = installed;
    }

    /** The files of the running user and the computer, with the installed programs of the desktop. */
    public static MimeApps system(Collection<XdgDesktopEntry> applications) {
        Map<String, String> env = System.getenv();
        String home = System.getProperty("user.home", "/tmp");
        Path config = XdgDirectories.configHome(env, home);
        List<Path> others = new ArrayList<>();
        String configDirs = env.getOrDefault("XDG_CONFIG_DIRS", "");
        for (String dir : (configDirs.isBlank() ? "/etc/xdg" : configDirs).split(":")) {
            if (!dir.isBlank()) others.add(Path.of(dir, "mimeapps.list"));
        }
        for (Path dir : XdgApplicationManager.getApplicationDirectories()) others.add(dir.resolve("mimeapps.list"));
        List<String> ids = applications.stream().map(XdgDesktopEntry::id).toList();
        return new MimeApps(config.resolve("mimeapps.list"), others, ids::contains);
    }

    public Path userFile() {
        return userFile;
    }

    /** The program that opens the type now: the user's choice, else the computer's. */
    public Optional<String> defaultFor(String mimeType) {
        Optional<String> own = first(userFile, mimeType);
        return own.isPresent() ? own : systemDefaultFor(mimeType);
    }

    /** The program amadeOS (or the administrator) chose for the type, without the user's choice. */
    public Optional<String> systemDefaultFor(String mimeType) {
        for (Path file : systemFiles) {
            Optional<String> id = first(file, mimeType);
            if (id.isPresent()) return id;
        }
        return Optional.empty();
    }

    /** True if the user's file chooses a program for the type. */
    public boolean userChoice(String mimeType) {
        return first(userFile, mimeType).isPresent();
    }

    private Optional<String> first(Path file, String mimeType) {
        String value = read(file).getOrDefault(DEFAULTS, Map.of()).get(mimeType.toLowerCase(Locale.ROOT));
        if (value == null) return Optional.empty();
        return Arrays.stream(value.split(";")).map(String::strip).filter(s -> !s.isEmpty()).filter(installed)
                .findFirst();
    }

    // --8<-- [start:set]
    /** Makes the program the user's default for the types. */
    public void setDefault(String desktopId, Collection<String> mimeTypes) throws IOException {
        update(lines -> {
            for (String type : mimeTypes) lines.put(type, desktopId + ";");
        });
    }

    /** Removes the user's choice for the types, so that amadeOS's default applies again. */
    public void reset(Collection<String> mimeTypes) throws IOException {
        if (!Files.isRegularFile(userFile)) return;
        update(lines -> mimeTypes.forEach(lines::remove));
    }
    // --8<-- [end:set]

    private interface Edit {
        void apply(Map<String, String> defaults);
    }

    /** Rewrites the user's file with the edited {@code [Default Applications]} lines and everything else unchanged. */
    private void update(Edit edit) throws IOException {
        List<String> lines = Files.isRegularFile(userFile)
                ? new ArrayList<>(Files.readAllLines(userFile, StandardCharsets.UTF_8)) : new ArrayList<>();
        int start = -1;
        int end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i).strip();
            if (s.startsWith("[") && s.endsWith("]")) {
                if (start >= 0) {
                    end = i;
                    break;
                }
                if (s.equals("[" + DEFAULTS + "]")) start = i;
            }
        }
        Map<String, String> defaults = new LinkedHashMap<>();
        List<String> other = new ArrayList<>();
        if (start >= 0) {
            for (String line : lines.subList(start + 1, end)) {
                int eq = line.indexOf('=');
                String s = line.strip();
                if (eq > 0 && !s.startsWith("#")) defaults.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                else if (!s.isEmpty()) other.add(line);
            }
        }
        edit.apply(defaults);
        List<String> section = new ArrayList<>();
        section.add("[" + DEFAULTS + "]");
        section.addAll(other);
        defaults.forEach((k, v) -> section.add(k + "=" + v));
        List<String> out = new ArrayList<>();
        if (start >= 0) {
            out.addAll(lines.subList(0, start));
            out.addAll(section);
            List<String> rest = lines.subList(end, lines.size());
            if (!rest.isEmpty()) out.add("");
            out.addAll(rest);
        } else {
            out.addAll(lines);
            if (!out.isEmpty() && !out.getLast().isBlank()) out.add("");
            out.addAll(section);
        }
        while (!out.isEmpty() && out.getLast().isBlank()) out.removeLast();
        Path dir = userFile.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, "mimeapps.list", ".tmp");
        try {
            Files.writeString(temp, String.join("\n", out) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temp, userFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, userFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** The sections of a file with their keys; an empty map for a missing or unreadable file. */
    static Map<String, Map<String, String>> read(Path file) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return sections;
        try {
            Map<String, String> current = null;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.isEmpty() || s.startsWith("#")) continue;
                if (s.startsWith("[") && s.endsWith("]")) {
                    current = sections.computeIfAbsent(s.substring(1, s.length() - 1), k -> new LinkedHashMap<>());
                } else if (current != null) {
                    int eq = s.indexOf('=');
                    if (eq > 0) current.putIfAbsent(s.substring(0, eq).strip().toLowerCase(Locale.ROOT), s.substring(eq + 1).strip());
                }
            }
        } catch (IOException e) {
            return Map.of();
        }
        return sections;
    }
}
