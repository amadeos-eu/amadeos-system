package eu.amadeos.system.base.x11;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

/**
 * JNA binding to libXfixes (Debian package {@code libxfixes3}), here for its selection events: the X server
 * tells a client whenever a selection such as {@code CLIPBOARD} gets a new owner, or loses it because the
 * owner's window was destroyed or the owner's connection closed. The clipboard manager watches the clipboard
 * this way instead of asking over and over.
 */
public interface LibXfixes extends Library {

    /** Loaded on first use, so that the desktop still starts when libXfixes is missing. */
    static LibXfixes load() {
        return Native.load("Xfixes", LibXfixes.class);
    }

    /** The selection event, at the event base the server reported for the extension. */
    int XFixesSelectionNotify = 0;

    int XFixesSetSelectionOwnerNotifyMask = 1;
    int XFixesSelectionWindowDestroyNotifyMask = 1 << 1;
    int XFixesSelectionClientCloseNotifyMask = 1 << 2;

    /** Subtypes of {@link XFixesSelectionNotifyEvent}. */
    int XFixesSetSelectionOwnerNotify = 0;
    int XFixesSelectionWindowDestroyNotify = 1;
    int XFixesSelectionClientCloseNotify = 2;

    @Structure.FieldOrder({"type", "serial", "send_event", "display", "window", "subtype", "owner", "selection",
            "timestamp", "selection_timestamp"})
    class XFixesSelectionNotifyEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong window;
        public int subtype;
        /** The new owner, or None when the selection was lost. */
        public NativeLong owner;
        public NativeLong selection;
        public NativeLong timestamp;
        public NativeLong selection_timestamp;

        public XFixesSelectionNotifyEvent() {
            super();
        }

        public XFixesSelectionNotifyEvent(Pointer p) {
            super(p);
            read();
        }
    }

    int XFixesQueryExtension(Pointer display, IntByReference eventBase, IntByReference errorBase);

    void XFixesSelectSelectionInput(Pointer display, NativeLong window, NativeLong selection, NativeLong eventMask);
}
