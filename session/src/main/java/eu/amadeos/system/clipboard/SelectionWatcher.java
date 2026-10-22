package eu.amadeos.system.clipboard;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import eu.amadeos.system.base.x11.LibX11;
import eu.amadeos.system.base.x11.LibXfixes;

import java.util.logging.Logger;

/**
 * The clipboard manager's X thread. It owns {@code _AMADEOS_CLIPBOARD} (one manager per session; the Front
 * Panel and the Style Manager send their commands to its owner) and, if no other clipboard manager is
 * running, {@code CLIPBOARD_MANAGER}, through which programs ask for their copy to be saved before they
 * close. It hears from XFixes whenever {@code CLIPBOARD} or {@code PRIMARY} changes hands and grabs
 * Ctrl+Alt+V on the root window for the history.
 *
 * <p>Everything here runs on the thread that calls {@link #run()}; the listener hands work on to the
 * manager's worker.
 */
final class SelectionWatcher {
    private static final Logger LOGGER = Logger.getLogger(SelectionWatcher.class.getName());
    static final String INSTANCE_ATOM = "_AMADEOS_CLIPBOARD";
    static final String COMMAND_ATOM = "_AMADEOS_CLIPBOARD_COMMAND";

    /** What the watcher reports. */
    interface Listener {
        void ownerChanged(Selection selection, long owner, boolean gone);

        /** Ctrl+Alt+V was pressed. */
        void hotkey();

        void command(ClipboardCommand command);

        /** A program asks for the clipboard to be saved; answer through the probe. */
        void saveRequested(Request request);
    }

    /** A conversion request, copied out of its event. */
    record Request(long requestor, long selection, long target, long property, long time) {
    }

    /** Thrown when the session already has a clipboard manager of amadeOS. */
    static final class AlreadyRunningException extends Exception {
        AlreadyRunningException() {
            super("another amadeOS clipboard manager is running");
        }
    }

    private final LibX11 x11;
    private final Pointer display;
    private final NativeLong root;
    private final NativeLong window;
    private final int fixesEvent;
    private final NativeLong clipboardAtom;
    private final NativeLong primaryAtom;
    private final NativeLong managerAtom;
    private final NativeLong targetsAtom;
    private final NativeLong saveTargetsAtom;
    private final NativeLong commandAtom;
    private final Listener listener;
    private int hotkeyCode;

    private SelectionWatcher(LibX11 x11, LibXfixes fixes, Pointer display, int fixesEvent, Listener listener)
            throws AlreadyRunningException {
        this.x11 = x11;
        this.display = display;
        this.fixesEvent = fixesEvent;
        this.listener = listener;
        this.root = x11.XRootWindow(display, x11.XDefaultScreen(display));
        this.window = x11.XCreateSimpleWindow(display, root, -10, -10, 1, 1, 0, new NativeLong(0), new NativeLong(0));
        x11.XStoreName(display, window, "amadeOS clipboard manager");
        this.clipboardAtom = atom(Selection.CLIPBOARD.atom());
        this.primaryAtom = atom(Selection.PRIMARY.atom());
        this.managerAtom = atom("CLIPBOARD_MANAGER");
        this.targetsAtom = atom("TARGETS");
        this.saveTargetsAtom = atom("SAVE_TARGETS");
        this.commandAtom = atom(COMMAND_ATOM);

        NativeLong instance = atom(INSTANCE_ATOM);
        if (owned(instance)) throw new AlreadyRunningException();
        x11.XSetSelectionOwner(display, instance, window, new NativeLong(LibX11.CurrentTime));
        if (owned(managerAtom)) {
            LOGGER.warning("Another clipboard manager owns CLIPBOARD_MANAGER; programs cannot ask this one to save");
        } else {
            x11.XSetSelectionOwner(display, managerAtom, window, new NativeLong(LibX11.CurrentTime));
        }
        // --8<-- [start:watch]
        long mask = LibXfixes.XFixesSetSelectionOwnerNotifyMask | LibXfixes.XFixesSelectionWindowDestroyNotifyMask
                | LibXfixes.XFixesSelectionClientCloseNotifyMask;
        fixes.XFixesSelectSelectionInput(display, window, clipboardAtom, new NativeLong(mask));
        fixes.XFixesSelectSelectionInput(display, window, primaryAtom, new NativeLong(mask));
        // --8<-- [end:watch]
        grabHotkey();
        x11.XFlush(display);
    }

    /**
     * Connects to the X server and takes the selections.
     *
     * @throws IllegalStateException if there is no display or the server lacks XFixes
     */
    static SelectionWatcher open(LibX11 x11, Listener listener) throws AlreadyRunningException {
        LibXfixes fixes;
        try {
            fixes = LibXfixes.load();
        } catch (UnsatisfiedLinkError e) {
            throw new IllegalStateException("libXfixes (package libxfixes3) not available", e);
        }
        Pointer display = x11.XOpenDisplay(null);
        if (display == null) throw new IllegalStateException("cannot open X display " + System.getenv("DISPLAY"));
        IntByReference eventBase = new IntByReference();
        if (fixes.XFixesQueryExtension(display, eventBase, new IntByReference()) == 0) {
            x11.XCloseDisplay(display);
            throw new IllegalStateException("X server has no XFIXES extension");
        }
        try {
            return new SelectionWatcher(x11, fixes, display, eventBase.getValue() + LibXfixes.XFixesSelectionNotify,
                    listener);
        } catch (AlreadyRunningException e) {
            x11.XCloseDisplay(display);
            throw e;
        }
    }

