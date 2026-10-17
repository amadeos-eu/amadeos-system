package org.datazuul.amadeos.wm.panel.widget;

import java.nio.file.Files;
import java.nio.file.Path;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockDockWidgetTest {

    private void cleanConfig() {
        try {
            Path conf = Path.of(System.getProperty("user.home", "/tmp"), ".config", "amadeos", "clock.conf");
            Files.deleteIfExists(conf);
        } catch (Exception ignored) {}
    }

    @BeforeEach
    void setUp() {
        cleanConfig();
    }

    @AfterEach
    void tearDown() {
        cleanConfig();
    }

    @Test
    void testClockStyleDefaultAndSwitching() {
        ClockDockWidget widget = new ClockDockWidget();

        assertNotNull(widget.component());
        assertEquals(CdePanelChrome.FIRST_SLOT_WIDTH, widget.component().getPreferredSize().width);
        assertEquals(CdePanelChrome.SLOT_HEIGHT, widget.component().getPreferredSize().height);

        // Verify default style is ANALOG
        assertEquals(ClockDockWidget.ClockStyle.ANALOG, widget.getClockStyle(), "Default style must be ANALOG");
        assertTrue(widget.analogItem.isSelected(), "Analog menu item should be selected initially");

        // Switch to DIGITAL
        widget.digitalItem.doClick();
        assertEquals(ClockDockWidget.ClockStyle.DIGITAL, widget.getClockStyle(), "Style should switch to DIGITAL");
        assertTrue(widget.digitalItem.isSelected());

        // Switch back to ANALOG programmatically
        widget.setClockStyle(ClockDockWidget.ClockStyle.ANALOG);
        assertEquals(ClockDockWidget.ClockStyle.ANALOG, widget.getClockStyle());
        assertTrue(widget.analogItem.isSelected());

        // Lifecycle & theme tests
        widget.start();
        widget.onThemeChanged(DesktopTheme.DARK_SLATE);
        widget.stop();
    }

    @Test
    void testRenderAnalogAndDigital() throws Exception {
        ClockDockWidget widget = new ClockDockWidget();
        widget.component().setSize(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT);

        // 1. Render Analog
        widget.setClockStyle(ClockDockWidget.ClockStyle.ANALOG);
        java.awt.image.BufferedImage analogImg = new java.awt.image.BufferedImage(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2a = analogImg.createGraphics();
        widget.component().paint(g2a);
        g2a.dispose();
        javax.imageio.ImageIO.write(analogImg, "png", new java.io.File("build/clock_analog_widget.png"));

        // 2. Render Digital
        widget.setClockStyle(ClockDockWidget.ClockStyle.DIGITAL);
        java.awt.image.BufferedImage digitalImg = new java.awt.image.BufferedImage(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = digitalImg.createGraphics();
        widget.component().paint(g2d);
        g2d.dispose();
        javax.imageio.ImageIO.write(digitalImg, "png", new java.io.File("build/clock_digital_widget.png"));
    }
}

