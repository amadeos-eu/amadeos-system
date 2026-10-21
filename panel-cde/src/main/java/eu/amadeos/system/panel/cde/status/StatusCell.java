package eu.amadeos.system.panel.cde.status;

import javax.swing.JComponent;
import eu.amadeos.commons.theme.DesktopTheme;

/**
 * One indicator in the front panel's status slot (network and software updates, later sound, battery,
 * Bluetooth). A cell with nothing to show, such as a battery on a desktop computer or the updates cell while
 * the system is up to date, reports itself unavailable and the slot leaves it out.
 */
public interface StatusCell {

    JComponent component();

    /** Whether the cell has something to show; the slot lays itself out again when the cell reports a change. */
    default boolean available() {
        return true;
    }

    /**
     * Gives the cell the slot's {@link StatusDockWidget#relayout()}, which a cell whose {@link #available()}
     * changes (the updates cell) calls on the event dispatch thread. Cells that are always shown ignore it.
     */
    default void onAvailabilityChanged(Runnable relayout) {}

    void onThemeChanged(DesktopTheme theme);

    default void start() {}

    default void stop() {}
}
