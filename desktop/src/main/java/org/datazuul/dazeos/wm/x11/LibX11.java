package org.datazuul.dazeos.wm.x11;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

public interface LibX11 extends Library {
    LibX11 INSTANCE = Native.load("X11", LibX11.class);

    // Event masks
    long KeyPressMask = 1L << 0;
    long KeyReleaseMask = 1L << 1;
    long ButtonPressMask = 1L << 2;
    long ButtonReleaseMask = 1L << 3;
    long EnterWindowMask = 1L << 4;
    long LeaveWindowMask = 1L << 5;
    long PointerMotionMask = 1L << 6;
    long ExposureMask = 1L << 15;
    long StructureNotifyMask = 1L << 17;
    long SubstructureNotifyMask = 1L << 19;
    long SubstructureRedirectMask = 1L << 20;
    long FocusChangeMask = 1L << 21;
    long PropertyChangeMask = 1L << 22;

    // Event types
    int KeyPress = 2;
    int KeyRelease = 3;
    int ButtonPress = 4;
    int ButtonRelease = 5;
    int MotionNotify = 6;
    int EnterNotify = 7;
    int LeaveNotify = 8;
    int FocusIn = 9;
    int FocusOut = 10;
    int CreateNotify = 16;
    int DestroyNotify = 17;
    int UnmapNotify = 18;
    int MapNotify = 19;
    int MapRequest = 20;
    int ReparentNotify = 21;
    int ConfigureNotify = 22;
    int ConfigureRequest = 23;
    int PropertyNotify = 28;
    int ClientMessage = 33;

    // Pointer and Button constants
    int Button1 = 1;
    long AnyModifier = 1L << 15;
    int GrabModeSync = 0;
    int GrabModeAsync = 1;
    int AsyncPointer = 0;
    int ReplayPointer = 2;

    // Property modes and standard atoms
    int PropModeReplace = 0;
    long XA_ATOM = 4L;
    long XA_CARDINAL = 6L;
    long XA_STRING = 31L;
    long XA_WINDOW = 33L;

    // RevertTo focus
    int RevertToNone = 0;
    int RevertToPointerRoot = 1;
    int RevertToParent = 2;

    // CurrentTime
    long CurrentTime = 0L;

    // Value masks for XConfigureWindow
    long CWX = 1L << 0;
    long CWY = 1L << 1;
    long CWWidth = 1L << 2;
    long CWHeight = 1L << 3;
    long CWBorderWidth = 1L << 4;
    long CWSibling = 1L << 5;
    long CWStackMode = 1L << 6;

    // Error codes
    int BadAccess = 10;

    interface XErrorHandler extends Callback {
        int apply(Pointer display, XErrorEvent errorEvent);
    }

    @Structure.FieldOrder({"type", "display", "resourceid", "serial", "error_code", "request_code", "minor_code"})
    class XErrorEvent extends Structure {
        public int type;
        public Pointer display;
        public NativeLong resourceid;
        public NativeLong serial;
        public byte error_code;
        public byte request_code;
        public byte minor_code;

        public XErrorEvent() {
            super();
        }

