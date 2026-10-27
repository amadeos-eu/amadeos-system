package eu.amadeos.system.settings;

import eu.amadeos.system.base.net.DeviceDetails;
import eu.amadeos.system.base.net.NetworkService;
import eu.amadeos.system.base.net.NetworkStatus;
import eu.amadeos.system.base.net.WifiNetwork;
import eu.amadeos.system.base.sound.AudioDevice;
import eu.amadeos.system.base.sound.SoundCard;
import eu.amadeos.system.base.sound.SoundService;
import eu.amadeos.system.base.sound.SoundStatus;
import eu.amadeos.system.base.bluetooth.BluetoothDevice;
import eu.amadeos.system.base.bluetooth.BluetoothService;
import eu.amadeos.system.base.bluetooth.BluetoothStatus;
import eu.amadeos.system.base.bluetooth.PairingAgent;
import eu.amadeos.system.print.cups.Printer;
import eu.amadeos.system.printers.FoundPrinter;
import eu.amadeos.system.printers.PrinterOptions;
import eu.amadeos.system.printers.PrinterService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Stand-ins for NetworkManager, the sound server, BlueZ and CUPS with the devices of a typical laptop, for the
 * tests and the handbook pictures. They remember what was asked of them.
 */
final class FakeDevices {
    private FakeDevices() {
    }

    static final class Network implements NetworkService {
        final List<String> calls = new ArrayList<>();
        boolean wifiOn = true;
        String active = "Home";

        @Override
        public NetworkStatus status() {
            return new NetworkStatus(NetworkStatus.State.CONNECTED, NetworkStatus.Kind.ETHERNET, "Wired connection 1",
                    0, "wlp2s0", wifiOn);
        }

        @Override
        public List<NetworkStatus.Device> devices() {
            return List.of(new NetworkStatus.Device("enp3s0", "ethernet", "connected", "Wired connection 1"),
                    new NetworkStatus.Device("wlp2s0", "wifi", active.isEmpty() ? "disconnected" : "connected", active));
        }

        @Override
        public List<WifiNetwork> wifiNetworks(boolean rescan) {
            return List.of(new WifiNetwork("Home", 82, true, active.equals("Home"), true),
                    new WifiNetwork("Cafe Central", 64, false, false, true),
                    new WifiNetwork("FRITZ!Box 7590 KL", 47, true, false, false),
                    new WifiNetwork("Library-Guest", 31, false, false, false),
                    new WifiNetwork("DIRECT-HP-LaserJet", 18, true, false, false));
        }

        @Override
        public DeviceDetails details(String device) {
            return device.equals("enp3s0")
                    ? new DeviceDetails("3C:52:82:1A:0B:7E", List.of("192.168.1.23/24"), "192.168.1.1",
                    List.of("192.168.1.1"), List.of("2a02:810d:4b3f:a100::23/128"), "1000 Mb/s")
                    : new DeviceDetails("A4:C3:F0:85:3D:11", List.of("192.168.1.57/24"), "192.168.1.1",
                    List.of("192.168.1.1"), List.of(), "");
        }

        @Override
        public void connect(WifiNetwork network, String password) {
            calls.add("connect " + network.ssid() + " " + password);
            active = network.ssid();
        }

        @Override
        public void disconnectWifi() {
            calls.add("disconnect wifi");
            active = "";
        }

        @Override
        public void forget(WifiNetwork network) {
            calls.add("forget " + network.ssid());
        }

        @Override
        public void setWifiEnabled(boolean enabled) {
            calls.add("wifi " + enabled);
            wifiOn = enabled;
        }

        @Override
        public void connectDevice(String device) {
            calls.add("connect " + device);
        }

        @Override
        public void disconnectDevice(String device) {
            calls.add("disconnect " + device);
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> { };
        }
    }

    static final class Sound implements SoundService {
        final List<String> calls = new ArrayList<>();
        String output = "alsa_output.pci-0000_00_1f.3.analog-stereo";

