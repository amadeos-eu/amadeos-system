package eu.amadeos.system.base.power;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerSuppliesTest {
    @TempDir
    Path root;

    @Test
    void aComputerWithoutBatteryHasNone() throws IOException {
        supply("AC", "type=Mains", "online=1");
        assertEquals(Optional.empty(), new PowerSupplies(root).battery());
        assertEquals(Optional.empty(), new PowerSupplies(root.resolve("missing")).battery());
    }

    @Test
    void dischargingWithEnergyAndPower() throws IOException {
        supply("AC", "type=Mains", "online=0");
        supply("BAT0", "type=Battery", "status=Discharging", "present=1",
                "energy_now=36000000", "energy_full=48000000", "power_now=12000000", "capacity=74");
        // 36 Wh at 12 W: three hours
        assertEquals(Optional.of(new BatteryStatus(75, BatteryStatus.State.DISCHARGING, 180)),
                new PowerSupplies(root).battery());
    }

    @Test
    void chargingWithChargeAndCurrent() throws IOException {
        supply("BAT1", "type=Battery", "status=Charging",
                "charge_now=2000000", "charge_full=4000000", "current_now=-1000000");
        assertEquals(Optional.of(new BatteryStatus(50, BatteryStatus.State.CHARGING, 120)),
                new PowerSupplies(root).battery());
    }

    @Test
    void onlyCapacityGivesNoTime() throws IOException {
        supply("BAT0", "type=Battery", "status=Full", "capacity=100");
        supply("ADP1", "type=Mains", "online=1");
        assertEquals(Optional.of(new BatteryStatus(100, BatteryStatus.State.FULL, -1)),
                new PowerSupplies(root).battery());
    }

    @Test
    void twoBatteriesCountAsOneAndMouseBatteriesNot() throws IOException {
        supply("BAT0", "type=Battery", "status=Discharging", "energy_now=10000000", "energy_full=20000000",
                "power_now=5000000");
        supply("BAT1", "type=Battery", "status=Unknown", "energy_now=20000000", "energy_full=20000000");
        supply("BAT2", "type=Battery", "present=0");
        supply("hidpp_battery_0", "type=Battery", "scope=Device", "status=Discharging", "capacity=5");
        assertEquals(Optional.of(new BatteryStatus(75, BatteryStatus.State.DISCHARGING, 360)),
                new PowerSupplies(root).battery());
    }

    @Test
    void pluggedInWithChargeLimit() throws IOException {
        supply("BAT0", "type=Battery", "status=Not charging", "capacity=80");
        assertEquals(Optional.of(new BatteryStatus(80, BatteryStatus.State.NOT_CHARGING, -1)),
                new PowerSupplies(root).battery());
    }

    private void supply(String name, String... attributes) throws IOException {
        Path dir = Files.createDirectories(root.resolve(name));
        for (String a : attributes) {
            int eq = a.indexOf('=');
            Files.writeString(dir.resolve(a.substring(0, eq)), a.substring(eq + 1) + "\n");
        }
    }
}
