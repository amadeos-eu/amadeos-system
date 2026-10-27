package org.bluez;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

/**
 * The errors a pairing agent returns to bluetoothd. dbus-java names a D-Bus error after the exception's class,
 * so these must be {@code org.bluez.Error.Rejected} and {@code org.bluez.Error.Canceled}.
 */
public final class Error {
    private Error() {
    }

    /** The user said the numbers do not match, or the agent cannot answer this request. */
    public static final class Rejected extends DBusExecutionException {
        private static final long serialVersionUID = 1L;

        public Rejected(String message) {
            super(message);
        }
    }

    /** The user closed the question. */
    public static final class Canceled extends DBusExecutionException {
        private static final long serialVersionUID = 1L;

        public Canceled(String message) {
            super(message);
        }
    }
}
