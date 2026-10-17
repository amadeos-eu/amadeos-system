package org.datazuul.amadeos.wm.xdg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parser for Freedesktop.org Desktop Entry files (.desktop).
 */
public final class XdgDesktopEntryParser {

    private XdgDesktopEntryParser() {}

    public static Optional<XdgDesktopEntry> parse(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(path)) {
            String fileName = path.getFileName().toString();
            return parse(in, fileName, path);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<XdgDesktopEntry> parse(InputStream in, String id, Path path) throws IOException {
        String currentSection = null;
        boolean inDesktopEntry = false;

        Map<String, String> values = new HashMap<>();
        Map<String, String> localizedNames = new HashMap<>();
        Map<String, String> localizedComments = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim();
                    inDesktopEntry = "Desktop Entry".equalsIgnoreCase(currentSection);
                    continue;
                }
                if (!inDesktopEntry) {
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq <= 0) continue;

                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();

                if (key.startsWith("Name[")) {
                    String lang = extractLocale(key);
                    localizedNames.put(lang, val);
                } else if (key.startsWith("Comment[")) {
                    String lang = extractLocale(key);
                    localizedComments.put(lang, val);
                } else {
                    values.put(key, val);
                }
            }
        }

        // Must be Type=Application (or unspecified defaulting to Application)
        String type = values.getOrDefault("Type", "Application");
        if (!"Application".equalsIgnoreCase(type)) {
            return Optional.empty();
        }

        String currentLang = Locale.getDefault().getLanguage().toLowerCase();
        String currentLocale = Locale.getDefault().toString().toLowerCase(); // e.g. de_de

        String name = localizedNames.get(currentLocale);
        if (name == null) name = localizedNames.get(currentLang);
        if (name == null) name = values.get("Name");
        if (name == null || name.isBlank()) {
            name = (id != null && id.endsWith(".desktop")) ? id.substring(0, id.length() - 8) : id;
        }

        String comment = localizedComments.get(currentLocale);
        if (comment == null) comment = localizedComments.get(currentLang);
        if (comment == null) comment = values.getOrDefault("Comment", "");

        String genericName = values.getOrDefault("GenericName", "");
        String exec = values.getOrDefault("Exec", "");
        String icon = values.getOrDefault("Icon", "window");
        String tryExec = values.getOrDefault("TryExec", "");
        boolean terminal = "true".equalsIgnoreCase(values.get("Terminal"));
        boolean noDisplay = "true".equalsIgnoreCase(values.get("NoDisplay"));
        boolean hidden = "true".equalsIgnoreCase(values.get("Hidden"));

        List<String> categories = parseList(values.get("Categories"));
        List<String> mimeTypes = parseList(values.get("MimeType"));
        List<String> onlyShowIn = parseList(values.get("OnlyShowIn"));
        List<String> notShowIn = parseList(values.get("NotShowIn"));

        return Optional.of(new XdgDesktopEntry(
                id, name, genericName, comment, icon, exec,
                terminal, categories, mimeTypes, noDisplay, hidden,
                onlyShowIn, notShowIn, tryExec, path
        ));
    }

    private static String extractLocale(String key) {
        int start = key.indexOf('[');
        int end = key.indexOf(']');
        if (start >= 0 && end > start) {
            return key.substring(start + 1, end).trim().toLowerCase();
        }
        return "";
    }

    private static List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : raw.split(";")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return Collections.unmodifiableList(result);
    }
}

