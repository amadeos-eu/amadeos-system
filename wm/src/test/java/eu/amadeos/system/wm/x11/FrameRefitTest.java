package eu.amadeos.system.wm.x11;

import eu.amadeos.system.wm.decoration.FrameMetrics;
import eu.amadeos.system.wm.decoration.HorizonFrameMetrics;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameRefitTest {
    @Test
    void theClientKeepsPlaceAndSizeUnderTheOtherFrame() {
        FrameMetrics motif = FrameMetrics.defaultMotif();
        FrameMetrics horizon = new HorizonFrameMetrics();
        Rectangle motifFrame = new Rectangle(100, 80, motif.frameWidth(500), motif.frameHeight(300));
        Rectangle horizonFrame = X11WindowManagerService.convert(motifFrame, motif, horizon);
        assertEquals(100 + motif.clientOffsetX(), horizonFrame.x + horizon.clientOffsetX());
        assertEquals(80 + motif.clientOffsetY(), horizonFrame.y + horizon.clientOffsetY());
        assertEquals(500, horizon.clientWidth(horizonFrame.width));
        assertEquals(300, horizon.clientHeight(horizonFrame.height));
        assertEquals(motifFrame, X11WindowManagerService.convert(horizonFrame, horizon, motif));
    }
}
