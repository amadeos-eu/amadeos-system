package org.datazuul.amadeos.wm.decoration;

import java.awt.Graphics2D;

public interface DecorationTheme {
    String name();

    FrameMetrics metrics();

    void paintFrame(Graphics2D g, int width, int height, String title, boolean active,
                    WindowButton hoveredButton, WindowButton pressedButton);
}
