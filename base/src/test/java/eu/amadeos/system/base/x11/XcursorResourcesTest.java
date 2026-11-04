package eu.amadeos.system.base.x11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class XcursorResourcesTest {

    @Test
    void readsThemeAndSizeAmongOtherResources() {
        XcursorResources r = XcursorResources.parse(
                "*background:\tgrey\nXcursor.size:\t48\nXcursor.theme:\tDMZ-White\nXft.dpi:\t96\n");
        assertEquals("DMZ-White", r.theme());
        assertEquals(48, r.size());
    }

    @Test
    void missingOrBrokenValuesMeanNone() {
        XcursorResources r = XcursorResources.parse("Xcursor.size: big\n");
        assertTrue(r.themeName().isEmpty());
        assertEquals(0, r.size());
        assertEquals(new XcursorResources("", 0), XcursorResources.parse(null));
    }
}
