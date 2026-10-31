package eu.amadeos.system.power;

import eu.amadeos.commons.i18n.TranslationCheck;
import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerControl;
import eu.amadeos.system.base.power.PowerSettings;
import eu.amadeos.system.base.power.PowerSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PowerManagerTest {
    @TempDir
    Path dir;

    private final List<String> calls = new ArrayList<>();
    private boolean hibernate;
    private boolean lid;
    private Optional<BatteryStatus> battery = Optional.of(new BatteryStatus(80, BatteryStatus.State.DISCHARGING, 120));

    private final PowerControl control = new PowerControl() {
        @Override public boolean canSuspend() { return true; }
        @Override public boolean canHibernate() { return hibernate; }
        @Override public void suspend() { calls.add("suspend"); }
        @Override public void hibernate() { calls.add("hibernate"); }
        @Override public void powerOff() { calls.add("power-off"); }
        @Override public boolean lidClosed() { return lid; }
        @Override public boolean docked() { return false; }
        @Override public AutoCloseable onSleep(Consumer<Boolean> listener) { return () -> { }; }
    };

    private final ScreenPower screen = new ScreenPower() {
        @Override public void setTimeout(int seconds) { calls.add("dpms " + seconds); }
        @Override public void turnOff() { calls.add("screen off"); }
    };

    private PowerManager manager(PowerSettings settings) {
        PowerSettingsStore store = new PowerSettingsStore(dir.resolve("power.conf"));
        store.save(settings);
        return new PowerManager(store, control, screen, () -> battery, () -> calls.add("lock"), () -> false,
                percent -> calls.add("warn " + percent));
    }

    @Test
    void screenTimeoutFollowsThePowerSource() {
        PowerManager m = manager(PowerSettings.DEFAULTS);
        m.poll(0, false);
        m.poll(1000, false);
        battery = Optional.of(new BatteryStatus(80, BatteryStatus.State.CHARGING, 30));
        m.poll(2000, false);
        assertEquals(List.of("dpms 300", "dpms 900"), calls);
    }

    @Test
    void lidLocksWhenChosen() {
        PowerManager m = manager(PowerSettings.DEFAULTS.withBattery(new PowerSettings.Profile(5, 15, PowerAction.LOCK)));
        lid = true;
        m.poll(0, false);
        assertTrue(calls.contains("lock"));
    }

    @Test
    void hibernateFallsBack() {
        PowerManager m = manager(PowerSettings.DEFAULTS);
        m.act(PowerAction.HIBERNATE, PowerPolicy.Reason.LID);
        m.act(PowerAction.HIBERNATE, PowerPolicy.Reason.LOW_BATTERY);
        hibernate = true;
        m.act(PowerAction.HIBERNATE, PowerPolicy.Reason.LOW_BATTERY);
        assertEquals(List.of("suspend", "power-off", "hibernate"), calls);
    }

    @Test
    void lowBatteryWarns() {
        PowerManager m = manager(PowerSettings.DEFAULTS);
        battery = Optional.of(new BatteryStatus(9, BatteryStatus.State.DISCHARGING, 20));
        m.poll(0, false);
        assertTrue(calls.contains("warn 9"));
    }

    @Test
    void warningIsACriticalNotificationOrTheFallback() {
        var texts = eu.amadeos.commons.i18n.Messages.load(PowerManager.class, PowerManager.TEXTS, java.util.Locale.ENGLISH);
        List<eu.amadeos.system.base.notify.DesktopNotification> sent = new ArrayList<>();
        Consumer<Integer> warning = PowerManager.notificationWarning(texts, n -> {
            sent.add(n);
            return sent.size() == 1 ? 7 : 0; // the second time no daemon answers
        }, () -> calls.add("settings"), percent -> calls.add("dialog " + percent));
        warning.accept(9);
        assertEquals(eu.amadeos.system.base.notify.DesktopNotification.Urgency.CRITICAL, sent.getFirst().urgency());
        assertEquals("amadeos-settings", sent.getFirst().desktopEntry());
        assertEquals("The battery is down to 9 %. Plug in the computer soon.", sent.getFirst().body());
        sent.getFirst().onAction().accept("default");
        warning.accept(8);
        assertEquals(List.of("settings", "dialog 8"), calls);
    }

    @Test
    void translations() {
        assertEquals(Map.of(), TranslationCheck.problems(PowerManager.class, PowerManager.TEXTS));
    }
}
