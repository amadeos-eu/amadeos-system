package eu.amadeos.system.wm.menu;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowMenuTest {
    private final List<String> calls = new ArrayList<>();
    private final WindowMenu windowMenu = new WindowMenu(new WindowMenu.Actions() {
        @Override public void restore(long clientWin) { calls.add("restore " + clientWin); }
        @Override public void minimize(long clientWin) { calls.add("minimize " + clientWin); }
        @Override public void maximize(long clientWin) { calls.add("maximize " + clientWin); }
        @Override public void lower(long clientWin) { calls.add("lower " + clientWin); }
        @Override public void close(long clientWin) { calls.add("close " + clientWin); }
    });

    @Test
    void offersTheEntriesOfTheCdeWindowMenu() {
        List<JMenuItem> items = items(windowMenu.buildPopupMenu(42, false));
        assertEquals(List.of("Restore", "Minimize", "Maximize", "Lower", "Close"), items.stream().map(JMenuItem::getText).toList());
        for (JMenuItem item : items) {
            item.doClick();
        }
        // Restore is disabled for a window that is not maximised, so it does nothing.
        assertEquals(List.of("minimize 42", "maximize 42", "lower 42", "close 42"), calls);
    }

    @Test
    void restoreAndMaximizeFollowTheWindowState() {
        List<JMenuItem> normal = items(windowMenu.buildPopupMenu(7, false));
        assertFalse(normal.get(0).isEnabled());
        assertTrue(normal.get(2).isEnabled());
        List<JMenuItem> maximized = items(windowMenu.buildPopupMenu(7, true));
        assertTrue(maximized.get(0).isEnabled());
        assertFalse(maximized.get(2).isEnabled());
    }

    @Test
    void showAndHideAreSafeWithoutADisplay() {
        windowMenu.show(1, false, 10, 10);
        windowMenu.hide();
        windowMenu.dispose();
    }

    private static List<JMenuItem> items(JPopupMenu menu) {
        List<JMenuItem> items = new ArrayList<>();
        for (var component : menu.getComponents()) {
            if (component instanceof JMenuItem item) {
                items.add(item);
            }
        }
        return items;
    }
}