        @Override
        public SoundStatus status() {
            return new SoundStatus(true, List.of(
                    new AudioDevice("alsa_output.pci-0000_00_1f.3.analog-stereo", "Built-in Audio Analog Stereo", 65, false),
                    new AudioDevice("alsa_output.pci-0000_00_1f.3.hdmi-stereo", "Built-in Audio Digital Stereo (HDMI)", 100, false),
                    new AudioDevice("bluez_output.38_18_4C_12_9A_E0.1", "WH-1000XM4", 40, false)),
                    output,
                    List.of(new AudioDevice("alsa_input.pci-0000_00_1f.3.analog-stereo", "Built-in Audio Analog Stereo", 80, false),
                            new AudioDevice("alsa_input.usb-Logitech_C920.analog-stereo", "HD Pro Webcam C920 Analog Stereo", 100, false)),
                    "alsa_input.pci-0000_00_1f.3.analog-stereo");
        }

        @Override
        public List<SoundCard> cards() {
            return List.of(new SoundCard("alsa_card.pci-0000_00_1f.3", "Built-in Audio", List.of(
                            new SoundCard.Profile("output:analog-stereo+input:analog-stereo", "Analog Stereo Duplex", true),
                            new SoundCard.Profile("output:hdmi-stereo", "Digital Stereo (HDMI) Output", true),
                            new SoundCard.Profile("off", "Off", true)), "output:analog-stereo+input:analog-stereo"),
                    new SoundCard("bluez_card.38_18_4C_12_9A_E0", "WH-1000XM4", List.of(
                            new SoundCard.Profile("a2dp-sink", "High Fidelity Playback (A2DP Sink)", true),
                            new SoundCard.Profile("headset-head-unit", "Headset Head Unit (HSP/HFP)", true),
                            new SoundCard.Profile("off", "Off", true)), "a2dp-sink"));
        }

        @Override
        public void setVolume(int percent) {
            calls.add("volume " + percent);
        }

        @Override
        public void setMuted(boolean muted) {
            calls.add("muted " + muted);
        }

        @Override
        public void setOutput(AudioDevice device) {
            calls.add("output " + device.name());
            output = device.name();
        }

        @Override
        public void setInput(AudioDevice device) {
            calls.add("input " + device.name());
        }

        @Override
        public void setInputMuted(boolean muted) {
            calls.add("input muted " + muted);
        }

        @Override
        public void setOutputVolume(AudioDevice device, int percent) {
            calls.add("volume " + device.name() + " " + percent);
        }

        @Override
        public void setProfile(SoundCard card, String profile) {
            calls.add("profile " + card.name() + " " + profile);
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> { };
        }
    }

    static final class Check implements SoundCheck {
        final List<String> calls = new ArrayList<>();
        int level = 62;

        @Override
        public void playTest(AudioDevice output, Channel channel) {
            calls.add("test " + output.name() + " " + channel);
        }

        @Override
        public AutoCloseable meter(AudioDevice input, IntConsumer consumer) {
            consumer.accept(level);
            return () -> { };
        }
    }

    static final class Bluetooth implements BluetoothService {
        final List<String> calls = new ArrayList<>();
        boolean powered = true;
        boolean discovering = true;

