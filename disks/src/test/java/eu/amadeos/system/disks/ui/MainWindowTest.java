package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.disks.Disks;
import eu.amadeos.system.disks.FakeDisks;
import eu.amadeos.system.disks.model.Drive;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MainWindowTest {
    static Context context(FakeDisks disks) {
        return new Context(Messages.load(Disks.class, Disks.TEXTS, Locale.ENGLISH), disks, "ralf");
    }

    static boolean shown(Action a) {
        return Boolean.TRUE.equals(a.getValue(MainWindow.APPLICABLE));
    }

    @Test
    void theSystemDiskOnlyOffersSafeSteps() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDisks disks = new FakeDisks();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow w = new MainWindow(context(disks));
            w.show(disks.drives());
            assertEquals(Drive.Kind.SYSTEM, w.drive().kind());
            w.partitionMap().select(1);
            assertEquals("/dev/nvme0n1p2", w.volume().device());
            assertFalse(shown(w.format), "the running system cannot be formatted");
            assertFalse(shown(w.delete));
            assertFalse(shown(w.unmount));
            assertFalse(shown(w.formatDrive));
            assertFalse(shown(w.writeImage));
            assertTrue(shown(w.health));
            assertTrue(shown(w.openFiles));
            w.partitionMap().select(3);
            assertTrue(w.segment().free());
            assertTrue(shown(w.create), "a new partition in the free space");
            w.dispose();
        });
    }

    @Test
    void aStickOffersEverything() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDisks disks = new FakeDisks();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow w = new MainWindow(context(disks));
            w.select("/dev/sdb", false);
            w.show(disks.drives());
            assertEquals("/dev/sdb", w.drive().device());
            assertEquals("STICK", w.volume().name());
            for (Action a : new Action[]{w.format, w.delete, w.unmount, w.formatDrive, w.writeImage, w.saveImage,
                    w.remove, w.check, w.rename, w.openFiles}) {
                assertTrue(shown(a) && a.isEnabled(), String.valueOf(a.getValue(Action.NAME)));
            }
            assertFalse(shown(w.mount));
            assertFalse(shown(w.health), "sticks report no health");
            assertFalse(shown(w.eject));
            w.dispose();
        });
    }

    @Test
    void theDiscDriveCanOnlyEject() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDisks disks = new FakeDisks();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow w = new MainWindow(context(disks));
            w.select("/dev/sr0", false);
            w.show(disks.drives());
            assertTrue(shown(w.eject));
            assertFalse(shown(w.formatDrive));
            assertFalse(shown(w.create));
            w.dispose();
        });
    }

    @Test
    void partitionMapGivesSmallPartitionsRoom() {
        var map = FakeDisks.initial().get(0).segments();
        int[] edges = PartitionMap.edges(map, 520);
        assertEquals(map.size() + 1, edges.length);
        assertEquals(520, edges[edges.length - 1]);
        assertTrue(edges[1] - edges[0] >= 44, "the 512 MB EFI partition stays clickable");
    }
}
