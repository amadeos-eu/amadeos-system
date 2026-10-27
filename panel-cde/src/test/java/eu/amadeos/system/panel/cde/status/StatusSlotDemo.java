package eu.amadeos.system.panel.cde.status;

import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.bluetooth.BluetoothDevice;
import eu.amadeos.system.base.net.NetworkStatus;
import eu.amadeos.system.base.net.NetworkService;
import eu.amadeos.system.base.net.WifiNetwork;
import eu.amadeos.system.base.power.BatteryStatus;

/** The status slot of a laptop with five cells, for the picture of the second slot. */
final class StatusSlotDemo {
    private StatusSlotDemo() {
    }

    public static void main(String[] args) {
        Messages en = Messages.load(StatusSlotDemo.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);
        SwingUtilities.invokeLater(() -> {
            NetworkCell network = new NetworkCell(new NetworkService() {
                public NetworkStatus status() {
                    return new NetworkStatus(NetworkStatus.State.CONNECTED, NetworkStatus.Kind.WIFI, "Home", 82, "wlp2s0", true);
                }
                public List<WifiNetwork> wifiNetworks(boolean rescan) { return List.of(); }
                public void connect(WifiNetwork n, String p) { }
                public void disconnectWifi() { }
                public void forget(WifiNetwork n) { }
                public void setWifiEnabled(boolean e) { }
                public AutoCloseable watch(Runnable r) { return () -> { }; }
            }, () -> { }, en);
            network.show(new NetworkStatus(NetworkStatus.State.CONNECTED, NetworkStatus.Kind.WIFI, "Home", 82, "wlp2s0", true));
            SoundCell sound = new SoundCell(new SoundCellTest.FakeService(), en);
            sound.show(new SoundCellTest.FakeService().status());
            BatteryCell battery = new BatteryCell(Optional::empty, en);
            battery.show(Optional.of(new BatteryStatus(64, BatteryStatus.State.DISCHARGING, 140)));
            BluetoothCell bluetooth = new BluetoothCell(null, () -> { }, en);
            bluetooth.show(BluetoothCellTest.status(true, new BluetoothDevice("/d1", "a", "WH-1000XM4",
                    BluetoothDevice.Kind.HEADPHONES, true, true, 80, 0), new BluetoothDevice("/d2", "b", "MX Master 3",
                    BluetoothDevice.Kind.MOUSE, true, true, 55, 0)));
            UpdatesCell updates = new UpdatesCell(null, () -> { }, () -> { }, en, (m, w) -> { }, null);
            updates.show(List.of(new eu.amadeos.system.packages.Change(eu.amadeos.system.packages.Change.Kind.UPGRADE,
                    "openssl", "1.0", "1.1", List.of(new eu.amadeos.system.packages.Origin("Debian-Security", "13",
                    "stable-security")), "amd64")));
            List<StatusCell> cells = args.length > 0 && args[0].equals("four")
                    ? List.of(network, sound, battery, bluetooth) : List.of(network, sound, battery, bluetooth, updates);
            StatusDockWidget widget = new StatusDockWidget(cells);
            JFrame frame = new JFrame("Status slot");
            JPanel root = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            root.add(widget.component());
            frame.setContentPane(root);
            frame.setUndecorated(true);
            frame.pack();
            frame.setVisible(true);
        });
    }
}
