package eu.amadeos.system.base.shortcuts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutsTest {

    @Test
    void combinationsAreReadAndWrittenInOneOrder() {
        KeyCombo k = KeyCombo.parse("super+alt+ctrl+t").orElseThrow();
        assertEquals(KeyCombo.CTRL | KeyCombo.ALT | KeyCombo.SUPER, k.modifiers());
        assertEquals("t", k.key());
        assertEquals("Ctrl+Alt+Super+T", k.text());
        assertEquals("Ctrl+Shift+Escape", KeyCombo.parse("Shift+Control+Escape").orElseThrow().text());
        assertEquals("Ctrl+plus", KeyCombo.parse("Ctrl++").orElseThrow().text());
        assertEquals(Optional.empty(), KeyCombo.parse("none"));
        assertEquals(Optional.empty(), KeyCombo.parse("Hyper+X"));
    }

    @Test
    void onlySpecialKeysStandAlone() {
        assertTrue(KeyCombo.parse("Print").orElseThrow().standsAlone());
        assertTrue(KeyCombo.parse("F12").orElseThrow().standsAlone());
        assertTrue(KeyCombo.parse("XF86Calculator").orElseThrow().standsAlone());
        assertFalse(KeyCombo.parse("a").orElseThrow().standsAlone());
        assertFalse(KeyCombo.parse("F25").orElseThrow().standsAlone());
    }

    @Test
    void ownersAndRelease() {
        KeyCombo t = KeyCombo.parse("Ctrl+Alt+T").orElseThrow();
        Shortcuts s = Shortcuts.DEFAULTS.withCustom(List.of(new CustomShortcut("Calc", "amade-calculator",
                KeyCombo.parse("Super+C").orElseThrow())));
        assertEquals(Optional.of(new Shortcuts.Owner.Action(ShortcutAction.TERMINAL)), s.owner(t));
        assertEquals(Optional.of(new Shortcuts.Owner.Custom(0)), s.owner(KeyCombo.parse("Super+C").orElseThrow()));
        assertEquals(Optional.of(new Shortcuts.Owner.Fixed()), s.owner(KeyCombo.parse("Alt+Tab").orElseThrow()));
        assertEquals(Optional.empty(), s.release(t).keys(ShortcutAction.TERMINAL));
        assertEquals(List.of(), s.release(KeyCombo.parse("Super+C").orElseThrow()).custom());
    }

    @Test
    void defaultsAreUniqueAndNotFixed() {
        for (ShortcutAction a : ShortcutAction.values()) {
            assertEquals(Optional.of(new Shortcuts.Owner.Action(a)), Shortcuts.DEFAULTS.owner(a.defaultKeys()), a.id());
        }
    }

    @Test
    void storeWritesOnlyChanges(@TempDir Path dir) throws Exception {
        ShortcutStore store = new ShortcutStore(dir.resolve(ShortcutStore.FILE_NAME));
        assertEquals(Shortcuts.DEFAULTS, store.load());
        Shortcuts changed = Shortcuts.DEFAULTS
                .with(ShortcutAction.TERMINAL, KeyCombo.parse("Super+Return"))
                .with(ShortcutAction.SEARCH, Optional.empty())
                .withCustom(List.of(new CustomShortcut("Calculator", "amade-calculator --mode scientific",
                        KeyCombo.parse("Super+C").orElseThrow())));
        store.save(changed);
        String text = Files.readString(store.file());
        assertTrue(text.contains("terminal=Super+Return"), text);
        assertTrue(text.contains("search=none"), text);
        assertFalse(text.contains("lock="), text);
        assertEquals(changed, store.load());
    }
}
