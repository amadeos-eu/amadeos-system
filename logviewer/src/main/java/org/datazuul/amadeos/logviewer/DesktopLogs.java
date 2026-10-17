package org.datazuul.amadeos.logviewer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.datazuul.amadeos.base.xdg.XdgDirectories;
import org.datazuul.amadeos.base.process.ProcessLauncher;

/**
 * The logs the viewer offers: the session and window manager logs and the per-application
 * logs in {@code ~/.cache/amadeos/logs}, the display manager's logs in {@code /var/log} when readable, and
 * the system journal of this boot.
 */
public final class DesktopLogs {
    /** How much of a log the viewer shows: the end of it. */
    static final int TAIL_BYTES = 256 * 1024;
    static final List<Path> SYSTEM_LOGS = List.of(
            Path.of("/var/log/amadeos-session-runner.log"), Path.of("/var/log/amadeos-xorg.log"));
    private static final List<String> JOURNAL_COMMAND = List.of("journalctl", "-b", "--no-pager", "-n", "2000");
    private static final Map<String, String> TITLES = Map.of(
            "session", "Session",
            "wm", "Window Manager",
            "amadeos-session-runner", "Display Manager: session runner",
            "amadeos-xorg", "Display Manager: X server");
    /** Order of the fixed logs in the list; applications follow alphabetically. */
    private static final List<String> FIRST = List.of("session", "wm");

    /** One entry of the list: a file, or the command that prints the journal (then {@code file} is null). */
    public record Source(String title, Path file, List<String> command) {
        /** The title and, for a file, its path with the home directory as {@code ~}. */
        public String label() {
            if (file == null) {
                return title;
            }
            String home = System.getProperty("user.home", "");
            String path = file.toString();
            if (!home.isEmpty() && path.startsWith(home + "/")) {
                path = "~" + path.substring(home.length());
            }
            return title + "  (" + path + ")";
        }

        /** The end of the log, or the journal; never throws. */
        public String read() {
            if (file != null) {
                return tail(file, TAIL_BYTES);
            }
            return journal(command);
        }

        @Override
        public String toString() {
            return label();
        }
    }

    private DesktopLogs() {
    }

    /** The logs of this desktop, in the order of the list. */
    public static List<Source> available() {
        List<Source> sources = list(XdgDirectories.amadeosLogDir(), SYSTEM_LOGS);
        if (ProcessLauncher.isOnPath(JOURNAL_COMMAND.get(0))) {
            sources.add(new Source("System Journal (this boot)", null, JOURNAL_COMMAND));
        }
        return sources;
    }

    /**
     * The {@code .log} files of the directory (session, window manager, then the applications by name) and
     * the readable ones of the system logs; rotated {@code .old} copies are left out.
     */
    static List<Source> list(Path logDir, List<Path> systemLogs) {
        List<Source> sources = new ArrayList<>();
        if (Files.isDirectory(logDir)) {
            try (Stream<Path> files = Files.list(logDir)) {
                files.filter(f -> f.getFileName().toString().endsWith(".log") && Files.isRegularFile(f))
                        .sorted(Comparator.comparing((Path f) -> rank(baseName(f))).thenComparing(DesktopLogs::baseName))
                        .forEach(f -> sources.add(new Source(TITLES.getOrDefault(baseName(f), baseName(f)), f, null)));
            } catch (IOException e) {
                // an unreadable directory: the list stays as it is
            }
        }
        for (Path log : systemLogs) {
            if (Files.isReadable(log) && Files.isRegularFile(log)) {
                sources.add(new Source(TITLES.getOrDefault(baseName(log), baseName(log)), log, null));
            }
        }
        return sources;
    }

    private static int rank(String name) {
        int index = FIRST.indexOf(name);
        return index < 0 ? FIRST.size() : index;
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".log") ? name.substring(0, name.length() - 4) : name;
    }

    /**
     * The last {@code maxBytes} of the file, from the first whole line; a cut log starts with a note.
     * A missing or unreadable file is reported in the text instead.
     */
    static String tail(Path file, int maxBytes) {
        if (!Files.exists(file)) {
            return "(" + file + " does not exist yet.)";
        }
        try {
            long size = Files.size(file);
            if (size <= maxBytes) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            byte[] end;
            try (InputStream in = Files.newInputStream(file)) {
                in.skipNBytes(size - maxBytes);
                end = in.readNBytes(maxBytes);
            }
            String text = new String(end, StandardCharsets.UTF_8);
            int lineBreak = text.indexOf('\n');
            return "(... the first " + (size - maxBytes) + " bytes are left out ...)\n"
                    + (lineBreak < 0 ? text : text.substring(lineBreak + 1));
        } catch (IOException e) {
            return "(Cannot read " + file + ": " + e.getMessage() + ")";
        }
    }

    /** The output of the journal command; a failure is reported in the text. */
    static String journal(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return new String(output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(Cannot run " + String.join(" ", command) + ": " + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
