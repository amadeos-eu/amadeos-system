package org.datazuul.amadeos.wm.panel.widget;

import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.datazuul.amadeos.commons.i18n.LocaleStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSwitcherDockWidgetTest {
    @TempDir
    Path config;

    @Test
    void testWorkspaceSwitching() {
        AtomicInteger switchedWs = new AtomicInteger(-1);
        AtomicBoolean powerInvoked = new AtomicBoolean(false);

        WorkspaceSwitcherDockWidget widget = new WorkspaceSwitcherDockWidget(
                switchedWs::set,
                () -> powerInvoked.set(true),
                new LocaleStore(config)
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

        // Programmatic workspace updates (e.g. from EWMH or root menu)
        assertEquals(2, widget.activeWorkspace());
        widget.setActiveWorkspace(4);
        assertEquals(4, widget.activeWorkspace());
        assertTrue(widget.workspaceButtons[3].isSelected(), "Workspace 'Four' should now be selected");
        assertFalse(widget.workspaceButtons[1].isSelected(), "Workspace 'Two' should now be unselected");
    }

    @Test
    void theLanguageButtonShowsAndSwitchesTheDesktopLanguage() throws Exception {
        Files.writeString(config.resolve(LocaleStore.LOCALE_FILE), "de_AT.UTF-8\n");
        LocaleStore store = new LocaleStore(config);
        WorkspaceSwitcherDockWidget widget = new WorkspaceSwitcherDockWidget(ws -> { }, () -> { }, store);

        assertEquals("DE", widget.languageBtn.code());
        assertEquals("Sprache: Deutsch", widget.languageBtn.getToolTipText());

        JPopupMenu menu = widget.languageMenu();
        List<String> languages = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            if (c instanceof JRadioButtonMenuItem item) {
                languages.add(item.getText());
                assertEquals(item.getName().equals("de"), item.isSelected(), item.getText());
            }
        }
        assertEquals(List.of("English", "Deutsch", "Français", "Español", "Italiano"), languages);
        JMenuItem lastHint = (JMenuItem) menu.getComponent(menu.getComponentCount() - 1);
        assertFalse(lastHint.isEnabled());
        assertEquals("Leiste und Terminals folgen bei der nächsten Anmeldung.", lastHint.getText());

        for (Component c : menu.getComponents()) {
            if (c instanceof JRadioButtonMenuItem item && item.getName().equals("fr")) item.doClick();
        }
        assertEquals("fr_FR.UTF-8", Files.readString(config.resolve(LocaleStore.LOCALE_FILE)).strip());
        assertEquals("FR", widget.languageBtn.code());
        assertEquals("Langue : Français", widget.languageBtn.getToolTipText());
    }
}
