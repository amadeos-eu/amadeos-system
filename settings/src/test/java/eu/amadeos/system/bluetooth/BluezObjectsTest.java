package eu.amadeos.system.bluetooth;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BluezObjectsTest {

    private static Map<String, Variant<?>> props(Object... pairs) {
        Map<String, Variant<?>> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], new Variant<>(pairs[i + 1]));
        return m;
    }

    @Test
    void adapterPairedAndNearbyDevices() {
        Map<DBusPath, Map<String, Map<String, Variant<?>>>> objects = new LinkedHashMap<>();
        objects.put(new DBusPath("/org/bluez"), Map.of("org.bluez.AgentManager1", Map.of()));
        objects.put(new DBusPath("/org/bluez/hci0"), Map.of(BluezObjects.ADAPTER, props("Address", "F4:4E:FC:21:07:9A",
                "Alias", "amadeos-laptop", "Powered", true, "Discoverable", false, "Discovering", true)));
        objects.put(new DBusPath("/org/bluez/hci0/dev_38_18_4C_12_9A_E0"), Map.of(
                BluezObjects.DEVICE, props("Address", "38:18:4C:12:9A:E0", "Alias", "WH-1000XM4",
                        "Icon", "audio-headset", "Paired", true, "Connected", true),
                BluezObjects.BATTERY, props("Percentage", (byte) 80)));
        objects.put(new DBusPath("/org/bluez/hci0/dev_7A_10_33_C5_8E_02"), Map.of(
                BluezObjects.DEVICE, props("Address", "7A:10:33:C5:8E:02", "Alias", "7A-10-33-C5-8E-02",
                        "Paired", false, "RSSI", (short) -88)));
        objects.put(new DBusPath("/org/bluez/hci0/dev_5C_E9_1E_80_22_71"), Map.of(
                BluezObjects.DEVICE, props("Address", "5C:E9:1E:80:22:71", "Alias", "Pixel 7", "Icon", "phone",
                        "Paired", false, "RSSI", (short) -52)));

        BluetoothStatus s = BluezObjects.status(objects);
        assertTrue(s.available());
        assertEquals("amadeos-laptop", s.adapter().name());
        assertTrue(s.adapter().powered());
        assertTrue(s.adapter().discovering());
        BluetoothDevice headset = s.paired().getFirst();
        assertEquals(BluetoothDevice.Kind.HEADPHONES, headset.kind());
        assertEquals(80, headset.battery());
        assertTrue(headset.connected());
        List<BluetoothDevice> nearby = s.nearby();
        assertEquals("Pixel 7", nearby.get(0).name(), "named devices first");
        assertEquals("7A:10:33:C5:8E:02", nearby.get(1).name(), "a device without a name shows its address");
        assertEquals(-1, nearby.get(0).battery());
    }

    @Test
    void noAdapter() {
        BluetoothStatus s = BluezObjects.status(Map.of(new DBusPath("/org/bluez"), Map.of()));
        assertTrue(s.available());
        assertNull(s.adapter());
        assertFalse(s.hasAdapter());
        assertEquals(List.of(), s.devices());
    }
}
