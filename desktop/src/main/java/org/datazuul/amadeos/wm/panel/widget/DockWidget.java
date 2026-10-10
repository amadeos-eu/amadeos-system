package org.datazuul.amadeos.wm.panel.widget;

import javax.swing.JComponent;
import org.datazuul.amadeos.wm.theme.DesktopTheme;

public interface DockWidget {
    String id();
    JComponent component();
    void onThemeChanged(DesktopTheme theme);
    default void start() {}
    default void stop() {}
}

