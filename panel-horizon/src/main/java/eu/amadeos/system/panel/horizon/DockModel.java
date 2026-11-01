package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the program part of the dock shows: the favourite programs in the user's order, then the programs that
 * run without being favourites. Windows are grouped by their program (the desktop entry found from the window
 * class, else the class itself), so a program with three windows is one icon with its windows behind it.
 */
final class DockModel {
    private DockModel() {
    }

    /** A window as the dock sees it. */
    record Window(long id, String windowClass, String title, boolean minimized) {
    }

    /**
     * One program in the dock.
     *
     * @param key       the desktop entry id, or {@code class:<window class>} for a program without an entry
     * @param entry     its desktop entry, if it has one
     * @param windows   its windows, oldest first
     * @param favourite kept in the dock while it is not running
     */
    record Program(String key, XdgDesktopEntry entry, String name, List<Window> windows, boolean favourite) {
        boolean running() {
            return !windows.isEmpty();
        }

        boolean allMinimized() {
            return !windows.isEmpty() && windows.stream().allMatch(Window::minimized);
        }
    }

    // --8<-- [start:programs]
    static List<Program> programs(List<String> favourites, List<XdgDesktopEntry> entries, List<Window> windows) {
        Map<String, XdgDesktopEntry> byId = new LinkedHashMap<>();
        for (XdgDesktopEntry e : entries) byId.putIfAbsent(e.id(), e);

        Map<String, List<Window>> windowsByKey = new LinkedHashMap<>();
        Map<String, XdgDesktopEntry> entryByKey = new LinkedHashMap<>();
        Map<String, String> nameByKey = new LinkedHashMap<>();
        for (Window w : windows) {
            Optional<XdgDesktopEntry> entry = ProgramNames.entryOf(w.windowClass(), entries);
            String key = entry.map(XdgDesktopEntry::id).orElseGet(() -> "class:" + classKey(w));
            windowsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(w);
            entry.ifPresent(e -> entryByKey.put(key, e));
            nameByKey.putIfAbsent(key, ProgramNames.nameOf(w.windowClass(), w.title(), entries));
        }

        List<Program> result = new ArrayList<>();
        for (String id : favourites) {
            XdgDesktopEntry e = byId.get(id);
            if (e == null) continue; // not installed (any more)
            result.add(new Program(id, e, e.name(), List.copyOf(windowsByKey.getOrDefault(id, List.of())), true));
            windowsByKey.remove(id);
        }
        for (Map.Entry<String, List<Window>> running : windowsByKey.entrySet()) {
            String key = running.getKey();
            result.add(new Program(key, entryByKey.get(key), nameByKey.get(key), List.copyOf(running.getValue()), false));
        }
        return result;
    }
    // --8<-- [end:programs]

    /** The window after {@code current} among the program's windows, for clicking through them. */
    static Window next(Program program, long current) {
        List<Window> ws = program.windows();
        for (int i = 0; i < ws.size(); i++) {
            if (ws.get(i).id() == current) return ws.get((i + 1) % ws.size());
        }
        return ws.get(0);
    }

    private static String classKey(Window w) {
        String c = w.windowClass() == null || w.windowClass().isBlank() ? w.title() : w.windowClass();
        return c == null ? "" : c.strip().toLowerCase(Locale.ROOT);
    }
}
