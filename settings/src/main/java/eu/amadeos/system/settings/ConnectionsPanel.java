package eu.amadeos.system.settings;

import eu.amadeos.system.base.net.DeviceDetails;
import eu.amadeos.system.base.net.NetworkService;
import eu.amadeos.system.base.net.NetworkStatus;
import eu.amadeos.system.base.net.WifiNetwork;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Connections tab of the Network page: each Ethernet port with its state and address, and the WLAN with the
 * networks in range, through NetworkManager ({@link NetworkService}, the same backend as the Front Panel's network
 * cell). Connecting, disconnecting and forgetting act at once.
 */
final class ConnectionsPanel extends JPanel {
    private final NetworkService network;
    private final Consumer<String> status;

    private final JPanel ethernet = new JPanel();
    private final JPanel wifiSection;
    private final JCheckBox wifiOn = new JCheckBox(SettingsTexts.get("network.wifi.on"));
    private final DefaultListModel<WifiNetwork> networks = new DefaultListModel<>();
    private final JList<WifiNetwork> networkList = new JList<>(networks);
    private final JButton connect = new JButton(SettingsTexts.get("network.wifi.connect"));
    private final JButton disconnect = new JButton(SettingsTexts.get("network.wifi.disconnect"));
    private final JButton forget = new JButton(SettingsTexts.get("network.wifi.forget"));
    private final JButton hidden = new JButton(SettingsTexts.get("network.wifi.hidden"));
    private final JButton rescan = new JButton(SettingsTexts.get("network.wifi.rescan"));
    private final JButton wifiDetails = new JButton(SettingsTexts.get("network.details"));
    private final JLabel wifiState = new JLabel(" ");
    private final JLabel unavailable = new JLabel(SettingsTexts.get("network.unavailable"));

    private NetworkStatus current = NetworkStatus.UNAVAILABLE;
    private List<NetworkStatus.Device> devices = List.of();
    private boolean updating;
    private boolean busy;
    private AutoCloseable watch;
    private final Timer refreshLater = new Timer(400, e -> refresh(false));

