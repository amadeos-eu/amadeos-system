package eu.amadeos.system.base.shortcuts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The keys of the desktop shortcuts: the built-in actions (a missing key means switched off) and the user's own
 * commands. Immutable; the {@code with} methods return a changed copy.
 */
public final class Shortcuts {
    public static final Shortcuts DEFAULTS = new Shortcuts(defaultBindings(), List.of());

    private final Map<ShortcutAction, Optional<KeyCombo>> bindings;
    private final List<CustomShortcut> custom;

    public Shortcuts(Map<ShortcutAction, Optional<KeyCombo>> bindings, List<CustomShortcut> custom) {
        EnumMap<ShortcutAction, Optional<KeyCombo>> copy = defaultBindings();
        copy.putAll(bindings);
        this.bindings = Collections.unmodifiableMap(copy);
        this.custom = List.copyOf(custom);
    }

    private static EnumMap<ShortcutAction, Optional<KeyCombo>> defaultBindings() {
        EnumMap<ShortcutAction, Optional<KeyCombo>> map = new EnumMap<>(ShortcutAction.class);
        for (ShortcutAction a : ShortcutAction.values()) map.put(a, Optional.of(a.defaultKeys()));
        return map;
    }

    /** The keys of an action, empty if it is switched off. */
    public Optional<KeyCombo> keys(ShortcutAction action) {
        return bindings.get(action);
    }

    public List<CustomShortcut> custom() {
        return custom;
    }

    public Shortcuts with(ShortcutAction action, Optional<KeyCombo> keys) {
        EnumMap<ShortcutAction, Optional<KeyCombo>> copy = new EnumMap<>(bindings);
        copy.put(action, keys);
        return new Shortcuts(copy, custom);
    }

    public Shortcuts withCustom(List<CustomShortcut> value) {
        return new Shortcuts(bindings, value);
    }

    /**
     * What already uses the keys: an action, a custom shortcut (by its index) or one of the fixed keys of the
     * window manager. Empty if they are free.
     */
    public Optional<Owner> owner(KeyCombo keys) {
        if (ShortcutAction.FIXED.contains(keys)) return Optional.of(new Owner.Fixed());
        for (ShortcutAction a : ShortcutAction.values()) {
            if (bindings.get(a).filter(keys::equals).isPresent()) return Optional.of(new Owner.Action(a));
        }
        for (int i = 0; i < custom.size(); i++) {
            if (custom.get(i).keys().equals(keys)) return Optional.of(new Owner.Custom(i));
        }
        return Optional.empty();
    }

    /** Takes the keys away from whatever uses them, so they can go elsewhere; custom shortcuts lose theirs entirely. */
    public Shortcuts release(KeyCombo keys) {
        Optional<Owner> owner = owner(keys);
        if (owner.isEmpty()) return this;
        return switch (owner.get()) {
            case Owner.Action a -> with(a.action(), Optional.empty());
            case Owner.Custom c -> {
                List<CustomShortcut> list = new ArrayList<>(custom);
                list.remove(c.index());
                yield withCustom(list);
            }
            case Owner.Fixed f -> this;
        };
    }

    /** Who holds a key combination; see {@link #owner}. */
    public sealed interface Owner {
        record Action(ShortcutAction action) implements Owner {
        }

        record Custom(int index) implements Owner {
        }

        record Fixed() implements Owner {
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Shortcuts s && bindings.equals(s.bindings) && custom.equals(s.custom);
    }

    @Override
    public int hashCode() {
        return bindings.hashCode() * 31 + custom.hashCode();
    }

    @Override
    public String toString() {
        return "Shortcuts" + bindings + custom;
    }
}
