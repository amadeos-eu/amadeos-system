package eu.amadeos.system.panel.cde.widget;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.theme.backdrop.MotifColors;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.theme.DesktopTheme;
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

        assertEquals(WorkspaceSwitcherDockWidget.PAD_WIDTH, widget.component().getPreferredSize().width);
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
    void theWorkspaceButtonsWearTheThemesWorkspaceColours() {
        WorkspaceSwitcherDockWidget widget = new WorkspaceSwitcherDockWidget(ws -> { }, () -> { },
                new LocaleStore(config));
        DesktopTheme theme = ThemeManager.currentTheme();
        for (int i = 0; i < 4; i++) {
            assertEquals(theme.workspaceColor(i), widget.workspaceButtons[i].color(), "workspace " + (i + 1));
            assertEquals(WorkspaceSwitcherDockWidget.workspaceName(i), widget.workspaceButtons[i].getText());
        }
        assertEquals(4, Set.of(widget.workspaceButtons[0].color(), widget.workspaceButtons[1].color(),
                widget.workspaceButtons[2].color(), widget.workspaceButtons[3].color()).size(),
                "four different colours, as in a CDE palette");

        // A theme change recolours the buttons
        widget.onThemeChanged(DesktopTheme.DARK_SLATE);
        for (int i = 0; i < 4; i++) {
            assertEquals(DesktopTheme.DARK_SLATE.workspaceColor(i), widget.workspaceButtons[i].color());
        }

        // The buttons paint without a window: the current one pressed in, the others raised
        BufferedImage image = new BufferedImage(96, 28, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 4; i++) {
            widget.workspaceButtons[i].setSize(96, 28);
            widget.workspaceButtons[i].paint(image.getGraphics());
            Color fill = new Color(image.getRGB(48, 4));
            assertEquals(DesktopTheme.DARK_SLATE.workspaceColor(i), fill, "fill of workspace " + (i + 1));
            MotifColors motif = MotifColors.of(fill);
            Color topEdge = new Color(image.getRGB(48, 0));
            assertEquals(i == 0 ? motif.bottomShadow() : motif.topShadow(), topEdge, "top edge of workspace " + (i + 1));
        }
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
