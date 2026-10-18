package eu.amadeos.base.x11;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

/**
 * JNA binding to the client side of the X Keyboard Extension (XKB), which is part of libX11. The window
 * manager uses it for the bell: it turns the server's own bell (the PC speaker, missing on most machines
 * and in QEMU) off and is told of every bell instead, so it can play the tone through the sound card.
 */
public interface LibXkb extends Library {

    static LibXkb load() {
        return Native.load("X11", LibXkb.class);
    }

    /** Device spec of the core keyboard. */
    int XkbUseCoreKbd = 0x0100;
    int XkbMajorVersion = 1;
    int XkbMinorVersion = 0;
    /** {@code xkb_type} of a bell event; all XKB events share the extension's one event type. */
    int XkbBellNotify = 8;
    long XkbBellNotifyMask = 1L << 8;
    int XkbAudibleBellMask = 1 << 9;

    /**
     * A bell rang ({@code XBell}, {@code XkbBell}, the terminal's ^G). {@code percent} is the volume the
     * server computed from the request and the base volume ({@code xset b}), {@code pitch} and
     * {@code duration} are the keyboard's bell settings.
     */
    @Structure.FieldOrder({"type", "serial", "send_event", "display", "time", "xkb_type", "device", "percent",
            "pitch", "duration", "bell_class", "bell_id", "name", "window", "event_only"})
    class XkbBellNotifyEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong time;
        public int xkb_type;
        public int device;
        public int percent;
        public int pitch;
        public int duration;
        public int bell_class;
        public int bell_id;
        public NativeLong name;
        public NativeLong window;
        public int event_only;

        public XkbBellNotifyEvent(Pointer p) {
            super(p);
            read();
        }
    }

    boolean XkbQueryExtension(Pointer display, IntByReference opcode, IntByReference eventBase,
            IntByReference errorBase, IntByReference major, IntByReference minor);

    boolean XkbSelectEvents(Pointer display, int deviceSpec, NativeLong bitsToChange, NativeLong valuesForBits);

    boolean XkbChangeEnabledControls(Pointer display, int deviceSpec, int affect, int values);
}
