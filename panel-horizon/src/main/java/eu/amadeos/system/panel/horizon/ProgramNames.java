package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;

import java.util.List;
import java.util.Locale;

/**
 * The name the top bar shows for the active window: the name of the program's desktop entry, found by the
 * window's class ({@code WM_CLASS}), else the class itself, else the window title.
 */
final class ProgramNames {
    private ProgramNames() {
    }

    // --8<-- [start:name]
    static String nameOf(String windowClass, String title, List<XdgDesktopEntry> entries) {
        String cls = windowClass == null ? "" : windowClass.strip();
        if (!cls.isEmpty()) {
            String key = normalize(cls);
            for (XdgDesktopEntry e : entries) {
                if (key.equals(normalize(stripDesktop(e.id()))) || key.equals(normalize(program(e)))) {
                    return e.name();
                }
            }
            // Java programs may report their main class (eu-amadeos-apps-pad-PadApplication): look for
            // amade-<part>, amadeos-<part> or <part> among the entries, else show the window title
            String[] parts = cls.split("[-.]");
            if (parts.length >= 3) {
                for (String part : parts) {
                    for (String candidate : new String[]{"amade" + part, "amadeos" + part}) {
                        String wanted = normalize(candidate);
                        for (XdgDesktopEntry e : entries) {
                            if (wanted.equals(normalize(stripDesktop(e.id())))) return e.name();
                        }
                    }
                }
                if (title != null && !title.isBlank()) return title.strip();
            }
            return Character.toUpperCase(cls.charAt(0)) + cls.substring(1);
        }
        return title == null ? "" : title.strip();
    }
    // --8<-- [end:name]

    private static String stripDesktop(String id) {
        return id.endsWith(".desktop") ? id.substring(0, id.length() - ".desktop".length()) : id;
    }

    private static String program(XdgDesktopEntry e) {
        String exec = e.exec() == null ? "" : e.exec().strip();
        String first = exec.isEmpty() ? "" : exec.split("\\s+", 2)[0];
        int slash = first.lastIndexOf('/');
        return slash >= 0 ? first.substring(slash + 1) : first;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
