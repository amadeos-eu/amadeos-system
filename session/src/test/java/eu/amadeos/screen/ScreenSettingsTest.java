package eu.amadeos.screen;

import eu.amadeos.screen.modules.SaverModule;
import eu.amadeos.screen.modules.SaverModules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ScreenSettingsTest {
    @TempDir
    Path dir;

    @Test
    void storeRoundTripAndDefaults() {
        ScreenSettingsStore store = new ScreenSettingsStore(dir.resolve("screen.conf"));
        assertEquals(ScreenSettings.DEFAULTS, store.load());
        assertNull(store.lastModified());

        ScreenSettings settings = new ScreenSettings(false, 3, true, 20, "swarm");
        store.save(settings);
        assertEquals(settings, store.load());
        assertNotNull(store.lastModified());
    }

    @Test
    void brokenValuesFallBackAndAreClamped() throws Exception {
        Path file = dir.resolve("screen.conf");
        Files.writeString(file, "saver.minutes=abc\nlock.minutes=99999\nsaver.module=\n");
        ScreenSettings s = new ScreenSettingsStore(file).load();
        assertEquals(ScreenSettings.DEFAULTS.saverMinutes(), s.saverMinutes());
        assertEquals(ScreenSettings.MAX_MINUTES, s.lockMinutes());
        assertEquals(ScreenSettings.DEFAULT_MODULE, s.module());
        assertEquals(1, new ScreenSettings(true, 0, true, -5, "x").saverMinutes());
    }

    @Test
    void changeDetection() throws Exception {
        ScreenSettingsStore store = new ScreenSettingsStore(dir.resolve("screen.conf"));
        FileTime none = store.lastModified();
        assertFalse(store.changedSince(none));
        store.save(ScreenSettings.DEFAULTS);
        assertTrue(store.changedSince(none));
        assertFalse(store.changedSince(store.lastModified()));
    }

    @Test
    void everyModulePaintsAtAnySize() {
        Random random = new Random(42);
        int[][] sizes = {{1, 1}, {7, 3}, {240, 150}, {1920, 1080}};
        for (SaverModules.Entry entry : SaverModules.ALL) {
            for (int[] size : sizes) {
                SaverModule module = entry.factory().get();
                assertEquals(entry.id(), module.id());
                BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                module.reset(size[0], size[1], random);
                for (int frame = 0; frame < 120; frame++) {
                    module.step();
                    module.paint(g, size[0], size[1]);
                }
                g.dispose();
            }
        }
    }

    @Test
    void moduleLookup() {
        Random random = new Random(1);
        assertEquals("swarm", SaverModules.create("swarm", random).id());
        assertEquals("qix", SaverModules.create("unknown", random).id());
        for (int i = 0; i < 20; i++) {
            assertNotEquals("blank", SaverModules.create(ScreenSettings.RANDOM, random).id());
        }
    }
}
