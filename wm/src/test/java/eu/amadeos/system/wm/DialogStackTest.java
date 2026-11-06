package eu.amadeos.system.wm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogStackTest {
    @Test
    void theDialogsOfARaisedWindowComeUpInTheirOrder() {
        // bottom to top: dialog 11 of window 1, window 2, dialog 12 of 1, window 1 (just raised)
        List<Long> stack = List.of(11L, 2L, 12L, 1L);
        Map<Long, Long> owners = Map.of(11L, 1L, 12L, 1L);
        assertEquals(List.of(11L, 12L), DialogStack.above(stack, 1, owners, id -> true));
    }

    @Test
    void dialogsOfDialogsComeUpTooButNotHiddenOnesOrOtherWindows() {
        // 21 is a dialog of the dialog 12; 13 is minimized; 30 belongs to window 3
        List<Long> stack = List.of(21L, 13L, 30L, 12L, 3L, 1L);
        Map<Long, Long> owners = Map.of(12L, 1L, 13L, 1L, 21L, 12L, 30L, 3L);
        Set<Long> hidden = Set.of(13L);
        assertEquals(List.of(21L, 12L), DialogStack.above(stack, 1, owners, id -> !hidden.contains(id)));
    }

    @Test
    void aWindowWithoutDialogsRaisesNothingElse() {
        assertEquals(List.of(), DialogStack.above(List.of(2L, 1L), 1, Map.of(), id -> true));
    }
}
