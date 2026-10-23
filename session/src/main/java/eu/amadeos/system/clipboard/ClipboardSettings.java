package eu.amadeos.system.clipboard;

/**
 * The "Clipboard" settings of the Settings.
 *
 * @param history  keep a history of what was copied (in memory only)
 * @param size     how many entries the history keeps
 * @param keep     take the clipboard over when the program that copied closes, so the copy can still be pasted
 * @param primary  also record selected text (the PRIMARY selection, pasted with the middle button) and put it
 *                 on the clipboard
 */
public record ClipboardSettings(boolean history, int size, boolean keep, boolean primary) {

    public static final int MIN_SIZE = 5;
    public static final int MAX_SIZE = 100;
    public static final ClipboardSettings DEFAULTS = new ClipboardSettings(true, 30, true, false);

    public ClipboardSettings {
        size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }
}
