package eu.amadeos.system.clipboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardManagerTest {
    private static final long EDITOR = 0x1200001;
    private static final long MANAGER = 0x3400001;
    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    /** The X server as far as the manager sees it: owners, targets, a mouse button and the data on offer. */
    private final Map<Selection, Long> owners = new EnumMap<>(Selection.class);
    private final Map<Selection, ClipEntry> offered = new EnumMap<>(Selection.class);
    private final List<String> targets = new ArrayList<>(List.of("TARGETS", "UTF8_STRING"));
    private final List<ClipEntry> owned = new ArrayList<>();
    private boolean buttonDown;
    private ClipboardHistory history;
    private ClipboardManager manager;

    @BeforeEach
    void setUp() {
        history = new ClipboardHistory(30, 1 << 20);
        manager = manager(ClipboardSettings.DEFAULTS);
    }

    private ClipboardManager manager(ClipboardSettings settings) {
        SystemClipboards clipboards = new SystemClipboards() {
            @Override
            public Optional<ClipEntry> read(Selection selection) {
                return Optional.ofNullable(offered.get(selection));
            }

            @Override
            public void own(ClipEntry entry) {
                owned.add(entry);
                owners.put(Selection.CLIPBOARD, MANAGER);
                offered.put(Selection.CLIPBOARD, entry);
            }
        };
        SelectionProbe probe = new SelectionProbe() {
            @Override
            public long owner(Selection selection) {
                return owners.getOrDefault(selection, 0L);
            }

            @Override
            public List<String> targets(Selection selection) {
                return targets;
            }

            @Override
            public boolean buttonDown() {
                return buttonDown;
            }
        };
        return new ClipboardManager(history, clipboards, probe, settings, () -> NOW);
    }

    /** A program copies {@code text}: it owns the clipboard and the server says so. */
    private boolean copy(String text) {
        owners.put(Selection.CLIPBOARD, EDITOR);
        offered.put(Selection.CLIPBOARD, ClipEntry.text(text, NOW));
        return manager.handle(manager.arrived(Selection.CLIPBOARD, EDITOR, false));
    }

    /** The program that owns the clipboard closes. */
    private void ownerCloses() {
        owners.remove(Selection.CLIPBOARD);
        offered.remove(Selection.CLIPBOARD);
        manager.handle(manager.arrived(Selection.CLIPBOARD, 0, true));
    }

    private List<String> texts() {
        return history.entries().stream().map(ClipEntry::text).toList();
    }

    @Test
    void recordsEveryCopyNewestFirst() {
        assertTrue(copy("one"));
        copy("two");
        copy("three");
        assertEquals(List.of("three", "two", "one"), texts());
    }

    @Test
    void aCopyThatIsAlreadyThereMovesToTheTop() {
        copy("one");
        copy("two");
        copy("one");
        assertEquals(List.of("one", "two"), texts());
    }

    @Test
    void takesTheClipboardOverWhenTheProgramThatCopiedCloses() {
        copy("keep me");
        ownerCloses();
        assertEquals(List.of("keep me"), owned.stream().map(ClipEntry::text).toList());
        assertEquals(MANAGER, owners.get(Selection.CLIPBOARD));
    }

    @Test
    void ignoresItsOwnTakeOver() {
        copy("keep me");
        ownerCloses();
        manager.handle(manager.arrived(Selection.CLIPBOARD, MANAGER, false));
        assertEquals(List.of("keep me"), texts());
        assertEquals(1, owned.size());
    }

    @Test
    void withoutKeepTheClipboardStaysEmpty() {
        manager = manager(new ClipboardSettings(true, 30, false, false));
        copy("gone");
        ownerCloses();
        assertTrue(owned.isEmpty());
        assertEquals(List.of("gone"), texts());
    }

    @Test
    void passwordsAreNeitherRecordedNorKept() {
        copy("public");
        targets.add("x-kde-passwordManagerHint");
        copy("secret");
        ownerCloses();
        assertEquals(List.of("public"), texts());
        assertTrue(owned.isEmpty(), "the older copy must not come back in place of the password");
    }

    @Test
    void aClipboardClearedOnPurposeStaysEmpty() {
        copy("one");
        owners.remove(Selection.CLIPBOARD);
        manager.handle(manager.arrived(Selection.CLIPBOARD, 0, false));
        ownerCloses();
        assertTrue(owned.isEmpty());
    }

    @Test
    void pausedRecordsNothing() {
        manager.setPaused(true);
        copy("private");
        ownerCloses();
        assertTrue(history.entries().isEmpty());
        assertTrue(owned.isEmpty());
        manager.setPaused(false);
        copy("public");
        assertEquals(List.of("public"), texts());
    }

    @Test
    void choosingAnEntryPutsItOnTheClipboardAndAtTheTop() {
        copy("one");
        copy("two");
        manager.choose(history.entries().get(1));
        assertEquals(List.of("one", "two"), texts());
        assertEquals("one", owned.getLast().text());
    }

    @Test
    void savesWhenAProgramAsksBeforeClosing() {
        owners.put(Selection.CLIPBOARD, EDITOR);
        offered.put(Selection.CLIPBOARD, ClipEntry.text("screenshot", NOW));
        assertTrue(manager.save());
        assertEquals(MANAGER, owners.get(Selection.CLIPBOARD));
        assertEquals("screenshot", owned.getLast().text());
        assertTrue(manager.save(), "already ours");
        owners.remove(Selection.CLIPBOARD);
        assertFalse(manager.save(), "nothing to save");
    }

    @Test
    void neverSavesAPassword() {
        owners.put(Selection.CLIPBOARD, EDITOR);
        offered.put(Selection.CLIPBOARD, ClipEntry.text("secret", NOW));
        targets.add("org.nspasteboard.ConcealedType");
        assertFalse(manager.save());
        assertTrue(owned.isEmpty());
    }

    @Test
    void selectedTextIsIgnoredUnlessTurnedOn() {
        owners.put(Selection.PRIMARY, EDITOR);
        offered.put(Selection.PRIMARY, ClipEntry.text("selected", NOW));
        assertTrue(manager.handle(manager.arrived(Selection.PRIMARY, EDITOR, false)));
        assertTrue(history.entries().isEmpty());
    }

    @Test
    void selectedTextGoesToTheClipboardOnceTheButtonIsReleased() {
        manager = manager(new ClipboardSettings(true, 30, true, true));
        owners.put(Selection.PRIMARY, EDITOR);
        offered.put(Selection.PRIMARY, ClipEntry.text("sel", NOW));
        ClipboardManager.OwnerChange first = manager.arrived(Selection.PRIMARY, EDITOR, false);
        buttonDown = true;
        assertFalse(manager.handle(first), "still selecting");
        offered.put(Selection.PRIMARY, ClipEntry.text("selected text", NOW));
        buttonDown = false;
        assertTrue(manager.handle(first));
        assertEquals(List.of("selected text"), texts());
        assertEquals("selected text", owned.getLast().text());
    }

    @Test
    void anOlderSelectionGivesWayToANewerOne() {
        manager = manager(new ClipboardSettings(true, 30, true, true));
        owners.put(Selection.PRIMARY, EDITOR);
        offered.put(Selection.PRIMARY, ClipEntry.text("newer", NOW));
        ClipboardManager.OwnerChange older = manager.arrived(Selection.PRIMARY, EDITOR, false);
        ClipboardManager.OwnerChange newer = manager.arrived(Selection.PRIMARY, EDITOR, false);
        assertTrue(manager.handle(older));
        assertTrue(history.entries().isEmpty());
        manager.handle(newer);
        assertEquals(List.of("newer"), texts());
    }

    @Test
    void turningTheHistoryOffForgetsIt() {
        copy("one");
        manager.apply(new ClipboardSettings(false, 30, true, false));
        assertTrue(history.entries().isEmpty());
        copy("two");
        assertTrue(history.entries().isEmpty());
    }

    @Test
    void removedEntriesAreNotTakenOver() {
        copy("one");
        manager.remove(history.entries().getFirst());
        ownerCloses();
        assertTrue(owned.isEmpty());
    }
}
