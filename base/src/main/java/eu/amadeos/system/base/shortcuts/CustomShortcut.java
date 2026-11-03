package eu.amadeos.system.base.shortcuts;

/**
 * A shortcut the user added: a name for the list, the command the window manager runs through {@code sh -c},
 * and its keys.
 */
public record CustomShortcut(String name, String command, KeyCombo keys) {
    public CustomShortcut {
        name = name == null ? "" : name.strip();
        command = command == null ? "" : command.strip();
        if (command.isEmpty()) throw new IllegalArgumentException("no command");
        if (name.isEmpty()) name = command;
    }
}
