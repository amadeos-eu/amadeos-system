package eu.amadeos.system.base.net;

import java.util.List;

/**
 * Network connections as the desktop sees them. Calls block (some for seconds), so callers run them off
 * the event dispatch thread.
 */
public interface NetworkService {

    NetworkStatus status();

    /** Networks in range; {@code rescan} asks the device to scan first instead of using its last results. */
    List<WifiNetwork> wifiNetworks(boolean rescan);

    /** @param password for a secured network that has no saved connection yet, otherwise ignored */
    void connect(WifiNetwork network, String password) throws NetworkException;

    void disconnectWifi() throws NetworkException;

    /** Deletes the saved connection, including its password. */
    void forget(WifiNetwork network) throws NetworkException;

    void setWifiEnabled(boolean enabled) throws NetworkException;

    /** The Ethernet and WLAN devices NetworkManager manages, in its order. */
    default List<NetworkStatus.Device> devices() {
        return List.of();
    }

    /** Addresses and hardware details of a device, e.g. for the Settings' "Details". */
    default DeviceDetails details(String device) {
        return DeviceDetails.NONE;
    }

    /** Connects a device with its best saved connection, e.g. an Ethernet port after "Disconnect". */
    default void connectDevice(String device) throws NetworkException {
        throw new NetworkException("not supported");
    }

    /** Disconnects a device until it is connected again by hand; NetworkManager does not reconnect it. */
    default void disconnectDevice(String device) throws NetworkException {
        throw new NetworkException("not supported");
    }

    /** Joins a network that does not announce its name; an empty password for an open network. */
    default void connectHidden(String ssid, String password) throws NetworkException {
        throw new NetworkException("not supported");
    }

    /**
     * Calls {@code onChange} (on a background thread) whenever devices or connections change, until the
     * returned handle is closed.
     */
    AutoCloseable watch(Runnable onChange);

    /** A request NetworkManager refused, with its reason. */
    final class NetworkException extends Exception {
        public NetworkException(String message) {
            super(message);
        }
    }
}
