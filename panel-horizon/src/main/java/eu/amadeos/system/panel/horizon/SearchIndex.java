package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.settings.SettingsCategory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * What the search finds, apart from sums: the installed programs (by name, generic name, description and
 * desktop entry id), the pages of Settings (by name, description and their search words) and the files and
 * folders in the home folder (by name). The better a hit matches, the higher its score: the whole name, the
 * start of the name, the start of a word in it, anywhere in the name, then only in the description.
 */
final class SearchIndex {
    enum Kind { CALCULATION, PROGRAM, SETTING, FILE }

    /**
     * One result.
     *
     * @param title   the line in bold: the program's or page's name, the file name, the result of the sum
     * @param detail  the dim line after it: what the program is, the page's description, the folder
     * @param iconKey the theme icon
     * @param target  what opening it needs: the desktop entry id, the page, the file path, the result
     */
    record Hit(Kind kind, String title, String detail, String iconKey, String target, int score) {
    }

    static final int MAX_PROGRAMS = 5;
    static final int MAX_SETTINGS = 4;
    static final int MAX_FILES = 6;
    /** The file index stops after this many entries, so that a huge home folder does not hold the search up. */
    static final int MAX_INDEXED = 60_000;
    /** How deep under the home folder the index looks. */
    static final int MAX_DEPTH = 6;
    /** Folders the index does not go into: caches and build output. */
    static final Set<String> SKIPPED = Set.of("node_modules", "target", "build", "__pycache__", "snap");

    private SearchIndex() {
    }

    /** 100 for the whole text, 80 for its start, 60 for the start of a word, 40 anywhere, else 0. */
    static int score(String text, String query) {
        if (text == null || text.isEmpty()) return 0;
        String t = text.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        if (t.equals(q)) return 100;
        if (t.startsWith(q)) return 80;
        for (int i = t.indexOf(q); i >= 0; i = t.indexOf(q, i + 1)) {
            if (!Character.isLetterOrDigit(t.charAt(i - 1))) return 60;
        }
        return t.contains(q) ? 40 : 0;
    }

    static List<Hit> programs(String query, List<XdgDesktopEntry> entries) {
        String q = query.strip();
        if (q.isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (XdgDesktopEntry e : entries) {
            if (!e.isVisible() || !seen.add(e.id())) continue;
            int s = score(e.name(), q);
            if (s == 0) {
                int other = Math.max(score(e.genericName(), q), score(e.id().replace(".desktop", ""), q));
                if (other == 0 && q.length() >= 3 && score(e.comment(), q) >= 60) other = 30;
                s = other == 0 ? 0 : Math.min(other, 60) - 10;
            }
            if (s > 0) {
                String detail = !e.genericName().isBlank() ? e.genericName() : e.comment();
                hits.add(new Hit(Kind.PROGRAM, e.name(), detail, e.icon(), e.id(), s));
            }
        }
        // Programs found only by a word of their description are noise next to one found by its name
        int top = hits.stream().mapToInt(Hit::score).max().orElse(0);
        if (top >= 50) hits.removeIf(h -> h.score() < 30);
        return best(hits, MAX_PROGRAMS);
    }

    static List<Hit> settings(String query) {
        String q = query.strip();
        if (q.isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>();
        for (SettingsCategory c : SettingsCategory.values()) {
            if (!c.available() || !c.matches(q)) continue;
            int s = score(c.label(), q);
            if (s == 0) {
                s = c.keywords().stream().mapToInt(k -> score(k, q)).max().orElse(0) >= 80 ? 45 : 25;
            }
            hits.add(new Hit(Kind.SETTING, c.label(), c.description(), "preferences-desktop",
                    c.name().toLowerCase(Locale.ROOT), s));
        }
        return best(hits, MAX_SETTINGS);
    }

    static List<Hit> files(String query, Path home, List<Path> index) {
        String q = query.strip();
        if (q.length() < 2) return List.of();
        List<Hit> hits = new ArrayList<>();
        for (Path p : index) {
            int s = score(p.getFileName().toString(), q);
            if (s == 0) continue;
            Path parent = p.getParent();
            String folder = parent == null ? "" : parent.equals(home) ? "~"
                    : parent.startsWith(home) ? "~/" + home.relativize(parent) : parent.toString();
            boolean dir = Files.isDirectory(p);
            // Shallower files first among equal matches
            hits.add(new Hit(Kind.FILE, p.getFileName().toString(), folder, dir ? "folder" : "text-x-generic",
                    p.toString(), s * 10 - Math.min(9, p.getNameCount())));
        }
        return best(hits, MAX_FILES);
    }

    private static List<Hit> best(List<Hit> hits, int max) {
        return hits.stream().sorted(Comparator.comparingInt(Hit::score).reversed()
                .thenComparing(h -> h.title().toLowerCase(Locale.ROOT))).limit(max).toList();
    }

    /**
     * The files and folders under {@code home}, without hidden ones and the {@link #SKIPPED} folders, at most
     * {@link #MAX_DEPTH} deep and {@link #MAX_INDEXED} in all; {@code cancelled} stops early.
     */
    static List<Path> indexFiles(Path home, BooleanSupplier cancelled) {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(home)) return found;
        try {
            Files.walkFileTree(home, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (cancelled.getAsBoolean() || found.size() >= MAX_INDEXED) return FileVisitResult.TERMINATE;
                    if (dir.equals(home)) return FileVisitResult.CONTINUE;
                    String name = dir.getFileName().toString();
                    if (name.startsWith(".") || SKIPPED.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                    found.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (found.size() >= MAX_INDEXED) return FileVisitResult.TERMINATE;
                    if (!file.getFileName().toString().startsWith(".")) found.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // what was found so far
        }
        return found;
    }
}
