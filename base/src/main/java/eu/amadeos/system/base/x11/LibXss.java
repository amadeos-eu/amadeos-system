package eu.amadeos.system.base.x11;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

/**
 * JNA binding to libXss, the client side of the X11 MIT-SCREEN-SAVER extension (Debian package
 * {@code libxss1}). The X server counts the time since the last keyboard or pointer input of any client;
 * the screen saver reads it here instead of watching input itself.
 */
public interface LibXss extends Library {

    /** Loaded on first use, so that the desktop still starts when libXss is missing. */
    static LibXss load() {
        return Native.load("Xss", LibXss.class);
    }

    @Structure.FieldOrder({"window", "state", "kind", "til_or_since", "idle", "eventMask"})
    class XScreenSaverInfo extends Structure {
        public NativeLong window;
        public int state;
        public int kind;
        public NativeLong til_or_since;
        /** Milliseconds since the last input event. */
        public NativeLong idle;
        public NativeLong eventMask;
    }

    int XScreenSaverQueryExtension(Pointer display, IntByReference eventBase, IntByReference errorBase);

    int XScreenSaverQueryInfo(Pointer display, NativeLong drawable, XScreenSaverInfo info);
}
