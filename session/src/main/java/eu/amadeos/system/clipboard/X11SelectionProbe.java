package eu.amadeos.system.clipboard;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import eu.amadeos.system.base.x11.LibX11;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The questions the clipboard manager asks the X server on its own connection, used only by the manager's
 * worker thread: who owns a selection, which formats the owner offers (to see a password manager's hint
 * before reading anything) and whether a mouse button is down. The worker also answers a program's request
 * to save the clipboard through it, once the copy is read.
 */
final class X11SelectionProbe implements SelectionProbe, AutoCloseable {
    private static final long TARGETS_TIMEOUT_MS = 1000;
    private static final int BUTTONS_1_TO_3 = (1 << 8) | (1 << 9) | (1 << 10);

    private final LibX11 x11;
    private final Pointer display;
    private final NativeLong root;
    private final NativeLong window;
    private final NativeLong targetsAtom;
    private final NativeLong property;
    private final NativeLong nullAtom;
    private final Map<Selection, NativeLong> selections = new EnumMap<>(Selection.class);
    private final Map<Long, String> atomNames = new HashMap<>();

    private X11SelectionProbe(LibX11 x11, Pointer display) {
        this.x11 = x11;
        this.display = display;
        this.root = x11.XRootWindow(display, x11.XDefaultScreen(display));
        this.window = x11.XCreateSimpleWindow(display, root, -10, -10, 1, 1, 0, new NativeLong(0), new NativeLong(0));
        x11.XStoreName(display, window, "amadeOS clipboard probe");
        this.targetsAtom = x11.XInternAtom(display, "TARGETS", 0);
        this.property = x11.XInternAtom(display, "_AMADEOS_CLIPBOARD_TARGETS", 0);
        this.nullAtom = x11.XInternAtom(display, "NULL", 0);
        for (Selection s : Selection.values()) selections.put(s, x11.XInternAtom(display, s.atom(), 0));
    }

    static X11SelectionProbe open(LibX11 x11) {
        Pointer display = x11.XOpenDisplay(null);
        if (display == null) throw new IllegalStateException("cannot open X display " + System.getenv("DISPLAY"));
        return new X11SelectionProbe(x11, display);
    }

    @Override
    public long owner(Selection selection) {
        NativeLong owner = x11.XGetSelectionOwner(display, selections.get(selection));
        return owner == null ? 0 : owner.longValue();
    }

    @Override
    public List<String> targets(Selection selection) {
        x11.XDeleteProperty(display, window, property);
        x11.XConvertSelection(display, selections.get(selection), targetsAtom, property, window,
                new NativeLong(LibX11.CurrentTime));
        x11.XFlush(display);
        LibX11.XSelectionEvent answer = awaitSelectionNotify();
        if (answer == null || answer.property == null || answer.property.longValue() == 0) return List.of();

        PointerByReference data = new PointerByReference();
        IntByReference format = new IntByReference();
        NativeLongByReference items = new NativeLongByReference();
        int status = x11.XGetWindowProperty(display, window, property, new NativeLong(0), new NativeLong(1024), 1,
                new NativeLong(LibX11.AnyPropertyType), new PointerByReference(), format, items,
                new NativeLongByReference(), data);
        if (status != 0 || data.getValue() == null) return List.of();
        List<String> names = new ArrayList<>();
        try {
            if (format.getValue() != 32) return List.of();
            long count = items.getValue().longValue();
            for (int i = 0; i < count; i++) {
                long atom = Native.LONG_SIZE == 8 ? data.getValue().getLong(i * 8L) : data.getValue().getInt(i * 4L);
                String name = atomName(atom);
                if (name != null) names.add(name);
            }
        } finally {
            x11.XFree(data.getValue());
        }
        return names;
    }

    private LibX11.XSelectionEvent awaitSelectionNotify() {
        long deadline = System.currentTimeMillis() + TARGETS_TIMEOUT_MS;
        LibX11.XEvent event = new LibX11.XEvent();
        while (System.currentTimeMillis() < deadline) {
            if (x11.XPending(display) > 0) {
                x11.XNextEvent(display, event.getPointer());
                event.read();
                if (event.type == LibX11.SelectionNotify) return new LibX11.XSelectionEvent(event.getPointer());
                continue;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private String atomName(long atom) {
        return atomNames.computeIfAbsent(atom, a -> {
            Pointer name = x11.XGetAtomName(display, new NativeLong(a));
            if (name == null) return null;
            try {
                return name.getString(0);
            } finally {
                x11.XFree(name);
            }
        });
    }

    @Override
    public boolean buttonDown() {
        IntByReference mask = new IntByReference();
        int same = x11.XQueryPointer(display, root, new NativeLongByReference(), new NativeLongByReference(),
                new IntByReference(), new IntByReference(), new IntByReference(), new IntByReference(), mask);
        return same != 0 && (mask.getValue() & BUTTONS_1_TO_3) != 0;
    }

    /**
     * Answers a {@code SAVE_TARGETS} request: success names the request's property (left empty), failure
     * names none.
     */
    void answerSave(SelectionWatcher.Request request, boolean saved) {
        if (saved) {
            x11.XChangeProperty(display, new NativeLong(request.requestor()), new NativeLong(request.property()),
                    nullAtom, 32, LibX11.PropModeReplace, null, 0);
        }
        SelectionWatcher.notify(x11, display, request, saved);
    }

    @Override
    public void close() {
        x11.XDestroyWindow(display, window);
        x11.XCloseDisplay(display);
    }
}
