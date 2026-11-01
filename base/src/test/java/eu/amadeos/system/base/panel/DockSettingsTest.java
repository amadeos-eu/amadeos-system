package eu.amadeos.system.base.panel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockSettingsTest {
    @Test
    void defaultsWithoutAFile(@TempDir Path dir) {
        assertEquals(DockSettings.DEFAULTS, DockSettings.load(dir.resolve(DockSettings.FILE_NAME)));
    }

    @Test
    void savesAndLoadsFavouritesAndSize(@TempDir Path dir) {
        Path file = dir.resolve(DockSettings.FILE_NAME);
        DockSettings s = DockSettings.DEFAULTS.withFavourites(List.of("a.desktop", "b.desktop"))
                .withFavourite("a.desktop", false).withFavourite("c.desktop", true);
        new DockSettings(s.favourites(), 52, false, true).save(file);
        DockSettings loaded = DockSettings.load(file);
        assertEquals(List.of("b.desktop", "c.desktop"), loaded.favourites());
        assertEquals(52, loaded.iconSize());
        assertFalse(loaded.drawers());
        assertTrue(loaded.folders());
    }

    @Test
    void oddSizesSnapToTheNearestOne(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(DockSettings.FILE_NAME);
        Files.writeString(file, "icon.size=40000\nfavourites= a.desktop , ,b.desktop\n");
        DockSettings loaded = DockSettings.load(file);
        assertEquals(52, loaded.iconSize());
        assertEquals(List.of("a.desktop", "b.desktop"), loaded.favourites());
        Files.writeString(file, "icon.size=big\n");
        assertEquals(DockSettings.DEFAULTS.iconSize(), DockSettings.load(file).iconSize());
    }
}
