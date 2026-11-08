package eu.amadeos.system.base.xdg;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XdgDirectoriesTest {
    @Test
    void usesXdgConfigHomeWhenAbsolute() {
        assertEquals(Path.of("/srv/conf"), XdgDirectories.configHome(Map.of("XDG_CONFIG_HOME", "/srv/conf"), "/home/alice"));
    }

    @Test
    void fallsBackToDotConfigWhenUnsetEmptyOrRelative() {
        assertEquals(Path.of("/home/alice/.config"), XdgDirectories.configHome(Map.of(), "/home/alice"));
        assertEquals(Path.of("/home/alice/.config"), XdgDirectories.configHome(Map.of("XDG_CONFIG_HOME", ""), "/home/alice"));
        assertEquals(Path.of("/home/alice/.config"), XdgDirectories.configHome(Map.of("XDG_CONFIG_HOME", "conf"), "/home/alice"));
    }

    @Test
    void amadeosDirectoryLivesBelowConfigHome() {
        assertEquals("amadeos", XdgDirectories.amadeosConfigDir().getFileName().toString());
    }

    @Test
    void logsLiveBelowCacheHome() {
        assertEquals(Path.of("/srv/cache"), XdgDirectories.cacheHome(Map.of("XDG_CACHE_HOME", "/srv/cache"), "/home/alice"));
        assertEquals(Path.of("/home/alice/.cache"), XdgDirectories.cacheHome(Map.of("XDG_CACHE_HOME", "cache"), "/home/alice"));
        assertTrue(XdgDirectories.amadeosLogDir().endsWith(Path.of("amadeos", "logs")));
    }

    @Test
    void systemDataDirsAddFlatpakAndSnapFolders() {
        assertEquals(java.util.List.of(Path.of("/usr/local/share"), Path.of("/usr/share"),
                        Path.of("/home/alice/.local/share/flatpak/exports/share"),
                        Path.of("/var/lib/flatpak/exports/share"), Path.of("/var/lib/snapd/desktop")),
                XdgDirectories.systemDataDirs(Map.of(), "/home/alice"));
        // Already listed (with a trailing slash, as Ubuntu's profile script writes it): not twice
        assertEquals(java.util.List.of(Path.of("/usr/share"), Path.of("/var/lib/snapd/desktop"),
                        Path.of("/data/flatpak/exports/share"), Path.of("/var/lib/flatpak/exports/share")),
                XdgDirectories.systemDataDirs(Map.of("XDG_DATA_DIRS", "/usr/share:/var/lib/snapd/desktop/",
                        "XDG_DATA_HOME", "/data"), "/home/alice"));
    }
}
