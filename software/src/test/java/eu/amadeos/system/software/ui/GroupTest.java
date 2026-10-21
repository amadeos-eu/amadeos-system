package eu.amadeos.system.software.ui;

import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.software.appstream.AppData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupTest {
    private static Item item(String section, String... categories) {
        CatalogEntry entry = new CatalogEntry("x", "", section, Category.APPLICATION, "", "1", false);
        Optional<AppData> app = categories.length == 0 ? Optional.empty()
                : Optional.of(new AppData("x", "x", "X", "", "", List.of(categories), Optional.empty(), "", List.of()));
        return new Item(entry, app);
    }

    @Test
    void appStreamCategoriesComeFirst() {
        assertEquals(Set.of(Group.GRAPHICS), Group.of(item("graphics", "Graphics", "RasterGraphics")));
        assertEquals(Set.of(Group.GAMES, Group.EDUCATION, Group.SCIENCE),
                Group.of(item("games", "Game", "Education", "Math")));
        assertEquals(Set.of(Group.AUDIO_VIDEO), Group.of(item("sound", "Audio", "AudioVideo", "Player")));
        assertEquals(Set.of(Group.INTERNET), Group.of(item("web", "Network", "WebBrowser")));
        assertEquals(Set.of(Group.SYSTEM), Group.of(item("admin", "Settings")));
    }

    @Test
    void withoutCategoriesTheSectionDecides() {
        assertEquals(Set.of(Group.GAMES), Group.of(item("games")));
        assertEquals(Set.of(Group.AUDIO_VIDEO), Group.of(item("video")));
        assertEquals(Set.of(Group.OFFICE), Group.of(item("editors")));
        assertEquals(Set.of(Group.OTHER), Group.of(item("x11")));
        // Only additional categories, no main one: the section.
        assertEquals(Set.of(Group.GRAPHICS), Group.of(item("graphics", "RasterGraphics")));
    }
}
