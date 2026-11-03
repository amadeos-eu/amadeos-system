package eu.amadeos.system.base.shortcuts;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a desktop shortcut does, with its key out of the box. The window manager grabs the keys and carries the
 * actions out; Settings &gt; Keyboard Shortcuts changes the keys. The names are {@code shortcut.<id>} in the
 * Settings' texts.
 */
public enum ShortcutAction {
    // --8<-- [start:actions]
    LOCK_SCREEN("lock", Group.SESSION, "Super+L"),
    TERMINAL("terminal", Group.PROGRAMS, "Ctrl+Alt+T"),
    FILES("files", Group.PROGRAMS, "Super+E"),
    SETTINGS("settings", Group.PROGRAMS, "Super+I"),
    TASK_MANAGER("taskmanager", Group.PROGRAMS, "Ctrl+Shift+Escape"),
    SEARCH("search", Group.PROGRAMS, "Super+space"),
    CLIPBOARD("clipboard", Group.PROGRAMS, "Ctrl+Alt+V"),
    SCREENSHOT("screenshot", Group.PROGRAMS, "Print"),
    SCREENSHOT_AREA("screenshot.area", Group.PROGRAMS, "Shift+Print"),
    CLOSE_WINDOW("close", Group.WINDOWS, "Alt+F4"),
    MAXIMIZE_WINDOW("maximize", Group.WINDOWS, "Super+Up"),
    MINIMIZE_WINDOW("minimize", Group.WINDOWS, "Super+Down"),
    SHOW_DESKTOP("showdesktop", Group.WINDOWS, "Super+D"),
    WORKSPACE_1("workspace.1", Group.WORKSPACES, "Super+1"),
    WORKSPACE_2("workspace.2", Group.WORKSPACES, "Super+2"),
    WORKSPACE_3("workspace.3", Group.WORKSPACES, "Super+3"),
    WORKSPACE_4("workspace.4", Group.WORKSPACES, "Super+4"),
    WORKSPACE_PREVIOUS("workspace.previous", Group.WORKSPACES, "Ctrl+Alt+Left"),
    WORKSPACE_NEXT("workspace.next", Group.WORKSPACES, "Ctrl+Alt+Right");
    // --8<-- [end:actions]

    /** The headings of the list in the Settings. */
    public enum Group {
        SESSION, PROGRAMS, WINDOWS, WORKSPACES, CUSTOM;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Keys the window manager keeps for itself: the window switcher (Alt+Tab and Alt+Shift+Tab hold Alt while
     * it is open) and the Task Manager's Ctrl+Alt+Delete, which a user who is stuck must always find.
     */
    public static final List<KeyCombo> FIXED = List.of(
            KeyCombo.of(KeyCombo.ALT, "Tab"),
            KeyCombo.of(KeyCombo.ALT | KeyCombo.SHIFT, "Tab"),
            KeyCombo.of(KeyCombo.CTRL | KeyCombo.ALT, "Delete"));

    private final String id;
    private final Group group;
    private final KeyCombo defaultKeys;

    ShortcutAction(String id, Group group, String defaultKeys) {
        this.id = id;
        this.group = group;
        this.defaultKeys = KeyCombo.parse(defaultKeys).orElseThrow();
    }

    /** The key in {@code shortcuts.conf} and in the texts, such as {@code terminal}. */
    public String id() {
        return id;
    }

    public Group group() {
        return group;
    }

    public KeyCombo defaultKeys() {
        return defaultKeys;
    }

    public static Optional<ShortcutAction> byId(String id) {
        for (ShortcutAction a : values()) {
            if (a.id.equals(id)) return Optional.of(a);
        }
        return Optional.empty();
    }

    /** The workspace (0 to 3) a WORKSPACE_n action switches to, or -1. */
    public int workspace() {
        return switch (this) {
            case WORKSPACE_1 -> 0;
            case WORKSPACE_2 -> 1;
            case WORKSPACE_3 -> 2;
            case WORKSPACE_4 -> 3;
            default -> -1;
        };
    }

    /**
     * The program a PROGRAMS action starts, empty for the actions the window manager does itself (search goes to
     * Horizon's top bar over a socket, the terminal is the one chosen in Terminals).
     */
    public List<String> command() {
        return switch (this) {
            case FILES -> List.of("amade-files");
            case SETTINGS -> List.of("amadeos-settings");
            case TASK_MANAGER -> List.of("amadeos-taskmanager");
            case CLIPBOARD -> List.of("amadeos-clipboard", "--show");
            case SCREENSHOT -> List.of("amade-screenshot", "--desktop");
            case SCREENSHOT_AREA -> List.of("amade-screenshot", "--area");
            default -> List.of();
        };
    }
}
