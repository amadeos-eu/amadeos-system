package eu.amadeos.system.wm.switcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SwitcherSelectionTest {
    private static List<SwitcherEntry> windows(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count)
                .mapToObj(id -> new SwitcherEntry(id, "Window " + id, null, null, false))
                .toList();
    }

    @Test
    void altTabSelectsThePreviousWindow() {
        SwitcherSelection selection = new SwitcherSelection(windows(4), false);
        assertEquals(1, selection.selectedIndex());
        assertEquals(2L, selection.selected().clientWin());
    }

    @Test
    void altShiftTabSelectsTheLastWindow() {
        assertEquals(3, new SwitcherSelection(windows(4), true).selectedIndex());
    }

    @Test
    void movingWrapsAround() {
        SwitcherSelection selection = new SwitcherSelection(windows(3), false);
        selection.move(1);
        selection.move(1);
        assertEquals(0, selection.selectedIndex());
        selection.move(-1);
        assertEquals(2, selection.selectedIndex());
    }

    @Test
    void aSingleWindowStaysSelected() {
        assertEquals(0, new SwitcherSelection(windows(1), false).selectedIndex());
    }

    @Test
    void selectIgnoresIndexesOutside() {
        SwitcherSelection selection = new SwitcherSelection(windows(3), false);
        selection.select(7);
        assertEquals(1, selection.selectedIndex());
        selection.select(2);
        assertEquals(2, selection.selectedIndex());
    }

    @Test
    void needsAWindow() {
        assertThrows(IllegalArgumentException.class, () -> new SwitcherSelection(List.of(), false));
    }
}
