package org.datazuul.amadeos.wm.panel.widget;

import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawerDockWidgetTest {

    @Test
    void testToggleDrawerMenu() {
        DrawerDockWidget.DrawerTool tool = new DrawerDockWidget.DrawerTool("Tool 1", null, e -> {});
        DrawerDockWidget widget = new DrawerDockWidget("test", "Test Tool", "terminal", e -> {}, List.of(tool));

        JFrame frame = new JFrame();
        frame.getContentPane().add(widget.component());
        frame.pack();

        assertFalse(widget.drawerPopup.isVisible(), "Popup should initially be closed");

        // First click: opens popup
        widget.tabBtn.doClick();
        assertTrue(widget.drawerPopup.isVisible(), "Popup should be open after first click");

        // Second click: closes popup
        widget.tabBtn.doClick();
        assertFalse(widget.drawerPopup.isVisible(), "Popup should be closed after second click");

        frame.dispose();
    }

    @Test
    void testSecondMouseClickDismissesAndDoesNotReopen() {
        DrawerDockWidget.DrawerTool tool = new DrawerDockWidget.DrawerTool("Tool 1", null, e -> {});
        DrawerDockWidget widget = new DrawerDockWidget("test", "Test Tool", "terminal", e -> {}, List.of(tool));

        JFrame frame = new JFrame();
        frame.getContentPane().add(widget.component());
        frame.pack();

        // Open popup
        widget.tabBtn.doClick();
        assertTrue(widget.drawerPopup.isVisible());

        // Simulate Swing dismissing the popup right before delivering mousePressed to tabBtn
        widget.drawerPopup.setVisible(false);
        assertFalse(widget.drawerPopup.isVisible());

        // Deliver mouse pressed and released to tabBtn
        long now = System.currentTimeMillis();
        widget.tabBtn.dispatchEvent(new MouseEvent(widget.tabBtn, MouseEvent.MOUSE_PRESSED, now, 0, 5, 5, 1, false));
        widget.tabBtn.dispatchEvent(new MouseEvent(widget.tabBtn, MouseEvent.MOUSE_RELEASED, now + 20, 0, 5, 5, 1, false));

        // It should NOT re-open
        assertFalse(widget.drawerPopup.isVisible(), "Popup should remain closed after second mouse click");

        frame.dispose();
    }
}
