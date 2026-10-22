package eu.amadeos.system.clipboard;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntryPreviewTest {
    private static final Instant NOW = Instant.now();
    private final Messages english = Messages.load(ClipboardDaemon.class, ClipboardDaemon.TEXTS, Locale.ENGLISH);

    @TempDir
    Path dir;

    @Test
    void textIsFoldedIntoOneShortLine() {
        assertEquals("first line second line", EntryPreview.line(ClipEntry.text("  first line\n\tsecond   line\n", NOW)));
        String line = EntryPreview.line(ClipEntry.text("x".repeat(200), NOW));
        assertEquals(EntryPreview.MAX_CHARS, line.length());
        assertTrue(line.endsWith("…"));
    }

    @Test
    void htmlWithoutTextShowsItsWords() {
        ClipEntry html = new ClipEntry(null, "<p>Fish &amp; <b>chips</b></p><script>x()</script>", null, null, NOW);
        assertEquals("Fish & chips", EntryPreview.line(html));
        assertTrue(EntryPreview.meta(html, english, ZoneOffset.UTC).startsWith("Formatted text · "));
    }

    @Test
    void filesShowTheirNames() {
        ClipEntry files = ClipEntry.files(List.of(Path.of("/home/anna/a.txt"), Path.of("/home/anna/Pictures")), NOW);
        assertEquals("a.txt, Pictures", EntryPreview.line(files));
        assertTrue(EntryPreview.meta(files, english, ZoneOffset.UTC).startsWith("2 files · "));
    }

    @Test
    void imagesShowTheirSizeAndAThumbnailThatFits() {
        ClipEntry image = ClipEntry.image(new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB), NOW);
        assertEquals("", EntryPreview.line(image));
        assertTrue(EntryPreview.meta(image, english, ZoneOffset.UTC).startsWith("Image 1920 × 1080 · "));
        java.awt.Image thumb = EntryPreview.thumbnail(image.image());
        assertEquals(EntryPreview.THUMB_WIDTH, thumb.getWidth(null));
        assertEquals(36, thumb.getHeight(null));
    }

    @Test
    void everyTranslationIsComplete() {
        assertEquals(Map.of(), TranslationCheck.problems(ClipboardDaemon.class, ClipboardDaemon.TEXTS));
        Messages german = Messages.load(ClipboardDaemon.class, ClipboardDaemon.TEXTS, Locale.GERMAN);
        assertEquals("1 Datei", german.format("kind.files", 1));
    }

    @Test
    void settingsRoundTripWithoutTheHistory() {
        ClipboardSettingsStore store = new ClipboardSettingsStore(dir.resolve("clipboard.conf"));
        assertEquals(ClipboardSettings.DEFAULTS, store.load());
        ClipboardSettings settings = new ClipboardSettings(true, 500, false, true);
        assertEquals(ClipboardSettings.MAX_SIZE, settings.size());
        store.save(settings);
        assertEquals(settings, store.load());
    }
}
