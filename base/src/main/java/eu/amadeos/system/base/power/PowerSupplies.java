package eu.amadeos.system.base.power;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the batteries from the kernel's power supply class ({@code /sys/class/power_supply}), which needs no
 * privileges and no daemon. Only system batteries count: the batteries of a wireless mouse or keyboard
 * (scope {@code Device}) are left out, and so are empty battery bays. Several batteries are added up like one.
 */
public final class PowerSupplies {
    public static final Path SYSFS = Path.of("/sys/class/power_supply");

    private final Path root;

    public PowerSupplies() {
        this(SYSFS);
    }

    public PowerSupplies(Path root) {
        this.root = root;
    }

    /** The batteries together, or empty on a computer without one (a desktop, most virtual machines). */
    public Optional<BatteryStatus> battery() {
        // --8<-- [start:batteries]
        List<Path> batteries = new ArrayList<>();
        try (DirectoryStream<Path> supplies = Files.newDirectoryStream(root)) {
            for (Path supply : supplies) {
                if ("Battery".equals(read(supply, "type"))
                        && !"Device".equals(read(supply, "scope"))
                        && !"0".equals(read(supply, "present"))) {
                    batteries.add(supply);
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        // --8<-- [end:batteries]
        if (batteries.isEmpty()) return Optional.empty();
        batteries.sort(null);

        double now = 0, full = 0, rate = 0;
        int capacitySum = 0;
        boolean measured = true;
        boolean discharging = false, charging = false, notCharging = false;
        for (Path b : batteries) {
            // Energy in µWh and power in µW, or charge in µAh and current in µA, depending on the firmware
            long n = number(b, "energy_now", "charge_now");
            long f = number(b, "energy_full", "charge_full");
            long r = Math.abs(number(b, "power_now", "current_now"));
            if (n >= 0 && f > 0) {
                now += n;
                full += f;
                if (r > 0) rate += r;
            } else {
                measured = false;
            }
            capacitySum += Math.max(0, number(b, "capacity"));
            switch (String.valueOf(read(b, "status"))) {
                case "Discharging" -> discharging = true;
                case "Charging" -> charging = true;
                case "Not charging" -> notCharging = true;
                default -> { }
            }
        }

        int percent = measured && full > 0
                ? (int) Math.round(100 * now / full)
                : Math.round((float) capacitySum / batteries.size());
        BatteryStatus.State state = discharging ? BatteryStatus.State.DISCHARGING
                : charging ? BatteryStatus.State.CHARGING
                : notCharging ? BatteryStatus.State.NOT_CHARGING
                : onMains() || percent >= 95 ? BatteryStatus.State.FULL
                : BatteryStatus.State.DISCHARGING;

        int minutes = -1;
        if (measured && rate > 0) {
            if (state == BatteryStatus.State.DISCHARGING) {
                minutes = (int) Math.round(60 * now / rate);
            } else if (state == BatteryStatus.State.CHARGING) {
                minutes = (int) Math.round(60 * (full - now) / rate);
            }
        }
        return Optional.of(new BatteryStatus(percent, state, minutes));
    }

    /** Whether a mains adapter is plugged in. */
    boolean onMains() {
        try (DirectoryStream<Path> supplies = Files.newDirectoryStream(root)) {
            for (Path supply : supplies) {
                if ("Mains".equals(read(supply, "type")) && "1".equals(read(supply, "online"))) return true;
            }
        } catch (IOException e) {
            // no adapter to be seen
        }
        return false;
    }

    /** The first of the files that holds a number, or -1. */
    private static long number(Path supply, String... names) {
        for (String name : names) {
            String value = read(supply, name);
            if (value == null) continue;
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // try the next one
            }
        }
        return -1;
    }

    private static String read(Path supply, String name) {
        Path file = supply.resolve(name);
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
