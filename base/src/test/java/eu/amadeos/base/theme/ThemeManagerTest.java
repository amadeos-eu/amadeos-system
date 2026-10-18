package eu.amadeos.base.theme;

import java.awt.Color;
import eu.amadeos.commons.theme.DesktopTheme;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void availableIconThemesListsInstalledThemesWithNsCdeFirst(@TempDir java.nio.file.Path home) throws Exception {
        for (String theme : new String[]{"Adwaita", "NsCDE", "hicolor"}) {
            java.nio.file.Path dir = java.nio.file.Files.createDirectories(home.resolve(".icons").resolve(theme));
            java.nio.file.Files.writeString(dir.resolve("index.theme"), "[Icon Theme]\nName=" + theme + "\n");
        }
        java.nio.file.Files.createDirectories(home.resolve(".icons/default-cursors-only"));
        String previous = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        IconManager.clearCache();
        try {
            var ids = IconManager.availableIconThemes().stream().map(IconManager.IconThemeInfo::id).toList();
            assertEquals(DesktopTheme.DEFAULT_ICON_THEME, ids.get(0));
            assertTrue(ids.contains("Adwaita"));
            assertFalse(ids.contains("hicolor"), "hicolor is only the fallback");
            assertFalse(ids.contains("classic"));
            assertFalse(ids.contains("default-cursors-only"));
        } finally {
            System.setProperty("user.home", previous);
            IconManager.clearCache();
        }
    }

    @Test
    void anUninstalledIconThemeFallsBackToNsCde(@TempDir java.nio.file.Path home) throws Exception {
        java.nio.file.Path apps = java.nio.file.Files.createDirectories(home.resolve(".icons/NsCDE/32x32/apps"));
        java.awt.image.BufferedImage red = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = red.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        javax.imageio.ImageIO.write(red, "png", apps.resolve("utilities-terminal.png").toFile());
        String previous = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        IconManager.clearCache();
        try {
            Icon icon = IconManager.getIcon("classic", "terminal", 32);
            java.awt.image.BufferedImage painted = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D pg = painted.createGraphics();
            icon.paintIcon(null, pg, 0, 0);
            pg.dispose();
            assertEquals(Color.RED.getRGB(), painted.getRGB(16, 16), "taken from the NsCDE theme");
        } finally {
            System.setProperty("user.home", previous);
            IconManager.clearCache();
        }
    }

    @Test
    void nsCdeIsTheDefaultIconTheme() {
        assertEquals("NsCDE", DesktopTheme.WARM_MOTIF.iconTheme());
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

