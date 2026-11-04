package eu.amadeos.system.base.x11;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA binding to libXcursor (Debian package {@code libxcursor1}), which libX11 loads behind
 * {@code XCreateFontCursor} to draw themed pointers. libXcursor reads the theme and size once per display
 * connection, from the resources the connection saw when it opened; a program that should follow a changed
 * pointer (Settings &gt; Accessibility) sets them anew here before it creates its cursors again.
 */
public interface LibXcursor extends Library {

    /** Loaded on first use, so that the desktop still starts when libXcursor is missing. */
    static LibXcursor load() {
        return Native.load("Xcursor", LibXcursor.class);
    }

    /** The theme the next cursors of this display come from; a theme that does not exist means X's own cursors. */
    int XcursorSetTheme(Pointer display, String theme);

    /** The nominal pointer size in pixels for the next cursors of this display. */
    int XcursorSetDefaultSize(Pointer display, int size);

    /** A cursor of the current theme and size by its name ({@code left_ptr}, {@code xterm}, ...); 0 if missing. */
    com.sun.jna.NativeLong XcursorLibraryLoadCursor(Pointer display, String name);
}
