package eu.amadeos.system.wm.x11;

import eu.amadeos.system.base.shortcuts.CustomShortcut;
import eu.amadeos.system.base.shortcuts.KeyCombo;
import eu.amadeos.system.base.shortcuts.ShortcutAction;
import eu.amadeos.system.base.shortcuts.Shortcuts;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

// --8<-- [start:shortcut-keys]
/**
 * The desktop shortcuts as the window manager grabs them: each key combination of {@code shortcuts.conf} turned
 * into a key code of the keyboard and its modifier mask, with what it does. Pure, so it can be tested without an
 * X server; the key codes come from a function ({@code XKeysymToKeycode} in the window manager).
 */
final class ShortcutKeys {

    /** A key code with the modifiers Shift, Control, Mod1 (Alt) and Mod4 (Super). */
    record Grab(int keycode, int modifiers) {
    }

    /** What a grabbed key does: a built-in action or a command of the user. */
    sealed interface Target {
        record Action(ShortcutAction action) implements Target {
        }

        record Command(String command) implements Target {
        }
    }

    private final Map<Grab, Target> grabs;

    private ShortcutKeys(Map<Grab, Target> grabs) {
        this.grabs = Map.copyOf(grabs);
    }

    /**
     * @param keycodes the key code of a keysym name such as "t" or "Print", 0 if the keyboard has no such key
     */
    static ShortcutKeys of(Shortcuts shortcuts, ToIntFunction<String> keycodes) {
        Map<Grab, Target> grabs = new LinkedHashMap<>();
        for (ShortcutAction action : ShortcutAction.values()) {
            shortcuts.keys(action).flatMap(k -> grab(k, keycodes))
                    .ifPresent(g -> grabs.putIfAbsent(g, new Target.Action(action)));
        }
        for (CustomShortcut custom : shortcuts.custom()) {
            grab(custom.keys(), keycodes).ifPresent(g -> grabs.putIfAbsent(g, new Target.Command(custom.command())));
        }
        return new ShortcutKeys(grabs);
    }

    static ShortcutKeys none() {
        return new ShortcutKeys(Map.of());
    }

    private static Optional<Grab> grab(KeyCombo keys, ToIntFunction<String> keycodes) {
        int keycode = keycodes.applyAsInt(keys.key());
        return keycode == 0 ? Optional.empty() : Optional.of(new Grab(keycode, keys.modifiers()));
    }

    /** All grabs, to pass to XGrabKey (and XUngrabKey when the shortcuts change). */
    Map<Grab, Target> grabs() {
        return grabs;
    }

    /** What a key press does; Num Lock, Caps Lock and the mouse buttons in its state do not count. */
    Optional<Target> target(int keycode, int state) {
        return Optional.ofNullable(grabs.get(new Grab(keycode, state & KeyCombo.ALL_MODIFIERS)));
    }
}
// --8<-- [end:shortcut-keys]
