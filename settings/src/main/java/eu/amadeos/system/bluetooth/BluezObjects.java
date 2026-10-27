package eu.amadeos.system.bluetooth;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.types.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reads BlueZ's object tree ({@code GetManagedObjects} on {@code /}) into a {@link BluetoothStatus}. Pure, so it
 * can be tested without bluetoothd.
 */
final class BluezObjects {
    static final String ADAPTER = "org.bluez.Adapter1";
    static final String DEVICE = "org.bluez.Device1";
    static final String BATTERY = "org.bluez.Battery1";

    private BluezObjects() {
    }

    static BluetoothStatus status(Map<DBusPath, Map<String, Map<String, Variant<?>>>> objects) {
        List<Map.Entry<DBusPath, Map<String, Map<String, Variant<?>>>>> sorted = new ArrayList<>(objects.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().getPath()));
        BluetoothStatus.Adapter adapter = null;
        for (var entry : sorted) {
            Map<String, Variant<?>> a = entry.getValue().get(ADAPTER);
            if (a == null) continue;
            adapter = new BluetoothStatus.Adapter(entry.getKey().getPath(), string(a, "Address"),
                    string(a, "Alias").isEmpty() ? string(a, "Name") : string(a, "Alias"), bool(a, "Powered"),
                    bool(a, "Discoverable"), bool(a, "Discovering"));
            break;
        }
        List<BluetoothDevice> devices = new ArrayList<>();
        if (adapter != null) {
            String prefix = adapter.id() + "/";
            for (var entry : sorted) {
                Map<String, Variant<?>> d = entry.getValue().get(DEVICE);
                if (d == null || !entry.getKey().getPath().startsWith(prefix)) continue;
                Map<String, Variant<?>> battery = entry.getValue().get(BATTERY);
                int percent = battery == null ? -1 : number(battery, "Percentage", -1);
                String name = string(d, "Alias").isEmpty() ? string(d, "Name") : string(d, "Alias");
                // BlueZ sets the alias to the address with dashes when a device has no name.
                if (name.equals(string(d, "Address").replace(':', '-'))) name = "";
                devices.add(new BluetoothDevice(entry.getKey().getPath(), string(d, "Address"), name,
                        BluetoothDevice.Kind.ofIcon(string(d, "Icon")), bool(d, "Paired") || bool(d, "Bonded"),
                        bool(d, "Connected"), percent, number(d, "RSSI", 0)));
            }
        }
        return new BluetoothStatus(true, adapter, devices);
    }

    private static String string(Map<String, Variant<?>> props, String name) {
        Variant<?> v = props.get(name);
        return v != null && v.getValue() != null ? v.getValue().toString() : "";
    }

    private static boolean bool(Map<String, Variant<?>> props, String name) {
        Variant<?> v = props.get(name);
        return v != null && Boolean.TRUE.equals(v.getValue());
    }

    private static int number(Map<String, Variant<?>> props, String name, int fallback) {
        Variant<?> v = props.get(name);
        return v != null && v.getValue() instanceof Number n ? n.intValue() : fallback;
    }
}
