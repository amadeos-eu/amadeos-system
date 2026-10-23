package eu.amadeos.system.clipboard;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import eu.amadeos.system.base.x11.LibX11;

import java.util.Optional;

/**
 * The commands other programs send to the running clipboard manager: the Front Panel's Clipboard entry
 * ({@code amadeos-clipboard}) shows the history, the Settings clear it. They travel as an X client
 * message to the owner of {@code _AMADEOS_CLIPBOARD}.
 */
public enum ClipboardCommand {
    SHOW(1),
    CLEAR(2);

    private final int code;

    ClipboardCommand(int code) {
        this.code = code;
    }

    static Optional<ClipboardCommand> of(int code) {
        for (ClipboardCommand c : values()) {
            if (c.code == code) return Optional.of(c);
        }
        return Optional.empty();
    }

    /** Sends the command to the clipboard manager of the session; false if none is running. */
    public boolean send() {
        LibX11 x11;
        try {
            x11 = LibX11.INSTANCE;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return false;
        }
        Pointer display = x11.XOpenDisplay(null);
        if (display == null) return false;
        try {
            NativeLong owner = x11.XGetSelectionOwner(display, x11.XInternAtom(display, SelectionWatcher.INSTANCE_ATOM, 0));
            if (owner == null || owner.longValue() == 0) return false;
            LibX11.XEvent buffer = new LibX11.XEvent();
            buffer.write();
            LibX11.XClientMessageEvent message = new LibX11.XClientMessageEvent(buffer.getPointer());
            message.type = LibX11.ClientMessage;
            message.send_event = 1;
            message.display = display;
            message.window = owner;
            message.message_type = x11.XInternAtom(display, SelectionWatcher.COMMAND_ATOM, 0);
            message.format = 32;
            for (int i = 0; i < message.data.length; i++) message.data[i] = new NativeLong(0);
            message.data[0] = new NativeLong(code);
            message.write();
            x11.XSendEvent(display, owner, 0, new NativeLong(0), buffer.getPointer());
            x11.XFlush(display);
            return true;
        } finally {
            x11.XCloseDisplay(display);
        }
    }
}
