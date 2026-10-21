package eu.amadeos.system.dm.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyboardLayoutsTest {
    private static final String EVDEV = """
            ! model
              pc105           Generic 105-key PC
            ! layout
              us              English (US)
              de              German
              fr              French
            ! variant
              nodeadkeys      de: German (no dead keys)
            """;

    @Test
    void readsTheLayoutSectionOfEvdevLst() {
        assertEquals(List.of(new KeyboardLayouts.Layout("us", "English (US)"), new KeyboardLayouts.Layout("de", "German"),
                new KeyboardLayouts.Layout("fr", "French")), KeyboardLayouts.parse(EVDEV));
        assertEquals("German (de)", new KeyboardLayouts.Layout("de", "German").toString());
    }

    @Test
    void fallsBackToABuiltInListWithoutEvdevLst(@TempDir Path root) {
        List<KeyboardLayouts.Layout> layouts = new KeyboardLayouts(root).list();
        assertTrue(KeyboardLayouts.find(layouts, "gb").isPresent());
        assertTrue(KeyboardLayouts.find(layouts, "de").isPresent());
        assertFalse(KeyboardLayouts.find(layouts, "xx").isPresent());
    }

    @Test
    void takesTheFirstLayoutAndVariantOfTheComputer() {
        assertEquals(new KeyboardLayouts.SystemLayout("fr", ""), KeyboardLayouts.parseDefaults("""
                XKBMODEL="pc105"
                XKBLAYOUT="fr,us"
                XKBVARIANT=""
                """));
        assertEquals(new KeyboardLayouts.SystemLayout("de", "nodeadkeys"), KeyboardLayouts.parseDefaults(
                "XKBLAYOUT=de\nXKBVARIANT=\"nodeadkeys\"\n"));
        assertEquals(new KeyboardLayouts.SystemLayout("de", ""), KeyboardLayouts.parseDefaults("# nothing\n"));
    }

    @Test
    void readsEtcDefaultKeyboardUnderTheRoot(@TempDir Path root) throws IOException {
        assertEquals(new KeyboardLayouts.SystemLayout("de", ""), new KeyboardLayouts(root).system());
        Files.createDirectories(root.resolve("etc/default"));
        Files.writeString(root.resolve(KeyboardLayouts.DEFAULT_KEYBOARD), "XKBLAYOUT=\"gb\"\n");
        Files.createDirectories(root.resolve("usr/share/X11/xkb/rules"));
        Files.writeString(root.resolve(KeyboardLayouts.EVDEV_LIST), EVDEV);
        KeyboardLayouts keyboards = new KeyboardLayouts(root);
        assertEquals(new KeyboardLayouts.SystemLayout("gb", ""), keyboards.system());
        assertEquals(Optional.of(new KeyboardLayouts.Layout("fr", "French")), KeyboardLayouts.find(keyboards.list(), "fr"));
    }
}
