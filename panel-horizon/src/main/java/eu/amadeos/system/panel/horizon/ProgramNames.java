package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The name the top bar shows for the active window: the name of the program's desktop entry, found by the
 * window's class ({@code WM_CLASS}), else the class itself, else the window title.
 */
final class ProgramNames {
    private ProgramNames() {
    }

    // --8<-- [start:name]
    static String nameOf(String windowClass, String title, List<XdgDesktopEntry> entries) {
        Optional<XdgDesktopEntry> entry = entryOf(windowClass, entries);
        if (entry.isPresent()) return entry.get().name();
        String cls = windowClass == null ? "" : windowClass.strip();
        if (cls.isEmpty() || cls.split("[-.]").length >= 3) {
            // A Java main class without an entry: the window title says more
            if (title != null && !title.isBlank()) return title.strip();
        }
        if (!cls.isEmpty()) return Character.toUpperCase(cls.charAt(0)) + cls.substring(1);
        return title == null ? "" : title.strip();
    }

    /**
     * The desktop entry of the program a window belongs to, by the window's class: the entry's
     * {@code StartupWMClass}, its id or program name (also the real program of snap and Flatpak entries, so
     * Firefox's class {@code firefox} finds {@code firefox_firefox.desktop} or
     * {@code org.mozilla.firefox.desktop}); for Java programs that report their main class (eu-amadeos-apps-pad-PadApplication), an entry
     * amade-&lt;part&gt; or amadeos-&lt;part&gt;.
     */
    static Optional<XdgDesktopEntry> entryOf(String windowClass, List<XdgDesktopEntry> entries) {
        String cls = windowClass == null ? "" : windowClass.strip();
        if (cls.isEmpty()) return Optional.empty();
        String key = normalize(cls);
        for (XdgDesktopEntry e : entries) {
            if (!e.startupWmClass().isBlank() && key.equals(normalize(e.startupWmClass()))) return Optional.of(e);
        }
        for (XdgDesktopEntry e : entries) {
            if (key.equals(normalize(stripDesktop(e.id()))) || key.equals(normalize(e.program()))) {
                return Optional.of(e);
            }
        }
        for (XdgDesktopEntry e : entries) {
            if (packagedNames(stripDesktop(e.id())).contains(key)) return Optional.of(e);
        }
        String[] parts = cls.split("[-.]");
        if (parts.length >= 3) {
            for (String part : parts) {
                for (String candidate : new String[]{"amade" + part, "amadeos" + part}) {
                    String wanted = normalize(candidate);
                    for (XdgDesktopEntry e : entries) {
                        if (wanted.equals(normalize(stripDesktop(e.id())))) return Optional.of(e);
                    }
                }
            }
        }
        return Optional.empty();
    }
    // --8<-- [end:name]

    private static String stripDesktop(String id) {
        return id.endsWith(".desktop") ? id.substring(0, id.length() - ".desktop".length()) : id;
    }

    /**
     * The names a snap or Flatpak entry id stands for: {@code firefox_firefox} (snap {@code <snap>_<app>}) gives
     * firefox, {@code org.mozilla.firefox} (reverse domain) gives firefox.
     */
    private static List<String> packagedNames(String id) {
        List<String> names = new java.util.ArrayList<>();
        int underscore = id.indexOf('_');
        if (underscore > 0) {
            names.add(normalize(id.substring(0, underscore)));
            names.add(normalize(id.substring(underscore + 1)));
        }
        String[] parts = id.split("\\.");
        if (parts.length >= 3) names.add(normalize(parts[parts.length - 1]));
        return names;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
