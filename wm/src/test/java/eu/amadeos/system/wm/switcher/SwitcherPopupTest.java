package eu.amadeos.system.wm.switcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SwitcherPopupTest {
    @Test
    void tilesWrapIntoRowsOnNarrowScreens() {
        assertEquals(4, SwitcherPopup.columns(4, 1280));
        assertEquals(5, SwitcherPopup.columns(12, 1280));
        assertEquals(3, SwitcherPopup.columns(12, 800));
        assertEquals(1, SwitcherPopup.columns(3, 200));
    }
}
