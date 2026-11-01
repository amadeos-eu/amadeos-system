package eu.amadeos.system.disks.ui;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

/** A panel in a scroll pane that is always as wide as the view, so rows of buttons wrap instead of scrolling. */
final class WidthTrackingPanel extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;

    WidthTrackingPanel() {
        super(new BorderLayout());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return visible.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
