package eu.amadeos.system.wm.decoration;

import java.awt.Graphics2D;

public interface DecorationTheme {
    String name();

    FrameMetrics metrics();

    void paintFrame(Graphics2D g, int width, int height, String title, boolean active,
                    WindowButton hoveredButton, WindowButton pressedButton);

    /** Paints the frame of a window; a style that needs more than the title (the icon, maximised) overrides it. */
    default void paintFrame(Graphics2D g, FrameWindow frame) {
        paintFrame(g, frame.frameWidth(), frame.frameHeight(), frame.displayedTitle(), frame.isActive(),
                frame.hoveredButton(), frame.pressedButton());
    }

    /**
     * The radius of the frame's rounded corners, cut with the X Shape extension; 0 for square corners. Maximised
     * and full screen windows always have square corners.
     */
    default int cornerRadius() {
        return 0;
    }
}
