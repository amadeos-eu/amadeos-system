package eu.amadeos.system.taskmanager.model;

import eu.amadeos.system.taskmanager.proc.ProcessInfo;
import eu.amadeos.system.taskmanager.x.WindowInfo;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessTreeTest {
    private static ProcessRow row(int pid, int ppid, String name, double cpu, long memory) {
        return new ProcessRow(new ProcessInfo(pid, ppid, name, "/usr/bin/" + name, 1000, 'S', 0, memory, 0, 0, 1, 0,
                false), "ralf", cpu, 0);
    }

    private final List<ProcessRow> rows = List.of(
            row(10, 1, "session", 0, 40),
            row(20, 10, "wm", 1, 90),
            row(30, 10, "firefox", 20, 600),
            row(31, 30, "Web Content", 15, 800),
            row(32, 30, "RDD Process", 0, 30),
            row(40, 999, "pipewire", 1, 18));

    private static final Comparator<ProcessRow> BY_NAME = Comparator.comparing(ProcessRow::name, String.CASE_INSENSITIVE_ORDER);

    private static List<String> names(List<ProcessTree.Line> lines) {
        return lines.stream().map(l -> "  ".repeat(l.depth()) + l.row().name()).toList();
    }

    @Test
    void childrenFollowTheirParentAndOrphansStartTheirOwnTree() {
        List<ProcessTree.Line> lines = ProcessTree.lines(rows, BY_NAME, true, Set.of(), "");
        assertEquals(List.of("pipewire", "session", "  firefox", "    RDD Process", "    Web Content", "  wm"),
                names(lines));
        assertTrue(lines.get(1).hasChildren());
        assertFalse(lines.get(0).hasChildren());
    }

    @Test
    void collapsedProcessesHideTheirChildren() {
        List<ProcessTree.Line> lines = ProcessTree.lines(rows, BY_NAME, true, Set.of(30), "");
        assertEquals(List.of("pipewire", "session", "  firefox", "  wm"), names(lines));
        assertFalse(lines.get(2).expanded());
    }

    @Test
    void searchKeepsTheParentsOfAMatch() {
        List<ProcessTree.Line> lines = ProcessTree.lines(rows, BY_NAME, true, Set.of(30), "web");
        assertEquals(List.of("session", "  firefox", "    Web Content"), names(lines),
                "a collapsed parent opens while searching");
        assertEquals(List.of("Web Content"), names(ProcessTree.lines(rows, BY_NAME, false, Set.of(), "WEB")));
        assertEquals(List.of("wm"), names(ProcessTree.lines(rows, BY_NAME, false, Set.of(), "20")));
    }

    @Test
    void theFlatListIsSortedAsAWhole() {
        Comparator<ProcessRow> byMemory = Comparator.comparingLong(ProcessRow::memory).reversed();
        assertEquals(List.of("Web Content", "firefox", "wm", "session", "RDD Process", "pipewire"),
                names(ProcessTree.lines(rows, byMemory, false, Set.of(), "")));
    }

    @Test
    void subtreesAndAncestors() {
        assertEquals(Set.of(30, 31, 32), ProcessTree.subtree(30, rows));
        Map<Integer, ProcessRow> byPid = new HashMap<>();
        rows.forEach(r -> byPid.put(r.pid(), r));
        assertEquals(List.of(30, 10), ProcessTree.ancestors(31, byPid));
    }

    @Test
    void aProgramAddsUpItsProcesses() {
        Map<Integer, ProcessRow> byPid = new HashMap<>();
        rows.forEach(r -> byPid.put(r.pid(), r));
        WindowInfo browser = new WindowInfo(0x400001, "Web Browser", 30, 0, false, true);
        WindowInfo unknown = new WindowInfo(0x500001, "xterm", 0, 1, false, false);
        List<ProgramRow> programs = ProgramRow.of(List.of(browser, unknown), byPid);
        assertEquals(35, programs.get(0).cpu(), 0.001);
        assertEquals(1430, programs.get(0).memory());
        assertEquals(0, programs.get(1).memory(), "a window without _NET_WM_PID");
    }
}
