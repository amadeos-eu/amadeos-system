package eu.amadeos.system.screen;

import eu.amadeos.system.screen.modules.SaverModules;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaverCanvasTest {
    @Test
    void onlyLargeAreasAndWillingModulesAreDrawnAtHalfSize() {
        Random random = new Random(1);
        var starfield = SaverModules.create("starfield", random);
        var logo = SaverModules.create("logo", random);
        assertTrue(SaverCanvas.atHalfSize(starfield, 3840, 2160));
        assertFalse(SaverCanvas.atHalfSize(starfield, 1920, 1080), "one full HD monitor stays sharp");
        assertFalse(SaverCanvas.atHalfSize(logo, 3840, 2160), "text is never scaled up");
    }

    /** Every module survives tiny previews and many steps. */
    @Test
    void everyModulePaintsAtAnySize() {
        for (var entry : SaverModules.ALL) {
            for (int[] size : new int[][]{{1, 1}, {160, 100}, {800, 600}}) {
                var module = entry.factory().get();
                module.reset(size[0], size[1], new Random(2));
                BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                for (int i = 0; i < 120; i++) {
                    module.step();
                    Graphics2D g = image.createGraphics();
                    try {
                        module.paint(g, size[0], size[1]);
                    } finally {
                        g.dispose();
                    }
                }
            }
        }
    }
}
