package eu.amadeos.system.wm.power;

import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerSettings;
import eu.amadeos.system.base.power.PowerSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerButtonTest {
    @TempDir
    Path dir;

    private final List<String> calls = new ArrayList<>();
    private long now = 10_000;

    private PowerButton button(PowerAction action) {
        PowerSettingsStore store = new PowerSettingsStore(dir.resolve("power.conf"));
        PowerSettings d = PowerSettings.DEFAULTS;
        store.save(new PowerSettings(d.battery(), d.mains(), action, d.lidIgnoreDocked(), d.lockOnWake(),
                d.warnPercent(), d.lowPercent(), d.lowAction()));
        return new PowerButton(store, new PowerButton.Actions() {
            @Override public void ask() { calls.add("ask"); }
            @Override public void suspend() { calls.add("suspend"); }
            @Override public void hibernate() { calls.add("hibernate"); }
            @Override public void shutDown() { calls.add("shutdown"); }
        }, () -> now);
    }

    @Test
    void asksByDefaultButNotWhileLocked() {
        PowerButton b = button(PowerAction.ASK);
        b.pressed(false);
        now += 5000;
        b.pressed(true);
        assertEquals(List.of("ask"), calls);
    }

    @Test
    void doublePressCountsOnce() {
        PowerButton b = button(PowerAction.SUSPEND);
        b.pressed(true);
        now += 200;
        b.pressed(true);
        assertEquals(List.of("suspend"), calls);
    }

    @Test
    void nothingDoesNothing() {
        button(PowerAction.NOTHING).pressed(false);
        assertEquals(List.of(), calls);
    }
}
