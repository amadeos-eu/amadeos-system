package eu.amadeos.system.clipboard;

import eu.amadeos.commons.i18n.Messages;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.stream.Collectors;

/** What the history window shows of an entry: one line of its content and a line about it. */
final class EntryPreview {
    static final int MAX_CHARS = 80;
    static final int THUMB_WIDTH = 64;
    static final int THUMB_HEIGHT = 40;

    private EntryPreview() {
    }

    /** The content in one line: the text with its white space folded, the names of the files. */
    static String line(ClipEntry entry) {
        String line = switch (entry.kind()) {
            case FILES -> entry.files().stream().map(EntryPreview::name).collect(Collectors.joining(", "));
            case IMAGE -> "";
            case TEXT -> fold(entry.text() != null ? entry.text() : stripTags(entry.html()));
        };
        return line.length() <= MAX_CHARS ? line : line.substring(0, MAX_CHARS - 1).stripTrailing() + "…";
    }

    /** The kind of the entry and when it was copied, e.g. "Image 800 × 600 · 14:05". */
    static String meta(ClipEntry entry, Messages texts, ZoneId zone) {
        String kind = switch (entry.kind()) {
            case FILES -> texts.format("kind.files", entry.files().size());
            case IMAGE -> texts.format("kind.image", entry.image().getWidth(), entry.image().getHeight());
            case TEXT -> entry.text() == null || entry.html() != null ? texts.get("kind.html") : texts.get("kind.text");
        };
        if (entry.kind() == ClipEntry.Kind.TEXT && entry.image() != null) kind = texts.format("kind.with.image", kind);
        return texts.format("meta", kind, time(entry.copied(), zone, texts));
    }

    private static String time(Instant copied, ZoneId zone, Messages texts) {
        boolean today = copied.atZone(zone).toLocalDate().equals(Instant.now().atZone(zone).toLocalDate());
        DateTimeFormatter format = today ? DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                : DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
        return format.withLocale(texts.locale()).withZone(zone).format(copied);
    }

    /** The image scaled to fit the thumbnail box, never enlarged. */
    static Image thumbnail(BufferedImage image) {
        double scale = Math.min(1.0, Math.min((double) THUMB_WIDTH / image.getWidth(), (double) THUMB_HEIGHT / image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return image.getScaledInstance(w, h, Image.SCALE_SMOOTH);
    }

    static String fold(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    static String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("(?s)<(script|style)\\b.*?</\\1>", " ").replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&amp;", "&");
    }

    private static String name(Path file) {
        Path name = file.getFileName();
        return name == null ? file.toString() : name.toString();
    }
}
