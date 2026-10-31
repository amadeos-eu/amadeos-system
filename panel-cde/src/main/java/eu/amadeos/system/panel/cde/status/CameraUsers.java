package eu.amadeos.system.panel.cde.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which programs have a camera open: every process whose open files include a {@code /dev/video*} device.
 * Read from {@code /proc}, so it sees the processes of the logged-in user (and all of them when the panel may
 * read them); a camera opened by another user's program is not seen, which is fine for a desktop light.
 * <p>
 * The name is the program the user started, not the helper that opens the device: a camera opened by
 * {@code ffmpeg} on behalf of a Java application is reported as that application ({@code amade-webcam}).
 */
public final class CameraUsers {
    /** Helper programs that open the camera for another program; the parent's name is shown instead. */
    static final Set<String> HELPERS = Set.of("ffmpeg", "ffplay", "gst-launch-1.0", "v4l2-ctl", "sh", "bash", "dash");

    private final Path proc;

    public CameraUsers() {
        this(Path.of("/proc"));
    }

    CameraUsers(Path proc) {
        this.proc = proc;
    }

    // --8<-- [start:scan]
    /** The names of the programs that have a camera open now, sorted; empty when no camera is in use. */
    public Set<String> programs() {
        Set<String> names = new TreeSet<>();
        try (DirectoryStream<Path> processes = Files.newDirectoryStream(proc, p -> isPid(p.getFileName().toString()))) {
            for (Path process : processes) {
                if (usesCamera(process)) names.add(programName(process.getFileName().toString(), 0));
            }
        } catch (IOException | RuntimeException e) {
            // /proc not readable: report nothing rather than a wrong light
        }
        return names;
    }

    private static boolean usesCamera(Path process) {
        try (DirectoryStream<Path> fds = Files.newDirectoryStream(process.resolve("fd"))) {
            for (Path fd : fds) {
                try {
                    String target = Files.readSymbolicLink(fd).toString();
                    if (target.startsWith("/dev/video")) return true;
                } catch (IOException | UnsupportedOperationException e) {
                    // the file was closed meanwhile
                }
            }
        } catch (IOException e) {
            // another user's process, or it has ended
        }
        return false;
    }
    // --8<-- [end:scan]

    /** {@code firefox}, or the parent's name for a helper; a Java program is named after its jar. */
    String programName(String pid, int depth) {
        String comm = read(proc.resolve(pid).resolve("comm")).map(String::strip).orElse("?");
        if (HELPERS.contains(comm) && depth < 3) {
            Optional<String> parent = parent(pid);
            if (parent.isPresent() && !parent.get().equals("1") && !parent.get().equals("0")) {
                String name = programName(parent.get(), depth + 1);
                if (!name.equals("?") && !name.equals("systemd")) return name;
            }
        }
        if (comm.equals("java")) return javaProgram(pid).orElse(comm);
        return comm;
    }

    private Optional<String> parent(String pid) {
        // "123 (name with spaces) S 45 ...": the parent pid is the second field after the closing bracket
        return read(proc.resolve(pid).resolve("stat")).flatMap(stat -> {
            int close = stat.lastIndexOf(')');
            if (close < 0) return Optional.empty();
            String[] fields = stat.substring(close + 1).trim().split("\\s+");
            return fields.length > 1 ? Optional.of(fields[1]) : Optional.empty();
        });
    }

    /** {@code amade-webcam} for {@code java -jar /usr/share/amade-webcam/amade-webcam-1.0.0.jar}. */
    private Optional<String> javaProgram(String pid) {
        return read(proc.resolve(pid).resolve("cmdline")).flatMap(cmdline -> {
            List<String> args = List.of(cmdline.split("\0"));
            int jar = args.indexOf("-jar");
            if (jar < 0 || jar + 1 >= args.size()) return Optional.empty();
            String file = Path.of(args.get(jar + 1)).getFileName().toString();
            String name = file.replaceFirst("\\.jar$", "").replaceFirst("-\\d[^-]*(-.*)?$", "");
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        });
    }

    private static Optional<String> read(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean isPid(String name) {
        if (name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) if (!Character.isDigit(name.charAt(i))) return false;
        return true;
    }
}
