package eu.amadeos.system.users;

import java.util.List;

/**
 * The accounts and groups of the computer: reading needs no rights, every change goes through the root helper
 * {@code /usr/libexec/amadeos-users} and polkit, which asks for an administrator's password (kept for a few
 * minutes). Only the own password is changed without it, with the current password, through {@code passwd}.
 */
public interface UserService {

    /** The people's accounts (user ID 1000 to 59999), sorted by name. */
    List<Account> accounts() throws UserException;

    /** The permission groups that exist on this computer and the groups made on it, without personal groups. */
    List<UserGroup> groups() throws UserException;

    /** The login name of the user running the Settings. */
    String self();

    /**
     * Makes an account with a home folder; it is put into the usual desktop groups (sound, video, printers,
     * removable media, network, Bluetooth, scanner) and, as administrator, into {@link Account#ADMIN_GROUP}.
     */
    void add(String login, String fullName, boolean admin, char[] password) throws UserException;

    /** Removes an account; its home folder is deleted or stays (and is then owned by nobody). */
    void remove(String login, boolean deleteHome) throws UserException;

    void setFullName(String login, String fullName) throws UserException;

    void setAdmin(String login, boolean admin) throws UserException;

    /** Sets another account's password, without knowing the old one. */
    void setPassword(String login, char[] password) throws UserException;

    /** Changes the own password; PAM checks the current one and passes the new one to the login keyring. */
    void changeOwnPassword(char[] current, char[] password) throws UserException;

    void setDisabled(String login, boolean disabled) throws UserException;

    void join(String login, String group) throws UserException;

    void leave(String login, String group) throws UserException;

    void addGroup(String group) throws UserException;

    void removeGroup(String group) throws UserException;

    /** A change was not made; {@link #reason()} says why, the message is the helper's or the system's text. */
    final class UserException extends Exception {
        /** Why a change was not made. */
        public enum Reason {
            /** The password prompt was closed. */
            DISMISSED,
            /** polkit refused, e.g. a wrong password three times or no administrator. */
            NOT_AUTHORIZED,
            /** The helper refused, e.g. removing yourself or the last administrator; the message says what. */
            REFUSED,
            /** The current password was wrong or the new one was rejected (too short, too simple). */
            WRONG_PASSWORD,
            /** Anything else: the helper or a system command failed. */
            FAILED
        }

        private final Reason reason;
        private final String code;

        public UserException(Reason reason, String code, String message) {
            super(message);
            this.reason = reason;
            this.code = code == null ? "" : code;
        }

        public Reason reason() {
            return reason;
        }

        /** The helper's error ID ("self", "last-admin", "logged-in", "exists", …), empty when there is none. */
        public String code() {
            return code;
        }
    }
}
