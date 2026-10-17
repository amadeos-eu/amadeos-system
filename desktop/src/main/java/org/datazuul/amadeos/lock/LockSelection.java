package org.datazuul.amadeos.lock;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import org.datazuul.amadeos.base.x11.LibX11;

/**
 * The session-wide lock state is the ownership of the X selection {@code _AMADEOS_LOCK}.
 * The lock process owns it while the screen is locked; the X server releases it automatically
 * when that process dies, and every client (window manager, panel) can query it.
 */
public final class LockSelection {
    public static final String ATOM = "_AMADEOS_LOCK";

    private LockSelection() {
    }

    public static boolean isLocked(LibX11 x11, Pointer display) {
        NativeLong owner = x11.XGetSelectionOwner(display, atom(x11, display));
        return owner != null && owner.longValue() != 0;
    }

    /** Takes the selection for {@code owner}; false if another lock process already holds it. */
    static boolean acquire(LibX11 x11, Pointer display, NativeLong owner) {
        NativeLong atom = atom(x11, display);
        NativeLong current = x11.XGetSelectionOwner(display, atom);
        if (current != null && current.longValue() != 0) {
            return false;
        }
        x11.XSetSelectionOwner(display, atom, owner, new NativeLong(LibX11.CurrentTime));
        NativeLong now = x11.XGetSelectionOwner(display, atom);
        return now != null && now.longValue() == owner.longValue();
    }

    private static NativeLong atom(LibX11 x11, Pointer display) {
        return x11.XInternAtom(display, ATOM, 0);
    }
}
