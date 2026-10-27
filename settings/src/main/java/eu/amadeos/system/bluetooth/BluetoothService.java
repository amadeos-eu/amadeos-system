package eu.amadeos.system.bluetooth;

/**
 * Bluetooth as the Settings use it. Calls block (pairing for up to a minute), so callers run them off the event
 * dispatch thread.
 */
public interface BluetoothService {

    BluetoothStatus status();

    void setPowered(boolean on) throws BluetoothException;

    /** Lets other devices find this computer for the next few minutes (BlueZ's DiscoverableTimeout). */
    void setDiscoverable(boolean on) throws BluetoothException;

    /** Starts or stops searching for devices in range; found devices appear in {@link #status()}. */
    void setSearching(boolean on) throws BluetoothException;

    /**
     * Pairs with a device, trusts it so that it may connect by itself later, and connects it. {@code agent} is
     * asked for confirmation or a PIN when the device needs one.
     */
    void pair(BluetoothDevice device, PairingAgent agent) throws BluetoothException;

    void connect(BluetoothDevice device) throws BluetoothException;

    void disconnect(BluetoothDevice device) throws BluetoothException;

    /** Removes the pairing; the device has to be paired again before it can connect. */
    void remove(BluetoothDevice device) throws BluetoothException;

    /** Calls {@code onChange} (on a background thread) when the adapter or a device changes, until closed. */
    AutoCloseable watch(Runnable onChange);

    /** A request BlueZ refused, with its reason. */
    final class BluetoothException extends Exception {
        public BluetoothException(String message) {
            super(message);
        }
    }
}
