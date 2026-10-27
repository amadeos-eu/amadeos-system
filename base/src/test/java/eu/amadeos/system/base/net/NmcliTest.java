package eu.amadeos.system.base.net;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NmcliTest {

    @Test
    void splitsTerseFieldsAndUnescapesColonsAndBackslashes() {
        assertEquals(List.of("*", "Café:5G", "72", "WPA2"), Nmcli.fields("*:Café\\:5G:72:WPA2"));
        assertEquals(List.of("a\\b", ""), Nmcli.fields("a\\\\b:"));
    }

    @Test
    void ethernetWinsOverWifi() {
        String devices = """
                enx207bd51a1b75:ethernet:connected:Wired connection 1
                wlp2s0:wifi:connected:FRITZ!Box 7530 WX
                lo:loopback:connected (externally):lo
                p2p-dev-wlp2s0:wifi-p2p:disconnected:
                """;
        NetworkStatus s = NetworkStatus.of(Nmcli.parseDevices(devices), true, 64);
        assertEquals(NetworkStatus.State.CONNECTED, s.state());
        assertEquals(NetworkStatus.Kind.ETHERNET, s.kind());
        assertEquals("Wired connection 1", s.name());
        assertEquals("wlp2s0", s.wifiDevice());
    }

    @Test
    void wifiOnlyCarriesTheSignal() {
        String devices = """
                enp3s0:ethernet:unavailable:
                wlp2s0:wifi:connected:Home
                lo:loopback:connected (externally):lo
                """;
        NetworkStatus s = NetworkStatus.of(Nmcli.parseDevices(devices), true, 64);
        assertEquals(NetworkStatus.Kind.WIFI, s.kind());
        assertEquals("Home", s.name());
        assertEquals(64, s.signal());
    }

    @Test
    void connectingAndDisconnected() {
        NetworkStatus connecting = NetworkStatus.of(Nmcli.parseDevices(
                "wlp2s0:wifi:connecting (getting IP configuration):Home\n"), true, 0);
        assertEquals(NetworkStatus.State.CONNECTING, connecting.state());

        NetworkStatus none = NetworkStatus.of(Nmcli.parseDevices(
                "enp3s0:ethernet:unavailable:\nlo:loopback:connected (externally):lo\n"), false, 0);
        assertEquals(NetworkStatus.State.DISCONNECTED, none.state());
        assertFalse(none.hasWifi());
        assertNull(none.wifiDevice());
    }

    @Test
    void wifiListIsDedupedSortedAndMarksKnownNetworks() {
        String list = """
                 :Neighbour:40:WPA2
                 :Cafe:55:
                *:Home:61:WPA2 WPA3
                 :Home:80:WPA2 WPA3
                 ::90:WPA2
                 :Neighbour:47:WPA2
                """;
        List<WifiNetwork> networks = Nmcli.parseWifiList(list, Set.of("Home"));
        assertEquals(List.of("Home", "Cafe", "Neighbour"), networks.stream().map(WifiNetwork::ssid).toList());
        WifiNetwork home = networks.get(0);
        assertTrue(home.active());
        assertTrue(home.known());
        assertEquals(80, home.signal());
        assertFalse(networks.get(1).secured(), "empty SECURITY is an open network");
        assertEquals(47, networks.get(2).signal());
    }

    @Test
    void savedWifiConnectionsOnly() {
        String connections = """
                Wired connection 1:802-3-ethernet
                Home:802-11-wireless
                lo:loopback
                """;
        assertEquals(Set.of("Home"), Nmcli.parseWifiConnections(connections));
    }

    @Test
    void connectCommands() {
        WifiNetwork known = new WifiNetwork("Home", 80, true, false, true);
        assertEquals(List.of("nmcli", "--wait", "45", "connection", "up", "id", "Home"),
                Nmcli.connect(known, "ignored", "wlp2s0"));

        WifiNetwork fresh = new WifiNetwork("Cafe", 50, true, false, false);
        assertEquals(List.of("nmcli", "--wait", "45", "device", "wifi", "connect", "Cafe", "password", "secret",
                "ifname", "wlp2s0"), Nmcli.connect(fresh, "secret", "wlp2s0"));

        WifiNetwork open = new WifiNetwork("Library", 50, false, false, false);
        assertEquals(List.of("nmcli", "--wait", "45", "device", "wifi", "connect", "Library"),
                Nmcli.connect(open, null, null));
    }

    @Test
    void errorMessageDropsThePrefix() {
        assertEquals("Connection activation failed: Secrets were required, but not provided.",
                Nmcli.errorMessage("Error: Connection activation failed: Secrets were required, but not provided.\n"));
        assertEquals("NetworkManager is not running.", Nmcli.errorMessage("Error: NetworkManager is not running.\n"));
    }

    @Test
    void signalBars() {
        assertEquals(0, WifiNetwork.bars(0));
        assertEquals(1, WifiNetwork.bars(10));
        assertEquals(2, WifiNetwork.bars(40));
        assertEquals(3, WifiNetwork.bars(74));
        assertEquals(4, WifiNetwork.bars(75));
    }

    @Test
    void deviceDetailsWithAddressesAndWithoutLinkLocal() {
        String show = """
                GENERAL.HWADDR:3C\\:52\\:82\\:1A\\:0B\\:7E
                CAPABILITIES.SPEED:1000 Mb/s
                IP4.ADDRESS[1]:192.168.1.23/24
                IP4.GATEWAY:192.168.1.1
                IP4.DNS[1]:192.168.1.1
                IP4.DNS[2]:9.9.9.9
                IP6.ADDRESS[1]:2a02\\:810d\\:4b3f\\:a100\\:\\:23/128
                IP6.ADDRESS[2]:fe80\\:\\:3e52\\:82ff\\:fe1a\\:b7e/64
                """;
        DeviceDetails d = Nmcli.parseDetails(show);
        assertEquals("3C:52:82:1A:0B:7E", d.hardwareAddress());
        assertEquals("1000 Mb/s", d.speed());
        assertEquals(List.of("192.168.1.23/24"), d.ipv4());
        assertEquals("192.168.1.23", d.address());
        assertEquals("192.168.1.1", d.gateway());
        assertEquals(List.of("192.168.1.1", "9.9.9.9"), d.dns());
        assertEquals(List.of("2a02:810d:4b3f:a100::23/128"), d.ipv6());
    }

    @Test
    void detailsOfAnUnconnectedDeviceAreEmpty() {
        DeviceDetails d = Nmcli.parseDetails("GENERAL.HWADDR:A4\\:C3\\:F0\\:85\\:3D\\:11\nCAPABILITIES.SPEED:unknown\nIP4.GATEWAY:--\n");
        assertEquals("A4:C3:F0:85:3D:11", d.hardwareAddress());
        assertEquals("", d.speed());
        assertEquals("", d.gateway());
        assertEquals("", d.address());
    }

    @Test
    void hiddenNetworkCommand() {
        assertEquals(List.of("nmcli", "--wait", "45", "device", "wifi", "connect", "Attic", "hidden", "yes",
                "password", "secret", "ifname", "wlp2s0"), Nmcli.connectHidden("Attic", "secret", "wlp2s0"));
    }
}