        @Override
        public BluetoothStatus status() {
            String a = "/org/bluez/hci0";
            return new BluetoothStatus(true, new BluetoothStatus.Adapter(a, "F4:4E:FC:21:07:9A", "amadeos-laptop",
                    powered, false, discovering), List.of(
                    new BluetoothDevice(a + "/dev_38_18_4C_12_9A_E0", "38:18:4C:12:9A:E0", "WH-1000XM4",
                            BluetoothDevice.Kind.HEADPHONES, true, true, 80, 0),
                    new BluetoothDevice(a + "/dev_E8_3B_A1_02_44_10", "E8:3B:A1:02:44:10", "MX Master 3",
                            BluetoothDevice.Kind.MOUSE, true, true, 55, 0),
                    new BluetoothDevice(a + "/dev_C2_71_08_6B_1D_3E", "C2:71:08:6B:1D:3E", "Keyboard K380",
                            BluetoothDevice.Kind.KEYBOARD, true, false, -1, 0),
                    new BluetoothDevice(a + "/dev_5C_E9_1E_80_22_71", "5C:E9:1E:80:22:71", "Pixel 7",
                            BluetoothDevice.Kind.PHONE, false, false, -1, -52),
                    new BluetoothDevice(a + "/dev_00_1A_7D_DA_71_13", "00:1A:7D:DA:71:13", "JBL Flip 5",
                            BluetoothDevice.Kind.SPEAKER, false, false, -1, -60),
                    new BluetoothDevice(a + "/dev_7A_10_33_C5_8E_02", "7A:10:33:C5:8E:02", "",
                            BluetoothDevice.Kind.OTHER, false, false, -1, -88)));
        }

        @Override
        public void setPowered(boolean on) {
            calls.add("powered " + on);
            powered = on;
        }

        @Override
        public void setDiscoverable(boolean on) {
            calls.add("discoverable " + on);
        }

        @Override
        public void setSearching(boolean on) {
            calls.add("searching " + on);
            discovering = on;
        }

        @Override
        public void pair(BluetoothDevice device, PairingAgent agent) {
            calls.add("pair " + device.name());
        }

        @Override
        public void connect(BluetoothDevice device) {
            calls.add("connect " + device.name());
        }

        @Override
        public void disconnect(BluetoothDevice device) {
            calls.add("disconnect " + device.name());
        }

        @Override
        public void remove(BluetoothDevice device) {
            calls.add("remove " + device.name());
        }

        @Override
        public AutoCloseable watch(Runnable onChange) {
            return () -> { };
        }
    }

    static final class Printers implements PrinterService {
        final List<String> calls = new ArrayList<>();

        @Override
        public List<Printer> printers() {
            return List.of(
                    new Printer("Brother_HL-L2350DW", "Brother HL-L2350DW", "Office", "Brother HL-L2350DW series",
                            Printer.State.IDLE, "", List.of(), true, "ipp://localhost/printers/Brother_HL-L2350DW", 0, true),
                    new Printer("HP_OfficeJet_Pro_9010", "HP OfficeJet Pro 9010", "Living room",
                            "HP OfficeJet Pro 9010 series", Printer.State.PROCESSING, "", List.of(), true,
                            "ipp://localhost/printers/HP_OfficeJet_Pro_9010", 2, false));
        }

        @Override
        public List<FoundPrinter> discover() {
            return List.of(new FoundPrinter("dnssd://Canon%20TS5300%20series._ipp._tcp.local/", "Canon TS5300 series",
                            "Canon TS5300 series", "Kitchen"),
                    new FoundPrinter("dnssd://EPSON%20ET-2810%20Series._ipp._tcp.local/", "EPSON ET-2810 Series",
                            "EPSON ET-2810 Series", ""));
        }

        @Override
        public void add(String name, String uri, String info, String location) {
            calls.add("add " + name + " " + uri);
        }

        @Override
        public void remove(Printer printer) {
            calls.add("remove " + printer.name());
        }

        @Override
        public void makeDefault(Printer printer) {
            calls.add("default " + printer.name());
        }

        @Override
        public void printTestPage(Printer printer) {
            calls.add("test page " + printer.name());
        }

        @Override
        public PrinterOptions options(Printer printer) {
            return new PrinterOptions(List.of("A4", "A5", "Letter", "Legal", "Env10"), "A4",
                    List.of("None", "DuplexNoTumble", "DuplexTumble"), "DuplexNoTumble");
        }

        @Override
        public void setOptions(Printer printer, String pageSize, String duplex) {
            calls.add("options " + printer.name() + " " + pageSize + " " + duplex);
        }
    }
}
