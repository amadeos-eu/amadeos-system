package eu.amadeos.system.clipboard;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One thing that was copied: its text, its HTML, an image or a list of files, any of them missing. A copy
 * from a web page brings text and HTML, a copy in the file manager files and their names as text, a copied
 * picture only the image. The clipboard manager offers all of them again when it takes the clipboard over.
 *
 * <p>Entries live in memory only; nothing of the history is written to disk.
 */
public final class ClipEntry {
    /** The kind shown in the history: what the entry is mainly about. */
    public enum Kind { TEXT, IMAGE, FILES }

    private final String text;
    private final String html;
    private final BufferedImage image;
    private final List<Path> files;
    private final Instant copied;
    private final int imageHash;

    public ClipEntry(String text, String html, BufferedImage image, List<Path> files, Instant copied) {
        this.text = text == null || text.isEmpty() ? null : text;
        this.html = html == null || html.isBlank() ? null : html;
        this.image = image;
        this.files = files == null ? List.of() : List.copyOf(files);
        this.copied = Objects.requireNonNull(copied);
        this.imageHash = image == null ? 0 : hash(image);
        if (this.text == null && this.html == null && image == null && this.files.isEmpty()) {
            throw new IllegalArgumentException("an entry needs text, HTML, an image or files");
        }
    }

    public static ClipEntry text(String text, Instant copied) {
        return new ClipEntry(text, null, null, null, copied);
    }

    public static ClipEntry image(BufferedImage image, Instant copied) {
        return new ClipEntry(null, null, image, null, copied);
    }

    public static ClipEntry files(List<Path> files, Instant copied) {
        return new ClipEntry(null, null, null, files, copied);
    }

    public Kind kind() {
        if (!files.isEmpty()) return Kind.FILES;
        if (text == null && html == null) return Kind.IMAGE;
        return image != null && (text == null || text.isBlank()) ? Kind.IMAGE : Kind.TEXT;
    }

    public String text() {
        return text;
    }

    public String html() {
        return html;
    }

    public BufferedImage image() {
        return image;
    }

    public List<Path> files() {
        return files;
    }

    public Instant copied() {
        return copied;
    }

    /** The same entry, copied at another time: choosing an entry from the history moves it to the top. */
    public ClipEntry copiedAt(Instant time) {
        return new ClipEntry(text, html, image, files, time);
    }

    /** About the memory the image takes, for the limit of the history. */
    public long imageBytes() {
        return image == null ? 0 : (long) image.getWidth() * image.getHeight() * 4;
    }

    /** True if both hold the same text, HTML, image and files, whenever they were copied. */
    public boolean sameContent(ClipEntry other) {
        return other != null
                && Objects.equals(text, other.text)
                && Objects.equals(html, other.html)
                && files.equals(other.files)
                && sameImage(other);
    }

    private boolean sameImage(ClipEntry other) {
        if (image == null || other.image == null) return image == other.image;
        if (imageHash != other.imageHash) return false;
        if (image.getWidth() != other.image.getWidth() || image.getHeight() != other.image.getHeight()) return false;
        return Arrays.equals(pixels(image), pixels(other.image));
    }

    private static int hash(BufferedImage image) {
        return 31 * (31 * image.getWidth() + image.getHeight()) + Arrays.hashCode(pixels(image));
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Override
    public String toString() {
        return switch (kind()) {
            case FILES -> "files " + files.size();
            case IMAGE -> "image " + image.getWidth() + "x" + image.getHeight();
            case TEXT -> "text " + (text == null ? 0 : text.length()) + " chars" + (html == null ? "" : " + HTML");
        };
    }
}
