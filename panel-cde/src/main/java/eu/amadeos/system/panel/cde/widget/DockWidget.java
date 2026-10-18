package eu.amadeos.system.panel.cde.widget;

import javax.swing.JComponent;
import eu.amadeos.commons.theme.DesktopTheme;

public interface DockWidget {
    String id();
    JComponent component();
    void onThemeChanged(DesktopTheme theme);
    default void start() {}
    default void stop() {}
}

