package eu.amadeos.system.base.shortcuts;

import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * A key with the modifiers held down, such as Ctrl+Alt+T. The modifiers are X11's masks (Shift, Control, Mod1 =
 * Alt, Mod4 = Super), so the window manager can grab them as they are; the key is an X keysym name such as
 * {@code t}, {@code Escape}, {@code space}, {@code Print} or {@code F4}, as {@code XStringToKeysym} knows it.
 *
 * <p>In {@code shortcuts.conf} and on screen a combination is written {@code Ctrl+Alt+T}: the modifiers in the
 * order Ctrl, Alt, Shift, Super, then the key, a letter in capitals.
 */
public record KeyCombo(int modifiers, String key) {
    public static final int SHIFT = 1;
    public static final int CTRL = 1 << 2;
    public static final int ALT = 1 << 3;
    public static final int SUPER = 1 << 6;
    public static final int ALL_MODIFIERS = SHIFT | CTRL | ALT | SUPER;

    public KeyCombo {
        modifiers &= ALL_MODIFIERS;
        if (key == null || key.isBlank()) throw new IllegalArgumentException("no key");
        key = key.strip();
        // Letters are kept small, as their keysym names are: "t", not "T" (which XStringToKeysym makes Shift+t).
        if (key.length() == 1) key = key.toLowerCase(Locale.ROOT);
    }

    public static KeyCombo of(int modifiers, String key) {
        return new KeyCombo(modifiers, key);
    }

    /**
     * Reads {@code Ctrl+Alt+T}, {@code Super+space} or {@code Print}; empty for {@code none}, an empty text or
     * something that is not a combination.
     */
    public static Optional<KeyCombo> parse(String text) {
        if (text == null) return Optional.empty();
        String s = text.strip();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) return Optional.empty();
        // "Ctrl++" would be Ctrl and the plus key: the last part is the key, whatever it is.
        int split = s.endsWith("+") && s.length() > 1 ? s.length() - 1 : s.lastIndexOf('+') + 1;
        String key = s.substring(split);
        int modifiers = 0;
        if (split > 0) {
            for (String part : s.substring(0, split - 1).split("\\+")) {
                int mask = modifier(part.strip());
                if (mask == 0) return Optional.empty();
                modifiers |= mask;
            }
        }
        if (key.isBlank()) return Optional.empty();
        return Optional.of(new KeyCombo(modifiers, key.equals("+") ? "plus" : key));
    }

    private static int modifier(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "ctrl", "control" -> CTRL;
            case "alt", "mod1" -> ALT;
            case "shift" -> SHIFT;
            case "super", "win", "mod4", "meta" -> SUPER;
            default -> 0;
        };
    }

    /** The modifier keys themselves, which make no combination on their own. */
    public static boolean isModifierKey(String keysym) {
        return switch (keysym) {
            case "Shift_L", "Shift_R", "Control_L", "Control_R", "Alt_L", "Alt_R", "Meta_L", "Meta_R", "Super_L",
                 "Super_R", "Hyper_L", "Hyper_R", "ISO_Level3_Shift", "Caps_Lock", "Num_Lock", "Mode_switch" -> true;
            default -> false;
        };
    }

    /**
     * True for keys that may be a shortcut without a modifier: the function keys, Print, Pause and the special
     * keys of multimedia keyboards, also with Shift. A bare or shifted letter would be taken away from every program.
     */
    public boolean standsAlone() {
        if ((modifiers & (CTRL | ALT | SUPER)) != 0) return true;
        return key.matches("F([1-9]|1[0-9]|2[0-4])") || key.equals("Print") || key.equals("Pause")
                || key.equals("Scroll_Lock") || key.startsWith("XF86");
    }

    /** {@code Ctrl+Alt+T}, as written in {@code shortcuts.conf}. */
    public String text() {
        StringJoiner joiner = new StringJoiner("+");
        if ((modifiers & CTRL) != 0) joiner.add("Ctrl");
        if ((modifiers & ALT) != 0) joiner.add("Alt");
        if ((modifiers & SHIFT) != 0) joiner.add("Shift");
        if ((modifiers & SUPER) != 0) joiner.add("Super");
        joiner.add(key.length() == 1 ? key.toUpperCase(Locale.ROOT) : key);
        return joiner.toString();
    }

    @Override
    public String toString() {
        return text();
    }
}
