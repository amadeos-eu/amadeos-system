package org.datazuul.amadeos.wm;

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
}
