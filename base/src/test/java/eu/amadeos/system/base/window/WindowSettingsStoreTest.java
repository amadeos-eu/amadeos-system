package eu.amadeos.system.base.window;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowSettingsStoreTest {
    @TempDir
    Path dir;

    @Test
    void savesAndLoadsTheSettings() {
        WindowSettingsStore store = new WindowSettingsStore(dir.resolve(WindowSettingsStore.FILE_NAME));
        assertEquals(WindowSettings.DEFAULTS, store.load(), "no file: CDE's defaults");
        WindowSettings pointer = new WindowSettings(WindowSettings.Focus.POINTER, false, true);
        store.save(pointer);
        assertEquals(pointer, store.load());
    }

    @Test
    void unknownValuesFallBackToTheDefaults() throws Exception {
        Path file = dir.resolve(WindowSettingsStore.FILE_NAME);
        Files.writeString(file, "focus=sloppy\nraise.when.active=false\n");
        WindowSettings loaded = new WindowSettingsStore(file).load();
        assertEquals(WindowSettings.Focus.CLICK, loaded.focus());
        assertEquals(false, loaded.raiseWhenActive());
        assertEquals(false, loaded.showContentsDuringMove());
    }
}
