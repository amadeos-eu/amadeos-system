package eu.amadeos.system.power;

import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerPolicyTest {
    private static final long MIN = 60_000;
    private final PowerSettings settings = PowerSettings.DEFAULTS; // battery: suspend 15 min; mains: never
    private final PowerPolicy policy = new PowerPolicy();

    private List<PowerPolicy.Step> at(long idle, boolean battery, int percent, boolean lid, boolean docked) {
        return policy.next(settings, new PowerPolicy.State(idle, battery, percent, lid, docked, false));
    }

    private static PowerPolicy.Step act(PowerAction action, PowerPolicy.Reason reason) {
        return new PowerPolicy.Step.Act(action, reason);
    }

    @Test
    void suspendsOnceAfterIdleTimeOnBattery() {
        assertEquals(List.of(), at(14 * MIN, true, 80, false, false));
        assertEquals(List.of(act(PowerAction.SUSPEND, PowerPolicy.Reason.IDLE)), at(15 * MIN, true, 80, false, false));
        assertEquals(List.of(), at(16 * MIN, true, 80, false, false));
        at(100, true, 80, false, false); // input
        assertEquals(List.of(act(PowerAction.SUSPEND, PowerPolicy.Reason.IDLE)), at(15 * MIN, true, 80, false, false));
    }

    @Test
    void neverSuspendsPluggedInByDefault() {
        assertEquals(List.of(), at(600 * MIN, false, 80, false, false));
    }

    @Test
    void noIdleSuspendRightAfterWakingUp() {
        at(1000, true, 80, false, false);
        policy.wokeUp();
        assertEquals(List.of(), at(60 * MIN, true, 80, false, false), "the idle time still counts the sleep");
    }

    @Test
    void lidActsWhenClosingNotWhileClosed() {
        assertEquals(List.of(act(PowerAction.SUSPEND, PowerPolicy.Reason.LID)), at(0, false, -1, true, false));
        assertEquals(List.of(), at(0, false, -1, true, false));
        at(0, false, -1, false, false);
        assertEquals(List.of(), at(0, false, -1, true, true), "an external screen is connected");
    }

    @Test
    void warnsThenActsOnLowBatteryOnce() {
        assertEquals(List.of(), at(0, true, 11, false, false));
        assertEquals(List.of(new PowerPolicy.Step.Warn(10)), at(0, true, 10, false, false));
        assertEquals(List.of(), at(0, true, 8, false, false));
        assertEquals(List.of(act(PowerAction.HIBERNATE, PowerPolicy.Reason.LOW_BATTERY)), at(0, true, 5, false, false));
        assertEquals(List.of(), at(0, true, 4, false, false));
        at(0, false, 4, false, false); // plugged in
        assertEquals(List.of(act(PowerAction.HIBERNATE, PowerPolicy.Reason.LOW_BATTERY)), at(0, true, 4, false, false));
    }
}
