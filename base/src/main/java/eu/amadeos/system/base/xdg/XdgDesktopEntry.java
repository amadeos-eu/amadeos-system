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
        Path filePath,
        String startupWmClass
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
        startupWmClass = Objects.requireNonNullElse(startupWmClass, "");
        categories = (categories != null) ? List.copyOf(categories) : List.of();
        mimeTypes = (mimeTypes != null) ? List.copyOf(mimeTypes) : List.of();
        onlyShowIn = (onlyShowIn != null) ? List.copyOf(onlyShowIn) : List.of();
        notShowIn = (notShowIn != null) ? List.copyOf(notShowIn) : List.of();
    }

    /** An entry without {@code StartupWMClass}. */
    public XdgDesktopEntry(String id, String name, String genericName, String comment, String icon, String exec,
                           boolean terminal, List<String> categories, List<String> mimeTypes, boolean noDisplay,
                           boolean hidden, List<String> onlyShowIn, List<String> notShowIn, String tryExec,
                           Path filePath) {
        this(id, name, genericName, comment, icon, exec, terminal, categories, mimeTypes, noDisplay, hidden,
                onlyShowIn, notShowIn, tryExec, filePath, "");
    }

    // --8<-- [start:program]
    /**
     * The program the entry really starts, without its directory: the first word of Exec, but for
     * {@code env VAR=value program} the program after the variables, and for {@code flatpak run app.id} its
     * {@code --command}, else the last part of the application id ({@code org.mozilla.firefox} gives
     * {@code firefox}). Snap entries start
     * {@code /snap/bin/firefox} or {@code env BAMF_DESKTOP_FILE_HINT=… /snap/bin/firefox}, so they give
     * {@code firefox} too.
     */
    public String program() {
        List<String> tokens = tokenize(exec);
        int i = 0;
        if (i < tokens.size() && baseName(tokens.get(i)).equals("env")) {
            i++;
            while (i < tokens.size() && (tokens.get(i).startsWith("-") || tokens.get(i).contains("="))) i++;
        }
        if (i >= tokens.size()) return "";
        String program = baseName(tokens.get(i));
        if (program.equals("flatpak")) {
            for (int j = i + 1; j < tokens.size(); j++) {
                if (tokens.get(j).startsWith("--command=")) return baseName(tokens.get(j).substring("--command=".length()));
            }
            for (int j = i + 1; j < tokens.size(); j++) {
                String t = tokens.get(j);
                if (t.equals("run") || t.startsWith("-") || t.startsWith("@@")) continue;
                int dot = t.lastIndexOf('.');
                return dot >= 0 ? t.substring(dot + 1) : t;
            }
        }
        return program;
    }
    // --8<-- [end:program]

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
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

