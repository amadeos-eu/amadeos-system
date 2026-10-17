package org.datazuul.amadeos.base.net;

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
