package org.datazuul.dazeos.wm.panel.widget;

import javax.swing.JComponent;
import org.datazuul.dazeos.wm.theme.DesktopTheme;

public interface DockWidget {
    String id();
    JComponent component();
    void onThemeChanged(DesktopTheme theme);
    default void start() {}
    default void stop() {}
}
