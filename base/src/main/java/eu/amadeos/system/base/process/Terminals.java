package eu.amadeos.system.base.process;

import java.util.List;
import java.util.function.Predicate;

/**
 * The terminal program of the desktop: {@code $TERMINAL} if set, otherwise amade-terminal, the Debian
 * alternative {@code x-terminal-emulator}, or xterm.
 */
public final class Terminals {
    public static final String AMADE_TERMINAL = "amade-terminal";
    static final List<String> PREFERRED = List.of(AMADE_TERMINAL, "x-terminal-emulator", "xterm");

    private Terminals() {
    }

    /** The terminal to start. */
    public static String program() {
        return program(System.getenv("TERMINAL"), ProcessLauncher::isOnPath);
    }

    static String program(String environment, Predicate<String> installed) {
        if (environment != null && !environment.isBlank()) return environment.strip();
        for (String candidate : PREFERRED) {
            if (installed.test(candidate)) return candidate;
        }
        return "xterm";
    }

    /** A command in a terminal window with a title ({@code -title} is understood by xterm and amade-terminal). */
    public static List<String> command(String title, List<String> command) {
        return command(program(), title, command);
    }

    static List<String> command(String terminal, String title, List<String> command) {
        List<String> result = new java.util.ArrayList<>();
        result.add(terminal);
        if (title != null && !title.isBlank()) {
            result.add("-title");
            result.add(title);
        }
        if (!command.isEmpty()) {
            result.add("-e");
            result.addAll(command);
        }
        return List.copyOf(result);
    }
}
