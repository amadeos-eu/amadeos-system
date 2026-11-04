package eu.amadeos.system.packages;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A value read from the system, kept in a file together with a stamp of what it was read from: the
 * modification times of a few files and directories (apt's lists, dpkg's status, the program's own jar). As
 * long as none of them changed, the file is the answer, so a second start does not read all of Debian again.
 * Anything wrong with the file (another format, cut short) only means reading the system again.
 */
public final class StampedCache {
    private static final Logger LOGGER = Logger.getLogger(StampedCache.class.getName());

    /** Writes a value. */
    @FunctionalInterface
    public interface Writer<T> {
        void write(DataOutputStream out, T value) throws IOException;
    }

    /** Reads a value back. */
    @FunctionalInterface
    public interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }

    private final Path file;
    private final String format;
    private final List<Path> sources;

    /**
     * @param file    where the value is kept
     * @param format  names the layout of the file and what else the value depends on (the language, say);
     *                another format makes the file stale
     * @param sources the files and directories the value is read from; a change of one makes the file stale
     */
    public StampedCache(Path file, String format, List<Path> sources) {
        this.file = file;
        this.format = format;
        this.sources = List.copyOf(sources);
    }

    /** The state of the sources now; taken before reading them, so a change while reading shows next time. */
    public String stamp() {
        StringBuilder stamp = new StringBuilder(format);
        for (Path source : sources) {
            long time;
            try {
                time = Files.getLastModifiedTime(source).toMillis();
            } catch (IOException e) {
                time = -1;
            }
            stamp.append('\n').append(source).append('=').append(time);
        }
        return stamp.toString();
    }

    /** The kept value, when it was saved with this stamp. */
    public <T> Optional<T> load(String stamp, Reader<T> reader) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
            if (!in.readUTF().equals(stamp)) return Optional.empty();
            return Optional.of(reader.read(in));
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Cannot read " + file, e);
            return Optional.empty();
        }
    }

    /** Keeps the value with the stamp taken before reading it; a failure only costs the next start time. */
    public <T> void save(String stamp, T value, Writer<T> writer) {
        try {
            Files.createDirectories(file.getParent());
            Path part = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".part");
            try {
                try (DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(part), 1 << 16))) {
                    out.writeUTF(stamp);
                    writer.write(out, value);
                }
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(part);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Cannot write " + file, e);
        }
    }

    /** The user's cache directory, {@code $XDG_CACHE_HOME} or {@code ~/.cache}. */
    public static Path userCache() {
        String xdg = System.getenv("XDG_CACHE_HOME");
        return xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".cache");
    }

    /** The jar (or class directory) a class was loaded from, so a new version of the program reads anew. */
    public static Optional<Path> codeOf(Class<?> type) {
        try {
            var source = type.getProtectionDomain().getCodeSource();
            return source == null ? Optional.empty() : Optional.of(Path.of(source.getLocation().toURI()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Small helpers for the writers and readers.

    /** A string that may be longer than writeUTF's 64 KB. */
    public static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > (64 << 20)) throw new IOException("bad length " + length);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException("cut short");
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
