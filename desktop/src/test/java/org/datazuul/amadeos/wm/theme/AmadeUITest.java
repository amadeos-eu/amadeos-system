package org.datazuul.amadeos.wm.theme;

import java.awt.Dimension;
import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AmadeUITest {

    @Test
    void testComponentsPaintWithoutGradientIndexOutOfBoundsException() {
        AmadeUI.install(DesktopTheme.WARM_MOTIF);

        assertDoesNotThrow(() -> {
            BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();

            // 1. JScrollPane with vertical & horizontal scrollbars (which previously triggered OceanPaintThumb IndexOutOfBoundsException)
            JPanel content = new JPanel();
            content.setPreferredSize(new Dimension(800, 800));
            JScrollPane scrollPane = new JScrollPane(content);
            scrollPane.setSize(200, 200);
            scrollPane.doLayout();
            scrollPane.paint(g2);

            // 2. Direct JScrollBar thumb
            JScrollBar scrollBar = new JScrollBar(JScrollBar.VERTICAL);
            scrollBar.setSize(20, 200);
            scrollBar.setValues(0, 50, 0, 200);
            scrollBar.doLayout();
            scrollBar.paint(g2);

            // 3. Other gradient-managed controls
            JButton btn = new JButton("Test");
            btn.setSize(100, 30);
            btn.paint(g2);

            JSlider slider = new JSlider();
            slider.setSize(150, 30);
            slider.paint(g2);

            JCheckBox checkBox = new JCheckBox("Box");
            checkBox.setSize(80, 25);
            checkBox.paint(g2);

            JRadioButton radio = new JRadioButton("Radio");
            radio.setSize(80, 25);
            radio.paint(g2);

            JMenuBar menuBar = new JMenuBar();
            menuBar.setSize(100, 25);
            menuBar.paint(g2);

            JMenuItem menuItem = new JMenuItem("Item");
            menuItem.setSize(100, 25);
            menuItem.paint(g2);

            g2.dispose();
        });
    }
}

