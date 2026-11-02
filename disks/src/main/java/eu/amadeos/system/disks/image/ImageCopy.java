package eu.amadeos.system.disks.image;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copies disk images to and from drives in 4 MB blocks, with progress, SHA-256 and a read-back compare. Works on
 * plain streams, so it is tested with files; {@link eu.amadeos.system.disks.service.DiskService} opens the drive.
 */
public final class ImageCopy {
    static final int BLOCK = 4 * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("(?i)\\b([0-9a-f]{64})\\b(?:\\s+\\*?(\\S+))?");

    /** Told about progress: bytes done of total (total -1 when not known). */
    @FunctionalInterface
    public interface Listener {
        void progress(long done, long total);
    }

    /** The user stopped it. */
    public static final class Stopped extends IOException {
        private static final long serialVersionUID = 1L;

        public Stopped() {
            super("stopped");
        }
    }

    private ImageCopy() {
    }

    // --8<-- [start:copy]
    /**
     * Copies {@code total} bytes (or all, with -1) and returns their SHA-256 in hex. Calls {@code listener} after
     * every block and checks {@code stop} before it.
     */
    public static String copy(InputStream in, OutputStream out, long total, Listener listener, BooleanSupplier stop)
            throws IOException {
        MessageDigest sha = sha256();
        byte[] buffer = new byte[BLOCK];
        long done = 0;
        while (total < 0 || done < total) {
            if (stop.getAsBoolean()) throw new Stopped();
            int want = total < 0 ? buffer.length : (int) Math.min(buffer.length, total - done);
            int n = in.readNBytes(buffer, 0, want);
            if (n <= 0) break;
            out.write(buffer, 0, n);
            sha.update(buffer, 0, n);
            done += n;
            listener.progress(done, total);
        }
        out.flush();
        return HexFormat.of().formatHex(sha.digest());
    }
    // --8<-- [end:copy]

    /** The SHA-256 of the first {@code length} bytes of a stream (the read-back after writing). */
    public static String sha256(InputStream in, long length, Listener listener, BooleanSupplier stop)
            throws IOException {
        return copy(in, OutputStream.nullOutputStream(), length, listener, stop);
    }

    /**
     * The checksum published next to an image: {@code NAME.sha256}, {@code NAME.sha256sum}, or a
     * {@code SHA256SUMS} file in the same folder with a line for it.
     */
    public static Optional<String> publishedSha256(Path image) {
        String name = image.getFileName().toString();
        Path dir = image.toAbsolutePath().getParent();
        for (String candidate : new String[]{name + ".sha256", name + ".sha256sum", "SHA256SUMS", "sha256sums.txt",
                "SHA256SUMS.txt"}) {
            Path file = dir.resolve(candidate);
            if (!Files.isRegularFile(file)) continue;
            try {
                Optional<String> sum = find(Files.readString(file, StandardCharsets.UTF_8), name,
                        candidate.startsWith(name));
                if (sum.isPresent()) return sum;
            } catch (IOException e) {
                // unreadable: try the next one
            }
        }
        return Optional.empty();
    }

    /** The checksum for {@code name} in a sums file; a file of only one image may list it without the name. */
    static Optional<String> find(String text, String name, boolean ownFile) {
        String only = null;
        for (String line : text.split("\\R")) {
            Matcher m = SHA256.matcher(line.strip());
            if (!m.find()) continue;
            String file = m.group(2);
            if (file != null && Path.of(file).getFileName().toString().equals(name)) {
                return Optional.of(m.group(1).toLowerCase(Locale.ROOT));
            }
            if (file == null || ownFile) only = m.group(1).toLowerCase(Locale.ROOT);
        }
        return Optional.ofNullable(ownFile ? only : null);
    }

    /** Whether the file looks compressed with xz (an .img.xz), by its first bytes. */
    public static boolean isXz(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(6);
            return head.length == 6 && (head[0] & 0xFF) == 0xFD && head[1] == '7' && head[2] == 'z'
                    && head[3] == 'X' && head[4] == 'Z' && head[5] == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
