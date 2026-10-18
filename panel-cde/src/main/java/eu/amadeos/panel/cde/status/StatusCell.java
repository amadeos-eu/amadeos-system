package eu.amadeos.panel.cde.status;

import javax.swing.JComponent;
import eu.amadeos.commons.theme.DesktopTheme;

/**
 * One indicator in the front panel's status slot (network, later sound, battery, Bluetooth). A cell
 * without hardware to show, such as a battery on a desktop computer, reports itself unavailable and the
 * slot leaves it out.
 */
public interface StatusCell {

    JComponent component();

    /** Whether the cell has something to show; the slot calls {@link StatusDockWidget#relayout()} on change. */
    default boolean available() {
        return true;
    }

    void onThemeChanged(DesktopTheme theme);

    default void start() {}

    default void stop() {}
}
