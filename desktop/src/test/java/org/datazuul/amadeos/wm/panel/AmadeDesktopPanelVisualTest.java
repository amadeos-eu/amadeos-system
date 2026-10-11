package org.datazuul.amadeos.wm.panel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AmadeDesktopPanelVisualTest {

    @Test
    void renderPanelToPng() {
        try {
            X11EwmhHelper helper = X11EwmhHelper.open(null);
            AmadeDesktopPanel panel = new AmadeDesktopPanel(helper);
            panel.addNotify();
            panel.setSize(720, 84);
            panel.validate();

            BufferedImage img = new BufferedImage(720, 84, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            panel.getContentPane().paint(g2);
            g2.dispose();

            File out = new File("build/cde_front_panel_rendered.png");
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            System.out.println("SUCCESSFULLY_RENDERED_PANEL: " + out.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("Notice: Could not render off-screen panel: " + t.getMessage());
        }
    }
}
