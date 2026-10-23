package eu.amadeos.system.wm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FocusSuccessorTest {
    @Test
    void theOwnerOfAClosedDialogGetsTheFocus() {
        // Contacts main window 10, a terminal 20 above it, the contact editor 30 (a dialog of 10) on top;
        // the date picker, a dialog of 30, was closed.
        assertEquals(30L, FocusSuccessor.choose(List.of(10L, 20L, 30L), 30L, id -> true));
        assertEquals(10L, FocusSuccessor.choose(List.of(10L, 20L, 30L), 10L, id -> true));
    }

    @Test
    void otherwiseTheTopmostWindowThatCanTakeIt() {
        assertEquals(30L, FocusSuccessor.choose(List.of(10L, 20L, 30L), 0L, id -> true));
        assertEquals(20L, FocusSuccessor.choose(List.of(10L, 20L, 30L), 0L, id -> id != 30L), "30 is minimized");
        assertEquals(20L, FocusSuccessor.choose(List.of(10L, 20L, 30L), 30L, id -> id != 30L),
                "a minimized owner does not come back");
        assertEquals(30L, FocusSuccessor.choose(List.of(10L, 20L, 30L), 99L, id -> true), "an unmanaged owner");
        assertEquals(0L, FocusSuccessor.choose(List.of(), 0L, id -> true));
    }
}
