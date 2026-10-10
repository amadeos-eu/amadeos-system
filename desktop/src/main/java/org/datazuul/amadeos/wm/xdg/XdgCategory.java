package org.datazuul.amadeos.wm.xdg;

import java.util.List;
import java.util.Set;

/**
 * Standard Freedesktop.org application categories mapped to amadeOS CDE Front Panel drawers.
 */
public enum XdgCategory {
    FILE_MANAGER("FileManager", "File Management", "file-manager", Set.of("filemanager", "filetools")),
    TEXT_EDITOR("TextEditor", "Text Editors", "text-editor", Set.of("texteditor", "wordprocessor")),
    TERMINAL("TerminalEmulator", "Terminals", "terminal", Set.of("terminalemulator", "terminal")),
    MONITOR("Monitor", "System & Monitor", "diagnostics", Set.of("monitor", "system", "hardwaresettings", "systemmonitor")),
    SETTINGS("Settings", "Settings & Tools", "window", Set.of("settings", "desktopsettings", "preferences", "packagemanager")),
    UTILITY("Utility", "Accessories", "window", Set.of("utility", "accessories")),
    DEVELOPMENT("Development", "Development", "terminal", Set.of("development", "ide", "debugger")),
    GRAPHICS("Graphics", "Graphics", "window", Set.of("graphics", "viewer", "rastergraphics", "2dgraphics")),
    NETWORK("Network", "Internet & Network", "window", Set.of("network", "webbrowser", "email", "chat")),
    AUDIO_VIDEO("AudioVideo", "Sound & Video", "window", Set.of("audiovideo", "audio", "video", "player", "recorder")),
    OTHER("Other", "Applications", "window", Set.of());

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

    public boolean matches(List<String> entryCategories) {
        if (entryCategories == null || entryCategories.isEmpty()) {
            return this == OTHER;
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

