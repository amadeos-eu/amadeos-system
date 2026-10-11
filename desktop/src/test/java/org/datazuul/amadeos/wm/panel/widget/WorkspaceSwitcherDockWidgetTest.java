package org.datazuul.amadeos.wm.panel.widget;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSwitcherDockWidgetTest {

    @Test
    void testWorkspaceSwitching() {
        AtomicInteger switchedWs = new AtomicInteger(-1);
        AtomicBoolean powerInvoked = new AtomicBoolean(false);

        WorkspaceSwitcherDockWidget widget = new WorkspaceSwitcherDockWidget(
                switchedWs::set,
                () -> powerInvoked.set(true)
        );

        assertEquals(220, widget.component().getPreferredSize().width);
        assertEquals(CdePanelChrome.SLOT_HEIGHT, widget.component().getPreferredSize().height);

        // Initially workspace 1 is active
        assertTrue(widget.workspaceButtons[0].isSelected(), "Workspace 'One' should be selected initially");
        assertFalse(widget.workspaceButtons[1].isSelected(), "Workspace 'Two' should not be selected initially");

        // Click workspace 2
        widget.workspaceButtons[1].doClick();
        assertEquals(2, switchedWs.get(), "Workspace listener should receive workspace 2");
        assertFalse(widget.workspaceButtons[0].isSelected(), "Workspace 'One' should now be unselected");
        assertTrue(widget.workspaceButtons[1].isSelected(), "Workspace 'Two' should now be selected");

        // Click exit button
        widget.exitBtn.doClick();
        assertTrue(powerInvoked.get(), "Power/exit action should be invoked");
    }
}
