package eu.amadeos.system.base.bluetooth;

import java.util.Comparator;
import java.util.List;

/**
 * The Bluetooth adapter of the computer and the devices BlueZ knows.
 *
 * @param available    whether BlueZ (bluetoothd) answered
 * @param adapter      the first adapter, null when the computer has none (or it is blocked by rfkill)
 * @param devices      paired devices and those found while searching
 */
public record BluetoothStatus(boolean available, Adapter adapter, List<BluetoothDevice> devices) {

    public static final BluetoothStatus UNAVAILABLE = new BluetoothStatus(false, null, List.of());

    public BluetoothStatus {
        devices = List.copyOf(devices);
    }

    /**
     * @param id           BlueZ's object path, e.g. {@code /org/bluez/hci0}
     * @param name         the name other devices see, e.g. {@code amadeos}
     * @param powered      whether Bluetooth is switched on
     * @param discoverable whether other devices can find this computer
     * @param discovering  whether the adapter is searching for devices
     */
    public record Adapter(String id, String address, String name, boolean powered, boolean discoverable,
                          boolean discovering) {
    }

    public boolean hasAdapter() {
        return adapter != null;
    }

    /** The paired devices, connected ones first, then by name. */
    public List<BluetoothDevice> paired() {
        return devices.stream().filter(BluetoothDevice::paired).sorted(Comparator
                .comparing(BluetoothDevice::connected).reversed()
                .thenComparing(BluetoothDevice::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Devices in range that are not paired, named ones first, then by signal strength. */
    public List<BluetoothDevice> nearby() {
        return devices.stream().filter(d -> !d.paired()).sorted(Comparator
                .comparing((BluetoothDevice d) -> d.name().equals(d.address()))
                .thenComparing(Comparator.comparingInt(BluetoothDevice::signal).reversed())
                .thenComparing(BluetoothDevice::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
