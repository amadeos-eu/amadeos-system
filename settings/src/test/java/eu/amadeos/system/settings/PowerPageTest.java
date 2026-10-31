package eu.amadeos.system.settings;

import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerSettings;
import eu.amadeos.system.base.power.PowerSettingsStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Power page on a laptop and on a desktop, with a logind stand-in ({@link FakePower}). */
class PowerPageTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void english() {
        SettingsTexts.use(Locale.ENGLISH);
    }

    @Test
    void laptopSavesItsSettings() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        PowerSettingsStore store = new PowerSettingsStore(dir.resolve(PowerSettingsStore.FILE_NAME));
        SwingUtilities.invokeAndWait(() -> {
            PowerPage page = new PowerPage(s -> { }, store, new FakePower(false), FakePower::laptopBattery);
            PowerSettings shown = page.settings();
            assertEquals(PowerSettings.DEFAULTS.battery(), shown.battery());
            assertEquals(PowerAction.SHUTDOWN, shown.lowAction(), "without hibernation the battery shuts down");
            assertEquals("Power: screen off after 5 min, suspend after 15 min", page.apply());
        });
        assertEquals(PowerAction.SHUTDOWN, store.load().lowAction());
    }

    @Test
    void desktopKeepsTheBatteryColumn() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        PowerSettingsStore store = new PowerSettingsStore(dir.resolve(PowerSettingsStore.FILE_NAME));
        store.save(PowerSettings.DEFAULTS.withBattery(new PowerSettings.Profile(1, 2, PowerAction.LOCK)));
        SwingUtilities.invokeAndWait(() -> {
            PowerPage page = new PowerPage(s -> { }, store, new FakePower(true), Optional::empty);
            page.apply();
        });
        assertEquals(new PowerSettings.Profile(1, 2, PowerAction.LOCK), store.load().battery());
    }

    @Test
    void minutesTexts() {
        assertEquals("Never", PowerPage.minutesText(PowerSettings.NEVER));
        assertEquals("2 h", PowerPage.minutesText(120));
        assertEquals("90 min", PowerPage.minutesText(90));
    }
}
