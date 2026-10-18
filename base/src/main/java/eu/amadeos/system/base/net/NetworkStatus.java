package eu.amadeos.system.base.net;

import java.util.List;

/**
 * What the front panel shows about the network: the connection that carries traffic (Ethernet wins over
 * WLAN, like NetworkManager's default route), and whether there is a WLAN device to offer networks for.
 *
 * @param state     overall state
 * @param kind      what the active or attempted connection runs over
 * @param name      connection name (e.g. "Wired connection 1", or the SSID), empty when disconnected
 * @param signal    WLAN signal strength 0-100 of the active network, 0 otherwise
 * @param wifiDevice name of the first WLAN device (e.g. wlp2s0), or null when there is none
 * @param wifiEnabled whether the WLAN radio is switched on
 */
public record NetworkStatus(State state, Kind kind, String name, int signal, String wifiDevice, boolean wifiEnabled) {

    public enum State { CONNECTED, CONNECTING, DISCONNECTED, UNAVAILABLE }

    public enum Kind { ETHERNET, WIFI, NONE }

    /** NetworkManager is not installed or not running. */
    public static final NetworkStatus UNAVAILABLE =
            new NetworkStatus(State.UNAVAILABLE, Kind.NONE, "", 0, null, false);

    public boolean hasWifi() {
        return wifiDevice != null;
    }

    /** One line of {@code nmcli -t -f DEVICE,TYPE,STATE,CONNECTION device status}. */
    public record Device(String name, String type, String state, String connection) {
        boolean connected() {
            return state.startsWith("connected");
        }

        boolean connecting() {
            return state.startsWith("connecting");
        }
    }

    /**
     * Combines the device list, the WLAN radio state and the signal of the active WLAN network.
     */
    static NetworkStatus of(List<Device> devices, boolean wifiEnabled, int activeSignal) {
        String wifiDevice = devices.stream().filter(d -> d.type().equals("wifi")).map(Device::name)
                .findFirst().orElse(null);
        for (State wanted : List.of(State.CONNECTED, State.CONNECTING)) {
            for (Kind kind : List.of(Kind.ETHERNET, Kind.WIFI)) {
                String type = kind == Kind.ETHERNET ? "ethernet" : "wifi";
                for (Device d : devices) {
                    boolean matches = wanted == State.CONNECTED ? d.connected() : d.connecting();
                    if (d.type().equals(type) && matches) {
                        int signal = kind == Kind.WIFI && wanted == State.CONNECTED ? activeSignal : 0;
                        return new NetworkStatus(wanted, kind, d.connection(), signal, wifiDevice, wifiEnabled);
                    }
                }
            }
        }
        return new NetworkStatus(State.DISCONNECTED, Kind.NONE, "", 0, wifiDevice, wifiEnabled);
    }
}
