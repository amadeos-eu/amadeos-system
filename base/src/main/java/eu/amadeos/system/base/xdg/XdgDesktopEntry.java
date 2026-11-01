package eu.amadeos.system.base.xdg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import eu.amadeos.system.base.process.ProcessLauncher;

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
        String tryExec,
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
        tryExec = Objects.requireNonNullElse(tryExec, "");
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
     * Whether the program of this entry is installed: TryExec if given (desktop entry spec), else the
     * first word of Exec. Debian's vim.desktop, for example, comes with vim-common although vim-tiny
     * provides no /usr/bin/vim.
     */
    public boolean isInstalled() {
        String program = !tryExec.isBlank() ? tryExec.strip() : firstWord(exec);
        return program.isEmpty() || isExecutable(program);
    }

    private static String firstWord(String exec) {
        List<String> tokens = tokenize(exec);
        return tokens.isEmpty() ? "" : tokens.get(0);
    }

    /** Whether {@code program} (a path, or a name looked up in PATH) is an executable file. */
    static boolean isExecutable(String program) {
        if (program.contains("/")) {
            return Files.isExecutable(Path.of(program));
        }
        String pathEnv = System.getenv("PATH");
        String searchPath = pathEnv != null && !pathEnv.isBlank() ? pathEnv : "/usr/local/bin:/usr/bin:/bin";
        for (String dir : searchPath.split(":")) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, program))) {
                return true;
            }
        }
        return false;
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
            return eu.amadeos.system.base.process.Terminals.command(name, processedTokens);
        }

        return Collections.unmodifiableList(processedTokens);
    }

    public Process launch(String... extraArgs) throws IOException {
        List<String> cmd = buildCommandLine(extraArgs);
        if (cmd.isEmpty()) {
            throw new IOException("Cannot launch application '" + name + "': empty Exec command");
        }
        LOGGER.info(() -> "Launching XDG application [" + name + "]: " + String.join(" ", cmd));
        return ProcessLauncher.start(cmd);
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

