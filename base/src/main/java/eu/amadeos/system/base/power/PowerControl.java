package eu.amadeos.system.base.power;

import java.util.function.Consumer;

/**
 * What the desktop asks the system about sleeping and switching off, and tells it to do. On amadeOS this is
 * systemd-logind ({@link LogindPowerControl}); tests use stand-ins.
 */
public interface PowerControl {

    /** Whether the computer can suspend to memory, without a password or with one. */
    boolean canSuspend();

    /**
     * Whether the computer can hibernate: needs a swap space at least as large as the memory in use and a kernel
     * that may write it (not with Secure Boot's lockdown).
     */
    boolean canHibernate();

    void suspend() throws PowerException;

    void hibernate() throws PowerException;

    void powerOff() throws PowerException;

    /** Whether the lid is closed now; false on computers without one. */
    boolean lidClosed();

    /** Whether the computer is docked or has an external screen connected. */
    boolean docked();

    /**
     * Calls the listener with true just before the computer sleeps and with false after it woke up. Closing the
     * returned handle stops that.
     */
    AutoCloseable onSleep(Consumer<Boolean> listener) throws PowerException;

    /** Thrown when the system refused or could not carry out a power action. */
    final class PowerException extends Exception {
        public PowerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
