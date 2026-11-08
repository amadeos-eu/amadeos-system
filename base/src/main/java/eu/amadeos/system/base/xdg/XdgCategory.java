package eu.amadeos.system.base.xdg;

import java.util.List;
import java.util.Set;

/**
 * Standard Freedesktop.org application categories mapped to amadeOS CDE Front Panel drawers.
 */
public enum XdgCategory {
    // --8<-- [start:categories]
    FILE_MANAGER("FileManager", "File Management", "file-manager", Set.of("filemanager", "filetools")),
    TEXT_EDITOR("TextEditor", "Text Editors", "text-editor", Set.of("texteditor", "wordprocessor")),
    OFFICE("Office", "Office", "text-editor", Set.of("office", "spreadsheet", "presentation", "calendar",
            "projectmanagement", "chart", "database", "finance", "flowchart", "publishing", "dictionary")),
    TERMINAL("TerminalEmulator", "Terminals", "terminal", Set.of("terminalemulator", "terminal")),
    MAIL("Email", "Electronic Mail", "mail", Set.of("email", "mail", "contactmanagement", "news")),
    PRINTING("Printing", "Printers", "printer", Set.of("printing", "hardwareprinting", "printer", "cups")),
    HELP("Help", "Help & Information", "help", Set.of("documentation", "help", "systemhelp")),
    MONITOR("Monitor", "System & Monitor", "diagnostics", Set.of("monitor", "system", "hardwaresettings", "systemmonitor")),
    SETTINGS("Settings", "Settings & Tools", "style", Set.of("settings", "desktopsettings", "preferences", "packagemanager")),
    UTILITY("Utility", "Accessories", "app-manager", Set.of("utility", "accessories", "accessibility", "archiving",
            "compression", "calculator", "clock")),
    DEVELOPMENT("Development", "Development", "terminal", Set.of("development", "ide", "debugger")),
    GRAPHICS("Graphics", "Graphics", "graphics", Set.of("graphics", "viewer", "rastergraphics", "2dgraphics", "vectorgraphics", "3dgraphics", "photography", "scanning")),
    NETWORK("Network", "Internet & Network", "window", Set.of("network", "webbrowser", "chat")),
    AUDIO_VIDEO("AudioVideo", "Sound & Video", "multimedia", Set.of("audiovideo", "audio", "video", "player", "recorder")),
    GAMES("Game", "Games", "games", Set.of("game", "actiongame", "adventuregame", "arcadegame", "boardgame", "blocksgame",
            "cardgame", "kidsgame", "logicgame", "roleplaying", "shooter", "sportsgame", "strategygame")),
    OTHER("Other", "Applications", "app-manager", Set.of());
    // --8<-- [end:categories]

    private final String key;
    private final String displayName;
    private final String iconKey;
    private final Set<String> matchingTokens;

    XdgCategory(String key, String displayName, String iconKey, Set<String> matchingTokens) {
        this.key = key;
        this.displayName = displayName;
        this.iconKey = iconKey;
        this.matchingTokens = matchingTokens;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String iconKey() {
        return iconKey;
    }

    /**
     * Whether a program with these categories belongs to this group. {@link #OTHER} takes every program that
     * belongs to no other group, such as Education or Science programs, or those that only name their
     * toolkit ({@code Qt;KDE;}), so each installed program is in some drawer and menu.
     */
    public boolean matches(List<String> entryCategories) {
        if (this == OTHER) {
            for (XdgCategory c : values()) {
                if (c != OTHER && c.matches(entryCategories)) return false;
            }
            return true;
        }
        if (entryCategories == null || entryCategories.isEmpty()) {
            return false;
        }
        for (String cat : entryCategories) {
            String normalized = cat.trim().toLowerCase();
            if (normalized.equals(key.toLowerCase()) || matchingTokens.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
}

