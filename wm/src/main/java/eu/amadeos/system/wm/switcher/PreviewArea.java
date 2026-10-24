package eu.amadeos.system.wm.switcher;

import java.awt.Rectangle;
import java.util.List;

/**
 * Where on screen a window's preview can be read from. Without a compositor the X server keeps no picture of
 * covered windows, so the window manager reads the screen while a window is in full view.
 */
public final class PreviewArea {
    private PreviewArea() {
    }

    /**
     * The part of {@code frame} that can be read for its preview: clipped to the screen and to the area above
     * the front panel, and only if no window above it overlaps that part.
     *
     * @param frame  the window's frame on screen
     * @param above  the frames of the mapped windows stacked above it
     * @param screen the usable screen area (without the front panel)
     * @return the area to read, or null if the window is covered or too small to show
     */
    public static Rectangle readable(Rectangle frame, List<Rectangle> above, Rectangle screen) {
        Rectangle visible = frame.intersection(screen);
        if (visible.width < 16 || visible.height < 16) return null;
        for (Rectangle other : above) {
            if (other.intersects(visible)) return null;
        }
        return visible;
    }
}
