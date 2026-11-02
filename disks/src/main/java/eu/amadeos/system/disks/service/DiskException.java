package eu.amadeos.system.disks.service;

/** A step on a drive failed; {@link #reason()} says why in a way the window can turn into a text. */
public class DiskException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Why it failed. */
    public enum Reason {
        /** The user closed the password dialog: no message needed. */
        CANCELLED,
        /** The password was wrong or the user may not do this. */
        NOT_ALLOWED,
        /** A program still uses the file system. */
        BUSY,
        /** udisks or the tool behind it is missing. */
        UNAVAILABLE,
        /** The tool (mkfs, fsck, ...) failed; the message has its words. */
        FAILED
    }

    private final Reason reason;

    public DiskException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DiskException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
