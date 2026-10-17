package org.datazuul.amadeos.base.xdg;

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
}
