package org.datazuul.amadeos.panel.cde.widget;

import javax.swing.JComponent;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

public interface DockWidget {
    String id();
    JComponent component();
    void onThemeChanged(DesktopTheme theme);
    default void start() {}
    default void stop() {}
}

