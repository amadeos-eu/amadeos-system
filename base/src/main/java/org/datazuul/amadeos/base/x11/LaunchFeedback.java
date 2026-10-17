package org.datazuul.amadeos.base.x11;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tells the window manager that an application is starting. The window manager then shows the
 * busy cursor (a watch) on the desktop and the window frames until a new window appears or
 * {@link #TIMEOUT_MILLIS} have passed, and sets {@link #BUSY_PROPERTY} on the root window
 * meanwhile, so that the front panel can show the busy cursor too.
 *
 * <p>The message is a ClientMessage of type {@link #MESSAGE_ATOM} sent to the root window, like
 * the EWMH requests; it goes over a connection of its own, so any thread and process may call
 * {@link #announce()}. Without an X display it does nothing.
 */
public final class LaunchFeedback {
    private static final Logger LOGGER = Logger.getLogger(LaunchFeedback.class.getName());
    public static final String MESSAGE_ATOM = "_AMADEOS_LAUNCH";
    public static final String BUSY_PROPERTY = "_AMADEOS_LAUNCH_BUSY";
    /** Long enough for a cold start of Firefox in a virtual machine. */
    public static final long TIMEOUT_MILLIS = 20_000;

    private static Pointer display;
    private static boolean unavailable;

    private LaunchFeedback() {
    }

    /** Asks the window manager to show the busy cursor until the next new window. */
    public static synchronized void announce() {
        if (unavailable) {
            return;
        }
        try {
            LibX11 x11 = LibX11.INSTANCE;
            if (display == null) {
                display = x11.XOpenDisplay(null);
                if (display == null) {
                    unavailable = true;
                    return;
                }
            }
            send(x11, display);
        } catch (LinkageError | RuntimeException e) {
            unavailable = true;
            LOGGER.log(Level.FINE, "No launch feedback: X11 not available", e);
        }
    }

    /** Sends the launch message over the given connection. */
    public static void send(LibX11 x11, Pointer display) {
        NativeLong root = x11.XRootWindow(display, x11.XDefaultScreen(display));
        LibX11.XEvent buffer = new LibX11.XEvent();
        buffer.write();
        LibX11.XClientMessageEvent message = new LibX11.XClientMessageEvent(buffer.getPointer());
        message.type = LibX11.ClientMessage;
        message.send_event = 1;
        message.display = display;
        message.window = root;
        message.message_type = x11.XInternAtom(display, MESSAGE_ATOM, 0);
        message.format = 32;
        for (int i = 0; i < message.data.length; i++) {
            message.data[i] = new NativeLong(0);
        }
        message.write();
        long mask = LibX11.SubstructureRedirectMask | LibX11.SubstructureNotifyMask;
        x11.XSendEvent(display, root, 0, new NativeLong(mask), buffer.getPointer());
        x11.XFlush(display);
    }
}
