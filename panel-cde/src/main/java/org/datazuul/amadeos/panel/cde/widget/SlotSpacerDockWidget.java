package org.datazuul.amadeos.panel.cde.widget;

import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

/**
 * The narrow empty bay between the last slot of a side and the workspace switch, as in CDE's Front
 * Panel: a strip of the drawer tab row (without triangle) above a piece of button plate, with nothing
 * in it.
 */
public final class SlotSpacerDockWidget extends AbstractDockWidget {
    /** Width of the empty bay. */
    public static final int WIDTH = 10;
    private final JComponent bay;

    public SlotSpacerDockWidget(String id) {
        super(id);
        bay = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                CdePanelChrome.paintSlotBay(g, getWidth(), getHeight(), currentTheme);
            }
        };
        bay.setOpaque(true);
        bay.setPreferredSize(new Dimension(WIDTH, CdePanelChrome.SLOT_HEIGHT));
    }

    @Override
    public JComponent component() {
        return bay;
    }

    @Override
    protected void updateThemeStyles(DesktopTheme theme) {
        bay.repaint();
    }
}
