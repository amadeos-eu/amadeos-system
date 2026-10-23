package eu.amadeos.system.keyring;

import java.io.IOException;

/**
 * The user's login keyring, where gnome-keyring keeps the passwords of Mail and other programs: whether it is
 * there and unlocked, how many passwords it holds, and locking, unlocking and changing its password. The
 * Settings use it for their Passwords page; {@link SecretServiceKeyring} talks to the real one.
 */
public interface Keyring {
    /**
     * @param available whether a keyring service runs and has a default keyring
     * @param locked    whether the keyring is locked (programs then cannot read their passwords)
     * @param items     the number of stored passwords and keys
     */
    record Status(boolean available, boolean locked, int items) {
        public static final Status NONE = new Status(false, true, 0);
    }

    Status status();

    void lock() throws IOException;

    /** Unlocks the keyring; fails with a wrong password. */
    void unlock(char[] password) throws IOException;

    /** Gives the keyring a new password; fails if {@code current} is wrong. */
    void changePassword(char[] current, char[] newPassword) throws IOException;
}
