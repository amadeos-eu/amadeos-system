package eu.amadeos.system.base.power;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerSettingsStoreTest {
    @TempDir
    Path dir;

    @Test
    void defaultsWithoutFile() {
        assertEquals(PowerSettings.DEFAULTS, new PowerSettingsStore(dir.resolve("power.conf")).load());
    }

    @Test
    void savesAndLoads() {
        PowerSettingsStore store = new PowerSettingsStore(dir.resolve("power.conf"));
        PowerSettings s = new PowerSettings(new PowerSettings.Profile(2, 10, PowerAction.LOCK),
                new PowerSettings.Profile(PowerSettings.NEVER, 60, PowerAction.NOTHING), PowerAction.SUSPEND,
                false, false, 20, 7, PowerAction.SHUTDOWN);
        store.save(s);
        assertEquals(s, store.load());
    }

    @Test
    void unknownAndUnfitValuesFallBack() throws Exception {
        Path file = dir.resolve("power.conf");
        Files.write(file, List.of("battery.lid=ask", "power-button=screen-off", "low-battery.action=lock",
                "mains.suspend=abc", "low-battery.percent=99", "low-battery.warn=3"));
        PowerSettings s = new PowerSettingsStore(file).load();
        assertEquals(PowerAction.SUSPEND, s.battery().lid());
        assertEquals(PowerAction.ASK, s.powerKey());
        assertEquals(PowerAction.HIBERNATE, s.lowAction());
        assertEquals(PowerSettings.NEVER, s.mains().suspendMinutes());
        assertEquals(30, s.lowPercent());
        assertEquals(31, s.warnPercent(), "the warning comes before the action");
    }

    @Test
    void actionKeys() {
        assertEquals("screen-off", PowerAction.SCREEN_OFF.key());
        assertEquals(PowerAction.SCREEN_OFF, PowerAction.parse(" Screen-Off ", PowerAction.NOTHING));
        assertEquals(PowerAction.NOTHING, PowerAction.parse(null, PowerAction.NOTHING));
    }

    @Test
    void inhibitorCommand() {
        assertEquals(List.of("systemd-inhibit", "--what=handle-lid-switch", "--who=amadeOS", "--why=x", "--mode=block", "cat"),
                Inhibitor.command("handle-lid-switch", "block", "x"));
    }
}