    private void grabHotkey() {
        hotkeyCode = Byte.toUnsignedInt(x11.XKeysymToKeycode(display, new NativeLong(LibX11.XK_v)));
        if (hotkeyCode == 0) return;
        long base = LibX11.ControlMask | LibX11.Mod1Mask;
        // The same keys with Num Lock and Caps Lock on.
        for (long extra : new long[]{0, LibX11.Mod2Mask, LibX11.LockMask, LibX11.Mod2Mask | LibX11.LockMask}) {
            x11.XGrabKey(display, hotkeyCode, new NativeLong(base | extra), root, 1,
                    LibX11.GrabModeAsync, LibX11.GrabModeAsync);
        }
    }

    /** Handles events until the connection breaks. */
    void run() {
        LibX11.XEvent event = new LibX11.XEvent();
        while (!Thread.currentThread().isInterrupted()) {
            x11.XNextEvent(display, event.getPointer());
            event.read();
            handle(event);
        }
    }

    private void handle(LibX11.XEvent event) {
        if (event.type == fixesEvent) {
            LibXfixes.XFixesSelectionNotifyEvent e = new LibXfixes.XFixesSelectionNotifyEvent(event.getPointer());
            Selection selection = e.selection.longValue() == clipboardAtom.longValue() ? Selection.CLIPBOARD
                    : e.selection.longValue() == primaryAtom.longValue() ? Selection.PRIMARY : null;
            if (selection == null) return;
            boolean gone = e.subtype != LibXfixes.XFixesSetSelectionOwnerNotify;
            listener.ownerChanged(selection, e.owner == null ? 0 : e.owner.longValue(), gone);
            return;
        }
        switch (event.type) {
            case LibX11.KeyPress -> {
                LibX11.XKeyEvent key = new LibX11.XKeyEvent(event.getPointer());
                if (key.keycode == hotkeyCode) listener.hotkey();
            }
            case LibX11.ClientMessage -> {
                LibX11.XClientMessageEvent message = new LibX11.XClientMessageEvent(event.getPointer());
                if (message.message_type.longValue() == commandAtom.longValue()) {
                    ClipboardCommand.of(message.data[0].intValue()).ifPresent(listener::command);
                }
            }
            case LibX11.SelectionRequest -> request(new LibX11.XSelectionRequestEvent(event.getPointer()));
            case LibX11.SelectionClear -> LOGGER.info("Another program took over CLIPBOARD_MANAGER");
            default -> {
            }
        }
    }

    private void request(LibX11.XSelectionRequestEvent e) {
        long property = e.property.longValue() != 0 ? e.property.longValue() : e.target.longValue();
        Request request = new Request(e.requestor.longValue(), e.selection.longValue(), e.target.longValue(),
                property, e.time.longValue());
        if (e.selection.longValue() != managerAtom.longValue()) {
            notify(x11, display, request, false);
        } else if (e.target.longValue() == targetsAtom.longValue()) {
            Memory atoms = new Memory(2L * Native.LONG_SIZE);
            atoms.setNativeLong(0, targetsAtom);
            atoms.setNativeLong(Native.LONG_SIZE, saveTargetsAtom);
            x11.XChangeProperty(display, e.requestor, new NativeLong(property), new NativeLong(LibX11.XA_ATOM), 32,
                    LibX11.PropModeReplace, atoms, 2);
            notify(x11, display, request, true);
        // --8<-- [start:save-request]
        } else if (e.target.longValue() == saveTargetsAtom.longValue()) {
            listener.saveRequested(request);
        // --8<-- [end:save-request]
        } else {
            notify(x11, display, request, false);
        }
        x11.XFlush(display);
    }

    /** Sends the answer to a conversion request: the property on success, None on refusal. */
    static void notify(LibX11 x11, Pointer display, Request request, boolean success) {
        LibX11.XEvent buffer = new LibX11.XEvent();
        buffer.write();
        LibX11.XSelectionEvent answer = new LibX11.XSelectionEvent(buffer.getPointer());
        answer.type = LibX11.SelectionNotify;
        answer.send_event = 1;
        answer.display = display;
        answer.requestor = new NativeLong(request.requestor());
        answer.selection = new NativeLong(request.selection());
        answer.target = new NativeLong(request.target());
        answer.property = new NativeLong(success ? request.property() : 0);
        answer.time = new NativeLong(request.time());
        answer.write();
        x11.XSendEvent(display, answer.requestor, 0, new NativeLong(0), buffer.getPointer());
        x11.XFlush(display);
    }

    private boolean owned(NativeLong selection) {
        NativeLong owner = x11.XGetSelectionOwner(display, selection);
        return owner != null && owner.longValue() != 0;
    }

    private NativeLong atom(String name) {
        return x11.XInternAtom(display, name, 0);
    }
}
