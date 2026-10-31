package eu.amadeos.system.settings;

import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.base.power.PowerControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** A laptop whose logind only records what it was asked to do. */
final class FakePower implements PowerControl {
    final List<String> calls = new ArrayList<>();
    boolean hibernate;

    FakePower() {
        this(false);
    }

    FakePower(boolean hibernate) {
        this.hibernate = hibernate;
    }

    static Optional<BatteryStatus> laptopBattery() {
        return Optional.of(new BatteryStatus(76, BatteryStatus.State.DISCHARGING, 190));
    }

    @Override
    public boolean canSuspend() {
        return true;
    }

    @Override
    public boolean canHibernate() {
        return hibernate;
    }

    @Override
    public void suspend() {
        calls.add("suspend");
    }

    @Override
    public void hibernate() {
        calls.add("hibernate");
    }

    @Override
    public void powerOff() {
        calls.add("power-off");
    }

    @Override
    public boolean lidClosed() {
        return false;
    }

    @Override
    public boolean docked() {
        return false;
    }

    @Override
    public AutoCloseable onSleep(Consumer<Boolean> listener) {
        return () -> { };
    }
}
