package eu.amadeos.panel.cde.widget;

import java.awt.Color;
import java.awt.image.BufferedImage;
import eu.amadeos.commons.theme.DesktopTheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotSpacerDockWidgetTest {
    @Test
    void theSpacerIsANarrowEmptyBayInTheThemesColours() {
        SlotSpacerDockWidget spacer = new SlotSpacerDockWidget("spacer-left");
        DesktopTheme theme = DesktopTheme.CDE_BROCADE;
        spacer.onThemeChanged(theme);
        assertEquals(SlotSpacerDockWidget.WIDTH, spacer.component().getPreferredSize().width);
        assertEquals(CdePanelChrome.SLOT_HEIGHT, spacer.component().getPreferredSize().height);

        int w = SlotSpacerDockWidget.WIDTH;
        int h = CdePanelChrome.SLOT_HEIGHT;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        spacer.component().setSize(w, h);
        spacer.component().paint(image.getGraphics());
        assertEquals(theme.panelFrameBg(), new Color(image.getRGB(5, 7)), "tab row");
        assertEquals(theme.highlight(), new Color(image.getRGB(5, CdePanelChrome.TAB_HEIGHT)), "plate top");
        assertEquals(theme.base(), new Color(image.getRGB(5, 40)), "plate");
        assertEquals(theme.darkShadow(), new Color(image.getRGB(5, h - 1)), "plate bottom");
    }
}
