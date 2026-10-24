package eu.amadeos.system.base.panel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PanelSettingsStoreTest {
    @TempDir
    Path dir;

    @Test
    void savesAndLoadsTheSettings() {
        PanelSettingsStore store = new PanelSettingsStore(dir.resolve(PanelSettingsStore.FILE_NAME));
        assertEquals(PanelSettings.DEFAULTS, store.load(), "no file: the panel as before");
        PanelSettings digital = new PanelSettings(PanelSettings.Clock.DIGITAL, false, true, false, true, false);
        store.save(digital);
        assertEquals(digital, store.load());
    }

    @Test
    void theClockStyleOfTheOldClockFileIsKept() throws Exception {
        Files.writeString(dir.resolve(PanelSettingsStore.LEGACY_CLOCK_FILE), "DIGITAL");
        PanelSettingsStore store = new PanelSettingsStore(dir.resolve(PanelSettingsStore.FILE_NAME));
        assertEquals(PanelSettings.Clock.DIGITAL, store.load().clock());

        store.save(store.load().withClock(PanelSettings.Clock.ANALOG));
        assertEquals(PanelSettings.Clock.ANALOG, store.load().clock(), "panel.conf wins over clock.conf");
    }

    @Test
    void unknownValuesFallBackToTheDefaults() throws Exception {
        Path file = dir.resolve(PanelSettingsStore.FILE_NAME);
        Files.writeString(file, "clock=sundial\ncell.updates=false\n");
        PanelSettings loaded = new PanelSettingsStore(file).load();
        assertEquals(PanelSettings.Clock.ANALOG, loaded.clock());
        assertEquals(true, loaded.networkCell());
        assertEquals(true, loaded.batteryCell(), "a file from before the battery cell: shown");
        assertEquals(false, loaded.updatesCell());
        assertEquals(true, loaded.activityMeter(), "a file from before the busy light: disk and network shown");
    }
}
