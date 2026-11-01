package eu.amadeos.system.widgets.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinTest {
    @TempDir
    Path dir;

    @Test
    void toDoLinesKeepWhatIsDone() {
        assertEquals(new NoteWidget.Item("Buy milk", true), NoteWidget.Item.parse("[x] Buy milk"));
        assertEquals(new NoteWidget.Item("Call Anna", false), NoteWidget.Item.parse("[ ] Call Anna"));
        assertEquals(new NoteWidget.Item("Plain", false), NoteWidget.Item.parse("Plain"));
        assertEquals("[x] Buy milk", new NoteWidget.Item("Buy milk", true).line());
    }

    @Test
    void thePhotoFrameTakesAPictureOrTheFolder() throws Exception {
        Files.writeString(dir.resolve("b.jpg"), "x");
        Files.writeString(dir.resolve("a.PNG"), "x");
        Files.writeString(dir.resolve("notes.txt"), "x");
        Files.writeString(dir.resolve(".hidden.jpg"), "x");
        assertEquals(List.of(dir.resolve("a.PNG"), dir.resolve("b.jpg")), PhotoWidget.pictures(dir));
        assertEquals(List.of(dir.resolve("b.jpg")), PhotoWidget.pictures(dir.resolve("b.jpg")));
        assertEquals(List.of(), PhotoWidget.pictures(dir.resolve("notes.txt")));
        assertEquals(List.of(), PhotoWidget.pictures(dir.resolve("gone")));
        assertEquals(10, PhotoWidget.parseLong("ten", 10));
    }
}