        public XErrorEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"type", "serial", "send_event", "display", "window"})
    class XAnyEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong window;

        public XAnyEvent() {
            super();
        }

        public XAnyEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"type", "serial", "send_event", "display", "parent", "window"})
    class XMapRequestEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong parent;
        public NativeLong window;

        public XMapRequestEvent() {
            super();
        }

        public XMapRequestEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({
        "type", "serial", "send_event", "display", "parent", "window",
        "x", "y", "width", "height", "border_width", "above", "detail", "value_mask"
    })
    class XConfigureRequestEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong parent;
        public NativeLong window;
        public int x, y;
        public int width, height;
        public int border_width;
        public NativeLong above;
        public int detail;
        public NativeLong value_mask;

        public XConfigureRequestEvent() {
            super();
        }

        public XConfigureRequestEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"type", "serial", "send_event", "display", "event", "window"})
    class XDestroyWindowEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong event;
        public NativeLong window;

        public XDestroyWindowEvent() {
            super();
        }

        public XDestroyWindowEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"type", "serial", "send_event", "display", "event", "window", "from_configure"})
    class XUnmapEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong event;
        public NativeLong window;
        public int from_configure;

        public XUnmapEvent() {
            super();
        }

        public XUnmapEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({
        "type", "serial", "send_event", "display", "window",
        "root", "subwindow", "time", "x", "y", "x_root", "y_root",
        "state", "button", "same_screen"
    })
    class XButtonEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong window;
        public NativeLong root;
        public NativeLong subwindow;
        public NativeLong time;
        public int x, y;
        public int x_root, y_root;
        public int state;
        public int button;
        public int same_screen;

        public XButtonEvent() {
            super();
        }

        public XButtonEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"type", "serial", "send_event", "display", "window", "message_type", "format", "data"})
    class XClientMessageEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int send_event;
        public Pointer display;
        public NativeLong window;
        public NativeLong message_type;
        public int format;
        public NativeLong[] data = new NativeLong[5];

        public XClientMessageEvent() {
            super();
        }

        public XClientMessageEvent(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"type", "pad"})
    class XEvent extends Structure {
        public int type;
        public NativeLong[] pad = new NativeLong[24];

        public XEvent() {
            super();
        }
    }

    @Structure.FieldOrder({"x", "y", "width", "height", "border_width", "sibling", "stack_mode"})
    class XWindowChanges extends Structure {
        public int x;
        public int y;
        public int width;
        public int height;
        public int border_width;
        public NativeLong sibling;
        public int stack_mode;

        public XWindowChanges() {
            super();
        }
    }

    @Structure.FieldOrder({
        "x", "y", "width", "height", "border_width", "depth",
        "visual", "root", "class_", "bit_gravity", "win_gravity",
        "backing_store", "backing_planes", "backing_pixel", "save_under",
        "colormap", "map_installed", "map_state", "all_event_masks",
        "your_event_mask", "do_not_propagate_mask", "override_redirect", "screen"
    })
    class XWindowAttributes extends Structure {
        public int x, y;
        public int width, height;
        public int border_width;
        public int depth;
        public Pointer visual;
        public NativeLong root;
        public int class_;
        public int bit_gravity;
        public int win_gravity;
        public int backing_store;
        public NativeLong backing_planes;
        public NativeLong backing_pixel;
        public int save_under;
        public NativeLong colormap;
        public int map_installed;
        public int map_state;
        public NativeLong all_event_masks;
        public NativeLong your_event_mask;
        public NativeLong do_not_propagate_mask;
        public int override_redirect;
        public Pointer screen;

        public XWindowAttributes() {
            super();
        }
    }

    Pointer XOpenDisplay(String displayName);

    int XCloseDisplay(Pointer display);

    int XDefaultScreen(Pointer display);

    NativeLong XRootWindow(Pointer display, int screenNumber);

    int XDisplayWidth(Pointer display, int screenNumber);

    int XDisplayHeight(Pointer display, int screenNumber);

    int XSelectInput(Pointer display, NativeLong window, NativeLong eventMask);

    int XMapWindow(Pointer display, NativeLong window);

    int XUnmapWindow(Pointer display, NativeLong window);

    int XMoveResizeWindow(Pointer display, NativeLong window, int x, int y, int width, int height);

    int XConfigureWindow(Pointer display, NativeLong window, NativeLong valueMask, XWindowChanges values);

    int XSetInputFocus(Pointer display, NativeLong focus, int revertTo, NativeLong time);

    int XGetWindowAttributes(Pointer display, NativeLong window, XWindowAttributes windowAttributesReturn);

    int XNextEvent(Pointer display, Pointer eventReturn);

    int XPending(Pointer display);

    int XFlush(Pointer display);

    int XSync(Pointer display, int discard);

    Pointer XSetErrorHandler(XErrorHandler handler);

    int XQueryTree(
        Pointer display,
        NativeLong window,
        PointerByReference rootReturn,
        PointerByReference parentReturn,
        PointerByReference childrenReturn,
        IntByReference nchildrenReturn
    );

    int XFree(Pointer data);

    int XRaiseWindow(Pointer display, NativeLong window);

    int XLowerWindow(Pointer display, NativeLong window);

    int XGrabButton(
        Pointer display,
        int button,
        NativeLong modifiers,
        NativeLong grab_window,
        int owner_events,
        NativeLong event_mask,
        int pointer_mode,
        int keyboard_mode,
        NativeLong confine_to,
        NativeLong cursor
    );

    int XUngrabButton(Pointer display, int button, NativeLong modifiers, NativeLong grab_window);

    int XAllowEvents(Pointer display, int event_mode, NativeLong time);

    NativeLong XInternAtom(Pointer display, String atom_name, int only_if_exists);

    int XChangeProperty(
        Pointer display,
        NativeLong window,
        NativeLong property,
        NativeLong type,
        int format,
        int mode,
        Pointer data,
        int nelements
    );

    int XDeleteProperty(Pointer display, NativeLong window, NativeLong property);

    int XSendEvent(Pointer display, NativeLong window, int propagate, NativeLong event_mask, Pointer event_send);

    NativeLong XCreateSimpleWindow(
        Pointer display,
        NativeLong parent,
        int x,
        int y,
        int width,
        int height,
        int border_width,
        NativeLong border,
        NativeLong background
    );

    int XDestroyWindow(Pointer display, NativeLong window);

    int XKillClient(Pointer display, NativeLong resource);

    int XGetTransientForHint(Pointer display, NativeLong window, Pointer prop_window_return);
}

