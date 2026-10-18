package eu.amadeos.wm.menu;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopRootMenuTest {

    @Test
    void testBuildPopupMenuAndActions() {
        AtomicInteger switchedWs = new AtomicInteger(-1);
        AtomicBoolean shuffledUp = new AtomicBoolean(false);
        AtomicBoolean shuffledDown = new AtomicBoolean(false);
        AtomicBoolean minimizedAll = new AtomicBoolean(false);
        AtomicBoolean restoredAll = new AtomicBoolean(false);
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AtomicBoolean settingsOpened = new AtomicBoolean(false);
        AtomicBoolean locked = new AtomicBoolean(false);
        AtomicBoolean loggedOut = new AtomicBoolean(false);

        DesktopMenuActions actions = new DesktopMenuActions() {
            @Override public void switchWorkspace(int workspaceIndex) { switchedWs.set(workspaceIndex); }
            @Override public int currentWorkspace() { return 1; /* Workspace Two */ }
            @Override public void shuffleUp() { shuffledUp.set(true); }
            @Override public void shuffleDown() { shuffledDown.set(true); }
            @Override public void minimizeAll() { minimizedAll.set(true); }
            @Override public void restoreAll() { restoredAll.set(true); }
            @Override public void refreshDesktop() { refreshed.set(true); }
            @Override public void openSettings() { settingsOpened.set(true); }
            @Override public void lockScreen() { locked.set(true); }
            @Override public void logoutSession() { loggedOut.set(true); }
        };

        DesktopRootMenu rootMenu = new DesktopRootMenu(actions);
        JPopupMenu menu = rootMenu.buildPopupMenu();

        assertNotNull(menu, "Popup menu should be constructed");
        assertTrue(menu.getComponentCount() > 5, "Menu should contain banner, submenus, items, and separators");

        // Inspect menu items
        boolean foundWorkspaces = false;
        boolean foundWinManagement = false;
        boolean foundPrograms = false;

        for (int i = 0; i < menu.getComponentCount(); i++) {
            var comp = menu.getComponent(i);
            if (comp instanceof JMenu subMenu) {
                if ("Workspaces".equals(subMenu.getText())) {
                    foundWorkspaces = true;
                    assertEquals(4, subMenu.getItemCount(), "Workspaces menu should contain 4 items");

                    // Check that Workspace 2 has the checkmark prefix
                    JMenuItem ws2 = subMenu.getItem(1);
                    assertTrue(ws2.getText().contains("✔"), "Workspace Two should be marked as active: " + ws2.getText());

                    // Click Workspace 3 (index 2)
                    JMenuItem ws3 = subMenu.getItem(2);
                    ws3.doClick();
                    assertEquals(2, switchedWs.get(), "Clicking Workspace Three should trigger switchWorkspace(2)");
                } else if ("Window Management".equals(subMenu.getText())) {
                    foundWinManagement = true;
                    // Test Shuffle Up
                    JMenuItem shuffleUpItem = subMenu.getItem(0);
                    shuffleUpItem.doClick();
                    assertTrue(shuffledUp.get(), "Shuffle Up should be invoked");

                    // Test Shuffle Down
                    JMenuItem shuffleDownItem = subMenu.getItem(1);
                    shuffleDownItem.doClick();
                    assertTrue(shuffledDown.get(), "Shuffle Down should be invoked");
                } else if ("Programs".equals(subMenu.getText())) {
                    foundPrograms = true;
                    assertTrue(subMenu.getItemCount() > 0, "Programs menu should have subcategories or defaults");
                }
            } else if (comp instanceof JMenuItem item) {
                if ("Refresh Desktop".equals(item.getText())) {
                    item.doClick();
                    assertTrue(refreshed.get(), "Refresh Desktop should be invoked");
                } else if ("Lock Screen".equals(item.getText())) {
                    item.doClick();
                    assertTrue(locked.get(), "Lock Screen should be invoked");
                } else if ("Log Out...".equals(item.getText())) {
                    item.doClick();
                    assertTrue(loggedOut.get(), "Log Out should be invoked");
                }
            }
        }

        assertTrue(foundWorkspaces, "Workspaces submenu must be present");
        assertTrue(foundWinManagement, "Window Management submenu must be present");
        assertTrue(foundPrograms, "Programs submenu must be present");

        // Test safe headless show/hide/dispose calls
        rootMenu.show(100, 100);
        rootMenu.hide();
        rootMenu.dispose();
    }
}

