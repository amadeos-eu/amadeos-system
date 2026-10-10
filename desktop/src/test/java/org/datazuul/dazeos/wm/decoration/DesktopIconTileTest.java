package org.datazuul.dazeos.wm.decoration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.NativeLong;
import org.junit.jupiter.api.Test;

class DesktopIconTileTest {

    @Test
    void testGeometryAndStackingCoordinates() {
        NativeLong tileWin = new NativeLong(5555);
        DesktopIconTile tile0 = new DesktopIconTile(1001, tileWin, 0, "xterm");
        DesktopIconTile tile1 = new DesktopIconTile(1002, tileWin, 1, "Editor");
        DesktopIconTile tile2 = new DesktopIconTile(1003, tileWin, 2, "Control Panel");

        // Top-left corner with 0px spacing
        assertEquals(0, tile0.x());
        assertEquals(0, tile0.y());

        assertEquals(0, tile1.x());
        assertEquals(68, tile1.y());

        assertEquals(0, tile2.x());
        assertEquals(136, tile2.y());

        // Test reflow index update
        tile2.setIndex(1);
        assertEquals(68, tile2.y());
    }

    @Test
    void testRenderDifferentGlyphs() {
        NativeLong tileWin = new NativeLong(5555);

        // Terminal glyph
        DesktopIconTile termTile = new DesktopIconTile(1001, tileWin, 0, "xterm");
        termTile.render();

        // Editor glyph
        DesktopIconTile editTile = new DesktopIconTile(1002, tileWin, 1, "Text Editor");
        editTile.render();

        // Control Panel glyph
        DesktopIconTile cpTile = new DesktopIconTile(1003, tileWin, 2, "Control Panel");
        cpTile.render();

        // Generic glyph
        DesktopIconTile genericTile = new DesktopIconTile(1004, tileWin, 3, "Calculator");
        genericTile.render();

        assertEquals(68, DesktopIconTile.TILE_WIDTH);
        assertEquals(68, DesktopIconTile.TILE_HEIGHT);
    }
}

