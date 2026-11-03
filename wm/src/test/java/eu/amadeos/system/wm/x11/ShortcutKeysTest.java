package eu.amadeos.system.wm.x11;

import eu.amadeos.system.base.shortcuts.CustomShortcut;
import eu.amadeos.system.base.shortcuts.KeyCombo;
import eu.amadeos.system.base.shortcuts.ShortcutAction;
import eu.amadeos.system.base.shortcuts.Shortcuts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShortcutKeysTest {
    private static final Map<String, Integer> KEYCODES = Map.of("l", 46, "t", 28, "c", 54, "Print", 107);
    private static final int NUM_LOCK = 1 << 4;
    private static final int CAPS_LOCK = 1 << 1;

    @Test
    void keyPressesFindTheirActionWhateverTheLocks() {
        ShortcutKeys keys = ShortcutKeys.of(Shortcuts.DEFAULTS, k -> KEYCODES.getOrDefault(k, 0));
        assertEquals(Optional.of(new ShortcutKeys.Target.Action(ShortcutAction.LOCK_SCREEN)),
                keys.target(46, KeyCombo.SUPER | NUM_LOCK | CAPS_LOCK));
        assertEquals(Optional.of(new ShortcutKeys.Target.Action(ShortcutAction.SCREENSHOT)), keys.target(107, 0));
        assertEquals(Optional.empty(), keys.target(46, KeyCombo.SUPER | KeyCombo.SHIFT));
        // Keys the keyboard does not have are not grabbed.
        assertEquals(4, keys.grabs().size());
    }

    @Test
    void customCommandsAndSwitchedOffActions() {
        Shortcuts s = Shortcuts.DEFAULTS.with(ShortcutAction.TERMINAL, Optional.empty())
                .withCustom(List.of(new CustomShortcut("Calc", "amade-calculator", KeyCombo.parse("Super+C").orElseThrow())));
        ShortcutKeys keys = ShortcutKeys.of(s, k -> KEYCODES.getOrDefault(k, 0));
        assertEquals(Optional.empty(), keys.target(28, KeyCombo.CTRL | KeyCombo.ALT));
        assertEquals(Optional.of(new ShortcutKeys.Target.Command("amade-calculator")), keys.target(54, KeyCombo.SUPER));
    }
}
