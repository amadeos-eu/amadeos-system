package eu.amadeos.system.clipboard;

import java.util.Optional;

/** Reading and taking over the X selections' contents; {@link AwtClipboards} does it, the tests fake it. */
public interface SystemClipboards {

    /** What the owner of the selection offers now, or nothing if it offers nothing the history can keep. */
    Optional<ClipEntry> read(Selection selection);

    /** Makes this program the owner of the clipboard, offering the entry. */
    void own(ClipEntry entry);
}
