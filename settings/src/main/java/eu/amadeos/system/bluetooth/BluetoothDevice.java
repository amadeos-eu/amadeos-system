package eu.amadeos.system.bluetooth;

import java.util.Locale;

/**
 * A Bluetooth device BlueZ knows: paired ones and, while the adapter searches, those in range.
 *
 * @param id        BlueZ's object path, e.g. {@code /org/bluez/hci0/dev_00_1A_7D_DA_71_13}
 * @param address   hardware address, e.g. {@code 00:1A:7D:DA:71:13}
 * @param name      the device's name, or its address when it has none
 * @param kind      what it is, from BlueZ's icon name
 * @param paired    whether it is paired (bonded) with this computer
 * @param connected whether it is connected now
 * @param battery   charge in percent the device reports, -1 if it reports none
 * @param signal    signal strength (RSSI in dBm) while searching, 0 if unknown
 */
public record BluetoothDevice(String id, String address, String name, Kind kind, boolean paired, boolean connected,
                              int battery, int signal) {

    public BluetoothDevice {
        if (name == null || name.isBlank()) name = address;
    }

    /** What a device is, after the freedesktop icon names BlueZ gives ({@code audio-headphones}, ...). */
    public enum Kind {
        HEADPHONES, SPEAKER, KEYBOARD, MOUSE, PHONE, COMPUTER, GAMEPAD, PRINTER, OTHER;

        public static Kind ofIcon(String icon) {
            if (icon == null) return OTHER;
            return switch (icon.toLowerCase(Locale.ROOT)) {
                case "audio-headphones", "audio-headset" -> HEADPHONES;
                case "audio-card", "audio-speakers", "multimedia-player" -> SPEAKER;
                case "input-keyboard" -> KEYBOARD;
                case "input-mouse", "input-tablet" -> MOUSE;
                case "phone" -> PHONE;
                case "computer" -> COMPUTER;
                case "input-gaming" -> GAMEPAD;
                case "printer" -> PRINTER;
                default -> OTHER;
            };
        }
    }
}
