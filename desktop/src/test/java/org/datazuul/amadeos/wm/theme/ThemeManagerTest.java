package org.datazuul.amadeos.wm.theme;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeManagerTest {

    @AfterEach
    void tearDown() {
        ThemeManager.applyTheme("warm-motif");
    }

    @Test
    void defaultThemeIsWarmMotifWithMotifBeigePalette() {
        DesktopTheme current = ThemeManager.currentTheme();
        assertNotNull(current);
        assertEquals("warm-motif", current.id());
        assertEquals(new Color(0xCE, 0xC2, 0xBB), current.base());
    }

    @Test
    void allBuiltInThemesAreAvailable() {
        var themes = ThemeManager.availableThemes();
        assertTrue(themes.size() >= 5);
        assertTrue(themes.stream().anyMatch(t -> "warm-motif".equals(t.id())));
        assertTrue(themes.stream().anyMatch(t -> "cde-brocade".equals(t.id())));
        assertTrue(themes.stream().anyMatch(t -> "cde-desert".equals(t.id())));
        assertTrue(themes.stream().anyMatch(t -> "classic-gray".equals(t.id())));
        assertTrue(themes.stream().anyMatch(t -> "dark-slate".equals(t.id())));
    }

    @Test
    void applyThemeNotifiesListeners() {
        AtomicReference<DesktopTheme> received = new AtomicReference<>();
        ThemeManager.ThemeChangeListener listener = received::set;
        ThemeManager.addListener(listener);

        try {
            ThemeManager.applyTheme("cde-brocade");
            assertNotNull(received.get());
            assertEquals("cde-brocade", received.get().id());
            assertEquals("cde-brocade", ThemeManager.currentTheme().id());
        } finally {
            ThemeManager.removeListener(listener);
        }
    }

    @Test
    void iconManagerResolvesOrGeneratesAllRequiredIcons() {
        String[] keys = {"file-manager", "text-editor", "terminal", "lock", "power", "trash", "diagnostics", "window"};
        for (String key : keys) {
            Icon icon = IconManager.getIcon(key, 32);
            assertNotNull(icon, "Icon for key '" + key + "' should not be null");
            assertEquals(32, icon.getIconWidth());
            assertEquals(32, icon.getIconHeight());
        }
    }

    @Test
    void freedesktopBaseDirectoriesIncludeStandardXdgPaths() {
        var dirs = IconManager.getIconBaseDirectories();
        assertFalse(dirs.isEmpty());
        assertTrue(dirs.stream().anyMatch(d -> d.getPath().contains("icons")));
    }

    @Test
    void availableIconThemesDiscoversInstalledThemes() {
        var themes = IconManager.availableIconThemes();
        assertFalse(themes.isEmpty());
        assertTrue(themes.stream().anyMatch(t -> "classic".equals(t.id())));
    }

    @Test
    void iconManagerResolvesStandardFreedesktopNamesDirectly() {
        Icon termIcon = IconManager.getIcon("utilities-terminal", 32);
        assertNotNull(termIcon);
        assertEquals(32, termIcon.getIconWidth());
        assertEquals(32, termIcon.getIconHeight());

        Icon fmIcon = IconManager.getIcon("system-file-manager", 32);
        assertNotNull(fmIcon);
        assertEquals(32, fmIcon.getIconWidth());
        assertEquals(32, fmIcon.getIconHeight());
    }

    @Test
    void desktopThemeDerivesCohesive3DPalette() {
        DesktopTheme custom = DesktopTheme.fromBase("custom", "Custom Teal", new Color(0x30, 0x70, 0x90));
        assertNotNull(custom.highlight());
        assertNotNull(custom.softHighlight());
        assertNotNull(custom.shadow());
        assertNotNull(custom.darkShadow());
        assertNotNull(custom.sunkenBg());
        assertNotNull(custom.textActive());
        assertNotNull(custom.textInactive());
    }
}

