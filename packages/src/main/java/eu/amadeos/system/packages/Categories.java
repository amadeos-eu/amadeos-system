package eu.amadeos.system.packages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Sorts packages into {@link Category categories}. The best sources come first: the AppStream metadata of the
 * repositories (DEP-11, fetched by apt when the {@code appstream} package is installed) names the desktop and
 * console applications, and the files of installed packages show which bring a menu entry or a program in a
 * {@code bin} directory. Without them the package's section, name and debtags decide.
 */
public final class Categories {
    private static final Set<String> LIBRARY_SECTIONS = Set.of("libs", "libdevel", "oldlibs", "python", "perl",
            "ruby", "java", "javascript", "php", "haskell", "rust", "golang", "ocaml", "lisp", "gnu-r",
            "introspection", "debug", "cli-mono", "zope");
    private static final Set<String> COMMAND_LINE_SECTIONS = Set.of("utils", "admin", "net", "shells", "vcs",
            "text", "interpreters", "editors", "database", "mail", "devel", "science", "math", "httpd", "web",
            "comm", "news", "hamradio", "electronics", "tex", "embedded", "otherosfs");
    /** Only used when there is no AppStream data, which knows better. */
    private static final Set<String> APPLICATION_SECTIONS = Set.of("x11", "gnome", "kde", "xfce", "graphics",
            "games", "video", "sound", "gnustep", "education");

    /**
     * What is known about packages beyond their index entry.
     *
     * @param appstream true when AppStream data was found; then only the packages it (or an installed menu
     *                  entry) names are applications
     * @param desktop   packages with a desktop application (AppStream, or an installed menu entry)
     * @param console   packages with a console application (AppStream, a terminal menu entry, or an installed
     *                  program in a {@code bin} directory)
     */
    public record Hints(boolean appstream, Set<String> desktop, Set<String> console) {
        public static final Hints NONE = new Hints(false, Set.of(), Set.of());

        public Hints {
            desktop = Set.copyOf(desktop);
            console = Set.copyOf(console);
        }
    }

    /** Data, translations and documentation that come with a program. */
    private static final List<String> PART_SUFFIXES = List.of("-data", "-common", "-doc", "-l10n", "-locale",
            "-theme", "-themes", "-icons", "-music", "-sounds");

    private Categories() {
    }

    public static Category of(String name, String section, List<String> tags, Hints hints) {
        if (hints.desktop().contains(name)) return Category.APPLICATION;
        boolean development = name.endsWith("-dev") || name.endsWith("-dbg") || name.endsWith("-dbgsym");
        if (!development && hints.console().contains(name)) return Category.COMMAND_LINE;
        if (development || LIBRARY_SECTIONS.contains(section) || tags.contains("role::shared-lib")
                || tags.contains("role::devel-lib")) {
            return Category.LIBRARY;
        }
        if (PART_SUFFIXES.stream().anyMatch(name::endsWith)) return Category.OTHER;
        if (tags.contains("interface::commandline") || COMMAND_LINE_SECTIONS.contains(section)) {
            return Category.COMMAND_LINE;
        }
        if (!hints.appstream() && (tags.contains("interface::x11") || APPLICATION_SECTIONS.contains(section))) {
            return Category.APPLICATION;
        }
        return Category.OTHER;
    }

    /**
     * Reads AppStream DEP-11 YAML line by line: each document ({@code ---}) is one component, whose top-level
     * {@code Type} and {@code Package} fields say which package has which kind of application.
     */
    public static final class Dep11Reader implements Consumer<String> {
        private final Set<String> desktop = new HashSet<>();
        private final Set<String> console = new HashSet<>();
        private String type;
        private String pkg;

        @Override
        public void accept(String line) {
            if (line.startsWith("---")) {
                flush();
            } else if (line.startsWith("Type:")) {
                type = line.substring(5).strip();
            } else if (line.startsWith("Package:")) {
                pkg = line.substring(8).strip();
            }
        }

        private void flush() {
            if (pkg != null && type != null) {
                if (type.equals("desktop-application")) desktop.add(pkg);
                if (type.equals("console-application")) console.add(pkg);
            }
            type = null;
            pkg = null;
        }

        public Set<String> desktop() {
            flush();
            return desktop;
        }

        public Set<String> console() {
            flush();
            return console;
        }
    }

    /** A file of an installed package. */
    public record Owned(String pkg, String path) {
    }

    /**
     * {@code dpkg-query -S PATTERN} output ("pkg1, pkg2:amd64: /usr/bin/x") as (package, path) pairs, the
     * package without its architecture; diversion lines are skipped.
     */
    public static List<Owned> parseOwners(String output) {
        List<Owned> owned = new ArrayList<>();
        for (String line : output.lines().toList()) {
            if (line.startsWith("diversion ")) continue;
            int colon = line.indexOf(": /");
            if (colon <= 0) continue;
            String path = line.substring(colon + 2).strip();
            for (String name : line.substring(0, colon).split(",")) {
                String n = name.strip();
                int arch = n.indexOf(':');
                if (!n.isEmpty()) owned.add(new Owned(arch < 0 ? n : n.substring(0, arch), path));
            }
        }
        return owned;
    }
}
