package eu.amadeos.system.clipboard;

/** The two X selections the clipboard manager watches. */
public enum Selection {
    /** Ctrl+C and Ctrl+V. */
    CLIPBOARD,
    /** The selected text, pasted with the middle mouse button. */
    PRIMARY;

    /** The name of the selection's atom. */
    public String atom() {
        return name();
    }
}