    ConnectionsPanel(NetworkService network, Consumer<String> status) {
        super(new BorderLayout());
        this.network = network;
        this.status = status;
        refreshLater.setRepeats(false);

        ethernet.setLayout(new javax.swing.BoxLayout(ethernet, javax.swing.BoxLayout.Y_AXIS));
        networkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        networkList.setVisibleRowCount(6);
        networkList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                WifiNetwork n = (WifiNetwork) value;
                super.getListCellRendererComponent(l, describe(n), index, selected, focus);
                setIcon(DeviceIcons.wifi(n.bars(), n.secured()));
                setIconTextGap(8);
                return this;
            }
        });
        networkList.addListSelectionListener(e -> enableButtons());
        networkList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && connect.isEnabled()) connectSelected();
            }
        });
        wifiOn.addActionListener(e -> {
            if (!updating) act(() -> network.setWifiEnabled(wifiOn.isSelected()), null);
        });
        connect.addActionListener(e -> connectSelected());
        disconnect.addActionListener(e -> act(network::disconnectWifi, SettingsTexts.get("network.status.disconnected")));
        forget.addActionListener(e -> forgetSelected());
        hidden.addActionListener(e -> connectHidden());
        rescan.addActionListener(e -> refresh(true));
        wifiDetails.addActionListener(e -> {
            if (current.wifiDevice() != null) showDetails(current.wifiDevice());
        });

        JScrollPane scroll = new JScrollPane(networkList);
        scroll.setPreferredSize(new Dimension(440, 140));
        wifiSection = SettingsPage.section(SettingsTexts.get("network.wifi.title"), SettingsPage.line(wifiOn, wifiState),
                SettingsPage.line(scroll), SettingsPage.line(connect, disconnect, forget),
                SettingsPage.line(hidden, rescan, wifiDetails));
        add(SettingsPage.stack(SettingsPage.line(unavailable), ethernet, wifiSection,
                SettingsPage.note(SettingsTexts.get("network.connections.hint"))), BorderLayout.NORTH);
        unavailable.setVisible(false);
        refresh(false);
    }

    /** "Home-WLAN - connected", "Café - saved", or just the name. */
    static String describe(WifiNetwork n) {
        if (n.active()) return SettingsTexts.format("network.wifi.entry", n.ssid(), SettingsTexts.get("network.wifi.active"));
        if (n.known()) return SettingsTexts.format("network.wifi.entry", n.ssid(), SettingsTexts.get("network.wifi.known"));
        return n.ssid();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (watch == null) watch = network.watch(() -> SwingUtilities.invokeLater(refreshLater::restart));
    }

    @Override
    public void removeNotify() {
        if (watch != null) {
            try {
                watch.close();
            } catch (Exception ignored) {
                // nothing left to do
            }
            watch = null;
        }
        super.removeNotify();
    }

    private record Snapshot(NetworkStatus status, List<NetworkStatus.Device> devices, List<DeviceDetails> details,
                            List<WifiNetwork> networks) {
    }

    void refresh(boolean rescanWifi) {
        if (rescanWifi) wifiState.setText(SettingsTexts.get("network.wifi.scanning"));
        new SwingWorker<Snapshot, Void>() {
            @Override
            protected Snapshot doInBackground() {
                NetworkStatus s = network.status();
                List<NetworkStatus.Device> d = network.devices();
                List<DeviceDetails> details = new ArrayList<>();
                for (NetworkStatus.Device device : d) {
                    details.add(device.type().equals("ethernet") && device.connected()
                            ? network.details(device.name()) : DeviceDetails.NONE);
                }
                List<WifiNetwork> n = s.hasWifi() && s.wifiEnabled() ? network.wifiNetworks(rescanWifi) : List.of();
                return new Snapshot(s, d, details, n);
            }

            @Override
            protected void done() {
                try {
                    Snapshot snap = get();
                    show(snap.status(), snap.devices(), snap.details(), snap.networks());
                } catch (Exception e) {
                    show(NetworkStatus.UNAVAILABLE, List.of(), List.of(), List.of());
                }
            }
        }.execute();
    }

    void show(NetworkStatus s, List<NetworkStatus.Device> deviceList, List<DeviceDetails> details,
              List<WifiNetwork> found) {
        current = s;
        devices = deviceList;
        updating = true;
        try {
            unavailable.setVisible(s.state() == NetworkStatus.State.UNAVAILABLE);
            ethernet.removeAll();
            for (int i = 0; i < deviceList.size(); i++) {
                NetworkStatus.Device d = deviceList.get(i);
                if (!d.type().equals("ethernet")) continue;
                ethernet.add(ethernetSection(d, i < details.size() ? details.get(i) : DeviceDetails.NONE));
                ethernet.add(javax.swing.Box.createVerticalStrut(10));
            }
            ethernet.setVisible(ethernet.getComponentCount() > 0);
            ethernet.revalidate();
            wifiSection.setVisible(s.hasWifi());
            wifiOn.setSelected(s.wifiEnabled());
            wifiState.setText(wifiText(s));
            String selected = networkList.getSelectedValue() == null ? null : networkList.getSelectedValue().ssid();
            networks.clear();
            found.forEach(networks::addElement);
            for (int i = 0; i < found.size(); i++) {
                if (found.get(i).ssid().equals(selected)) networkList.setSelectedIndex(i);
            }
        } finally {
            updating = false;
        }
        enableButtons();
    }

    private static String wifiText(NetworkStatus s) {
        if (!s.wifiEnabled()) return SettingsTexts.get("network.wifi.off");
        if (s.kind() == NetworkStatus.Kind.WIFI && s.state() == NetworkStatus.State.CONNECTED) {
            return SettingsTexts.format("network.wifi.connected-to", s.name());
        }
        if (s.kind() == NetworkStatus.Kind.WIFI && s.state() == NetworkStatus.State.CONNECTING) {
            return SettingsTexts.format("network.wifi.connecting-to", s.name());
        }
        return " ";
    }

    /** One Ethernet port: "Connected - 192.168.1.23, 1000 Mb/s" with Details and Disconnect, or "No cable". */
    private JComponent ethernetSection(NetworkStatus.Device d, DeviceDetails details) {
        String text;
        if (d.connected()) {
            List<String> parts = new ArrayList<>();
            if (!details.address().isEmpty()) parts.add(details.address());
            if (!details.speed().isEmpty()) parts.add(details.speed());
            text = parts.isEmpty() ? SettingsTexts.get("network.ethernet.connected")
                    : SettingsTexts.format("network.ethernet.connected.with", String.join(", ", parts));
        } else if (d.connecting()) {
            text = SettingsTexts.get("network.ethernet.connecting");
        } else if (d.state().startsWith("unavailable")) {
            text = SettingsTexts.get("network.ethernet.no-cable");
        } else {
            text = SettingsTexts.get("network.ethernet.disconnected");
        }
        JLabel state = new JLabel(text, DeviceIcons.ethernet(), JLabel.LEFT);
        state.setIconTextGap(8);
        JButton toggle = new JButton(SettingsTexts.get(d.connected() || d.connecting()
                ? "network.ethernet.disconnect" : "network.ethernet.connect"));
        toggle.setEnabled(!d.state().startsWith("unavailable") && !busy);
        toggle.addActionListener(e -> {
            if (d.connected() || d.connecting()) {
                act(() -> network.disconnectDevice(d.name()), SettingsTexts.get("network.status.disconnected"));
            } else {
                act(() -> network.connectDevice(d.name()), SettingsTexts.format("network.status.connected", d.name()));
            }
        });
        JButton more = new JButton(SettingsTexts.get("network.details"));
        more.setEnabled(d.connected());
        more.addActionListener(e -> showDetails(d.name()));
        return SettingsPage.section(SettingsTexts.format("network.ethernet.title", d.name()),
                SettingsPage.line(state), SettingsPage.line(toggle, more));
    }

    private void enableButtons() {
        WifiNetwork n = networkList.getSelectedValue();
        boolean on = current.wifiEnabled() && !busy;
        connect.setEnabled(on && n != null && !n.active());
        disconnect.setEnabled(on && current.kind() == NetworkStatus.Kind.WIFI
                && current.state() != NetworkStatus.State.DISCONNECTED);
        forget.setEnabled(!busy && n != null && n.known());
        hidden.setEnabled(on);
        rescan.setEnabled(on);
        networkList.setEnabled(current.wifiEnabled());
        wifiDetails.setEnabled(current.kind() == NetworkStatus.Kind.WIFI
                && current.state() == NetworkStatus.State.CONNECTED);
    }

    private void connectSelected() {
        WifiNetwork n = networkList.getSelectedValue();
        if (n == null) return;
        String password = "";
        if (n.secured() && !n.known()) {
            JPasswordField field = new JPasswordField(20);
            int answer = JOptionPane.showConfirmDialog(this, new Object[]{
                            SettingsTexts.format("network.wifi.password", n.ssid()), field},
                    SettingsTexts.get("network.wifi.connect.title"), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            password = new String(field.getPassword());
        }
        String pw = password;
        status.accept(SettingsTexts.format("network.status.connecting", n.ssid()));
        act(() -> network.connect(n, pw), SettingsTexts.format("network.status.connected", n.ssid()));
    }

    private void forgetSelected() {
        WifiNetwork n = networkList.getSelectedValue();
        if (n == null) return;
        int answer = JOptionPane.showConfirmDialog(this, SettingsTexts.format("network.wifi.forget.question", n.ssid()),
                SettingsTexts.get("network.wifi.forget.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            act(() -> network.forget(n), SettingsTexts.format("network.status.forgotten", n.ssid()));
        }
    }

    private void connectHidden() {
        JTextField ssid = new JTextField(20);
        JPasswordField password = new JPasswordField(20);
        int answer = JOptionPane.showConfirmDialog(this, new Object[]{
                        SettingsTexts.get("network.wifi.hidden.name"), ssid,
                        SettingsTexts.get("network.wifi.hidden.password"), password},
                SettingsTexts.get("network.wifi.hidden.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION || ssid.getText().isBlank()) return;
        String name = ssid.getText().strip();
        String pw = new String(password.getPassword());
        status.accept(SettingsTexts.format("network.status.connecting", name));
        act(() -> network.connectHidden(name, pw), SettingsTexts.format("network.status.connected", name));
    }

    /** A small window with the addresses of a device: IPv4, gateway, name servers, IPv6, hardware address. */
    private void showDetails(String device) {
        new SwingWorker<DeviceDetails, Void>() {
            @Override
            protected DeviceDetails doInBackground() {
                return network.details(device);
            }

            @Override
            protected void done() {
                DeviceDetails d;
                try {
                    d = get();
                } catch (Exception e) {
                    d = DeviceDetails.NONE;
                }
                JOptionPane.showMessageDialog(ConnectionsPanel.this, detailsPanel(d),
                        SettingsTexts.format("network.details.title", device), JOptionPane.PLAIN_MESSAGE);
            }
        }.execute();
    }

    static JPanel detailsPanel(DeviceDetails d) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 12);
        c.anchor = GridBagConstraints.NORTHWEST;
        String none = SettingsTexts.get("network.details.none");
        String[][] rows = {
                {"network.details.ipv4", d.ipv4().isEmpty() ? none : String.join("\n", d.ipv4())},
                {"network.details.gateway", d.gateway().isEmpty() ? none : d.gateway()},
                {"network.details.dns", d.dns().isEmpty() ? none : String.join("\n", d.dns())},
                {"network.details.ipv6", d.ipv6().isEmpty() ? none : String.join("\n", d.ipv6())},
                {"network.details.hardware", d.hardwareAddress().isEmpty() ? none : d.hardwareAddress()},
                {"network.details.speed", d.speed().isEmpty() ? none : d.speed()}};
        for (int i = 0; i < rows.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            p.add(new JLabel(SettingsTexts.get(rows[i][0])), c);
            c.gridx = 1;
            p.add(new JLabel("<html>" + rows[i][1].replace("\n", "<br>") + "</html>"), c);
        }
        return p;
    }

    private interface NetworkCall {
        void run() throws NetworkService.NetworkException;
    }

    private void act(NetworkCall call, String success) {
        busy = true;
        enableButtons();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    call.run();
                    return null;
                } catch (NetworkService.NetworkException e) {
                    return e.getMessage();
                }
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String error = get();
                    if (error != null) status.accept(SettingsTexts.format("status.error", error));
                    else if (success != null) status.accept(success);
                } catch (Exception e) {
                    status.accept(SettingsTexts.format("status.error", e.getMessage()));
                }
                refresh(false);
            }
        }.execute();
    }

    JList<WifiNetwork> networkList() {
        return networkList;
    }

    List<NetworkStatus.Device> devices() {
        return devices;
    }
}
