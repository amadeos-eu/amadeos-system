package eu.amadeos.system.clipboard;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardHistoryTest {
    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    private static ClipEntry text(String s) {
        return ClipEntry.text(s, NOW);
    }

    private static ClipEntry image(int w, int h, int rgb) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, rgb);
        return ClipEntry.image(image, NOW);
    }

    @Test
    void keepsAtMostTheLimitAndDropsTheOldest() {
        ClipboardHistory history = new ClipboardHistory(3, 1 << 20);
        for (String s : List.of("a", "b", "c", "d")) history.add(text(s));
        assertEquals(List.of("d", "c", "b"), history.entries().stream().map(ClipEntry::text).toList());
        history.setLimit(1);
        assertEquals(1, history.size());
        assertEquals("d", history.latest().orElseThrow().text());
    }

    @Test
    void theSameImageAppearsOnceOtherImagesStay() {
        ClipboardHistory history = new ClipboardHistory(10, 1 << 20);
        history.add(image(10, 10, 0xFF0000FF));
        history.add(image(10, 10, 0xFFFF0000));
        history.add(image(10, 10, 0xFF0000FF));
        assertEquals(2, history.size());
    }

    @Test
    void imagesBeyondTheMemoryBudgetGoFirstButTheNewestStays() {
        // 100 x 100 x 4 bytes = 40 000 bytes each; room for two.
        ClipboardHistory history = new ClipboardHistory(10, 90_000);
        history.add(image(100, 100, 1));
        history.add(text("text stays"));
        history.add(image(100, 100, 2));
        history.add(image(100, 100, 3));
        List<ClipEntry> entries = history.entries();
        assertEquals(3, entries.size());
        assertEquals("text stays", entries.get(2).text());
        assertTrue(history.add(image(200, 200, 4)).isEmpty(), "bigger than the whole budget");
    }

    @Test
    void listenersHearEveryChange() {
        ClipboardHistory history = new ClipboardHistory(10, 1 << 20);
        AtomicInteger changes = new AtomicInteger();
        history.addListener(changes::incrementAndGet);
        ClipEntry a = text("a");
        history.add(a);
        history.remove(a);
        history.remove(a);
        history.add(text("b"));
        history.clear();
        history.clear();
        assertEquals(4, changes.get());
    }

    @Test
    void entriesKnowTheirKind() {
        assertEquals(ClipEntry.Kind.TEXT, text("x").kind());
        assertEquals(ClipEntry.Kind.IMAGE, image(1, 1, 0).kind());
        assertEquals(ClipEntry.Kind.FILES, new ClipEntry("/tmp/a", null, null, List.of(Path.of("/tmp/a")), NOW).kind());
        assertEquals(ClipEntry.Kind.TEXT, new ClipEntry("cell", null, image(1, 1, 0).image(), null, NOW).kind());
        assertEquals(ClipEntry.Kind.IMAGE, new ClipEntry(null, "<img src=x>", image(1, 1, 0).image(), null, NOW).kind());
        assertThrows(IllegalArgumentException.class, () -> new ClipEntry("", " ", null, List.of(), NOW));
    }
}
