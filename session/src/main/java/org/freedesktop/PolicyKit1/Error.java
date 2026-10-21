package org.freedesktop.PolicyKit1;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

/**
 * The errors an authentication agent returns to polkitd. dbus-java names a D-Bus error after the exception's
 * class, so these must be {@code org.freedesktop.PolicyKit1.Error.Cancelled} and {@code ...Failed}: polkitd
 * tells pkexec "dismissed" (exit 126) for Cancelled and "not authorized" (127) for anything else.
 */
public final class Error {
    private Error() {
    }

    /** The user closed the dialog, or polkitd cancelled the request. */
    public static final class Cancelled extends DBusExecutionException {
        private static final long serialVersionUID = 1L;

        public Cancelled(String message) {
            super(message);
        }
    }

    /** The request could not be handled at all (no user to authenticate as, helper missing). */
    public static final class Failed extends DBusExecutionException {
        private static final long serialVersionUID = 1L;

        public Failed(String message) {
            super(message);
        }
    }
}
