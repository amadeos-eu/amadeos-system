package eu.amadeos.system.taskmanager.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Puts the processes in the order of the Processes tab: as a tree (children under their parent, brothers
 * sorted) or as a flat list, filtered by a search text. A process whose parent is not shown (it belongs to
 * another user, or is filtered away) starts a tree of its own.
 */
public final class ProcessTree {
    /** One line of the table. */
    public record Line(ProcessRow row, int depth, boolean hasChildren, boolean expanded) {
    }

    private ProcessTree() {
    }

    // --8<-- [start:lines]
    /**
     * The lines to show.
     *
     * @param rows      the processes to show (already limited to the user's own, if so chosen)
     * @param order     the order of brothers (or of the whole list when flat)
     * @param tree      tree or flat list
     * @param collapsed the process ids whose children are hidden
     * @param search    text to look for in name, command line, user and process id; "" for all
     */
    public static List<Line> lines(Collection<ProcessRow> rows, Comparator<ProcessRow> order, boolean tree,
                                   Set<Integer> collapsed, String search) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Map<Integer, ProcessRow> byPid = new HashMap<>();
        for (ProcessRow r : rows) byPid.put(r.pid(), r);
        Set<Integer> matching = new HashSet<>();
        for (ProcessRow r : rows) {
            if (needle.isEmpty() || matches(r, needle)) matching.add(r.pid());
        }
        List<Line> result = new ArrayList<>();
        if (!tree) {
            rows.stream().filter(r -> matching.contains(r.pid())).sorted(order)
                    .forEach(r -> result.add(new Line(r, 0, false, false)));
            return result;
        }
        // While searching, the parents of a match stay, so the match keeps its place in the tree.
        Set<Integer> shown = new HashSet<>(matching);
        if (!needle.isEmpty()) {
            for (int pid : matching) {
                ProcessRow p = byPid.get(byPid.get(pid).parentPid());
                while (p != null && shown.add(p.pid())) p = byPid.get(p.parentPid());
            }
        }
        Map<Integer, List<ProcessRow>> children = new HashMap<>();
        List<ProcessRow> roots = new ArrayList<>();
        for (ProcessRow r : rows) {
            if (!shown.contains(r.pid())) continue;
            if (r.parentPid() != r.pid() && shown.contains(r.parentPid())) {
                children.computeIfAbsent(r.parentPid(), k -> new ArrayList<>()).add(r);
            } else {
                roots.add(r);
            }
        }
        roots.sort(order);
        for (ProcessRow root : roots) add(root, 0, children, order, collapsed, needle.isEmpty(), result);
        return result;
    }
    // --8<-- [end:lines]

    private static void add(ProcessRow row, int depth, Map<Integer, List<ProcessRow>> children,
                            Comparator<ProcessRow> order, Set<Integer> collapsed, boolean mayCollapse,
                            List<Line> out) {
        List<ProcessRow> kids = children.getOrDefault(row.pid(), List.of());
        boolean expanded = !mayCollapse || !collapsed.contains(row.pid());
        out.add(new Line(row, depth, !kids.isEmpty(), expanded));
        if (kids.isEmpty() || !expanded) return;
        List<ProcessRow> sorted = new ArrayList<>(kids);
        sorted.sort(order);
        for (ProcessRow k : sorted) add(k, depth + 1, children, order, collapsed, mayCollapse, out);
    }

    private static boolean matches(ProcessRow r, String needle) {
        return r.name().toLowerCase(Locale.ROOT).contains(needle)
                || r.info().command().toLowerCase(Locale.ROOT).contains(needle)
                || r.user().toLowerCase(Locale.ROOT).contains(needle)
                || Integer.toString(r.pid()).equals(needle);
    }

    /** The process and all its descendants, by process id. */
    public static Set<Integer> subtree(int pid, Collection<ProcessRow> rows) {
        Map<Integer, List<Integer>> children = new HashMap<>();
        for (ProcessRow r : rows) children.computeIfAbsent(r.parentPid(), k -> new ArrayList<>()).add(r.pid());
        Set<Integer> result = new HashSet<>();
        List<Integer> todo = new ArrayList<>(List.of(pid));
        while (!todo.isEmpty()) {
            int p = todo.remove(todo.size() - 1);
            if (result.add(p)) todo.addAll(children.getOrDefault(p, List.of()));
        }
        return result;
    }

    /** The ancestors of a process, nearest first. */
    public static List<Integer> ancestors(int pid, Map<Integer, ProcessRow> byPid) {
        List<Integer> result = new ArrayList<>();
        ProcessRow r = byPid.get(pid);
        Set<Integer> seen = new HashSet<>();
        while (r != null && seen.add(r.pid())) {
            ProcessRow parent = byPid.get(r.parentPid());
            if (parent == null || parent.pid() == r.pid()) break;
            result.add(parent.pid());
            r = parent;
        }
        return result;
    }
}
