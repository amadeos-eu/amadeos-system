package org.datazuul.amadeos.wm.xdg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Represents an individual Freedesktop.org XDG Desktop Entry (.desktop file).
 */
public record XdgDesktopEntry(
        String id,
        String name,
        String genericName,
        String comment,
        String icon,
        String exec,
        boolean terminal,
        List<String> categories,
        List<String> mimeTypes,
        boolean noDisplay,
        boolean hidden,
        List<String> onlyShowIn,
        List<String> notShowIn,
        Path filePath
) {
    private static final Logger LOGGER = Logger.getLogger(XdgDesktopEntry.class.getName());

    public XdgDesktopEntry {
        id = Objects.requireNonNullElse(id, "");
        name = Objects.requireNonNullElse(name, id);
        genericName = Objects.requireNonNullElse(genericName, "");
        comment = Objects.requireNonNullElse(comment, "");
        icon = Objects.requireNonNullElse(icon, "window");
        exec = Objects.requireNonNullElse(exec, "");
        categories = (categories != null) ? List.copyOf(categories) : List.of();
        mimeTypes = (mimeTypes != null) ? List.copyOf(mimeTypes) : List.of();
        onlyShowIn = (onlyShowIn != null) ? List.copyOf(onlyShowIn) : List.of();
        notShowIn = (notShowIn != null) ? List.copyOf(notShowIn) : List.of();
    }

    public boolean hasCategory(String category) {
        if (category == null || category.isBlank()) return false;
        String lower = category.trim().toLowerCase();
        for (String cat : categories) {
            if (cat.trim().equalsIgnoreCase(lower)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesCategory(XdgCategory category) {
        return category != null && category.matches(categories);
    }

    public boolean isVisible() {
        if (noDisplay || hidden) {
            return false;
        }
        if (!onlyShowIn.isEmpty()) {
            boolean matches = false;
            for (String env : onlyShowIn) {
                if ("amadeos".equalsIgnoreCase(env) || "cde".equalsIgnoreCase(env) || "x-cde".equalsIgnoreCase(env)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) return false;
        }
        if (!notShowIn.isEmpty()) {
            for (String env : notShowIn) {
                if ("amadeos".equalsIgnoreCase(env) || "cde".equalsIgnoreCase(env)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Cleans Exec string by stripping standard Freedesktop field codes:
     * %f, %F, %u, %U, %d, %D, %n, %N, %i, %c, %k, %v, %m
     */
    public List<String> buildCommandLine(String... extraArgs) {
        if (exec.isBlank()) return List.of();

        List<String> rawTokens = tokenize(exec);
        List<String> processedTokens = new ArrayList<>();

        for (String token : rawTokens) {
            String stripped = stripFieldCodes(token);
            if (!stripped.isEmpty()) {
                processedTokens.add(stripped);
            }
        }

        if (extraArgs != null) {
            for (String arg : extraArgs) {
                if (arg != null && !arg.isBlank()) {
                    processedTokens.add(arg);
                }
            }
        }

        if (terminal) {
            List<String> termCmd = new ArrayList<>();
            String termBin = System.getenv("TERMINAL");
            if (termBin == null || termBin.isBlank()) {
                termBin = "xterm";
            }
            termCmd.add(termBin);
            termCmd.add("-title");
            termCmd.add(name);
            termCmd.add("-e");
            termCmd.addAll(processedTokens);
            return Collections.unmodifiableList(termCmd);
        }

        return Collections.unmodifiableList(processedTokens);
    }

    public Process launch(String... extraArgs) throws IOException {
        List<String> cmd = buildCommandLine(extraArgs);
        if (cmd.isEmpty()) {
            throw new IOException("Cannot launch application '" + name + "': empty Exec command");
        }
        LOGGER.info(() -> "Launching XDG application [" + name + "]: " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(System.getProperty("user.home", "/tmp")));
        return pb.start();
    }

    private static String stripFieldCodes(String token) {
        if (token.equals("%f") || token.equals("%F") || token.equals("%u") || token.equals("%U")
                || token.equals("%d") || token.equals("%D") || token.equals("%n") || token.equals("%N")
                || token.equals("%i") || token.equals("%c") || token.equals("%k") || token.equals("%v")
                || token.equals("%m")) {
            return "";
        }
        return token.replaceAll("%[fFuUdDnNickvm]", "").trim();
    }

    public static List<String> tokenize(String cmdLine) {
        List<String> tokens = new ArrayList<>();
        if (cmdLine == null || cmdLine.isBlank()) return tokens;

        StringBuilder sb = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < cmdLine.length(); i++) {
            char c = cmdLine.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }
}

