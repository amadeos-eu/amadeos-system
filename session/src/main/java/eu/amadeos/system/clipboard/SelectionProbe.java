package eu.amadeos.system.clipboard;

import java.util.List;

/** What the clipboard manager asks the X server itself; {@link X11SelectionProbe} does it, the tests fake it. */
public interface SelectionProbe {

    /** The window that owns the selection, 0 for none. */
    long owner(Selection selection);

    /** The names of the formats (targets) the owner of the selection offers; empty if it does not answer. */
    List<String> targets(Selection selection);

    /** True while a mouse button is held down, for example while text is still being selected. */
    boolean buttonDown();
}
