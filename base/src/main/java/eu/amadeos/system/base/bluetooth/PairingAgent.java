package eu.amadeos.system.base.bluetooth;

/**
 * Asks the user what pairing needs, the way BlueZ's agent interface asks. The methods are called on a
 * background thread and may block until the user has answered.
 */
public interface PairingAgent {

    /** "Does the device show 123456?": both sides show the same number; true when the user confirms it. */
    boolean confirm(BluetoothDevice device, String passkey);

    /** A keyboard: the user types the number shown here on the device and presses Enter. */
    void showPasskey(BluetoothDevice device, String passkey);

    /** An older device asks for its PIN (often 0000 or 1234); null when the user cancels. */
    String requestPin(BluetoothDevice device);

    /** Pairing ended, by success, failure or the device; any open question disappears. */
    void done();
}
