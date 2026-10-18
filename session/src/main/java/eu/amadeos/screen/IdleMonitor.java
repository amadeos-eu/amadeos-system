package eu.amadeos.screen;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import eu.amadeos.base.x11.LockSelection;
import eu.amadeos.base.x11.LibX11;
import eu.amadeos.base.x11.LibXss;

/**
 * Asks the X server how long the session has been idle and whether the screen is locked.
 */
public interface IdleMonitor extends AutoCloseable {

    /** Milliseconds since the last keyboard or pointer input. */
    long idleMillis();

    /** True while a screen lock process owns {@code _AMADEOS_LOCK}. */
    boolean locked();

    @Override
    void close();

    /** Thrown when there is no X display or the MIT-SCREEN-SAVER extension is missing. */
    final class UnavailableException extends Exception {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The monitor of the current {@code $DISPLAY}, through libX11 and libXss. */
    static IdleMonitor x11() throws UnavailableException {
        return X11IdleMonitor.open();
    }

    final class X11IdleMonitor implements IdleMonitor {
        private final LibX11 x11;
        private final LibXss xss;
        private final Pointer display;
        private final NativeLong root;
        private final LibXss.XScreenSaverInfo info = new LibXss.XScreenSaverInfo();

        private X11IdleMonitor(LibX11 x11, LibXss xss, Pointer display) {
            this.x11 = x11;
            this.xss = xss;
            this.display = display;
            this.root = x11.XRootWindow(display, 0);
        }

        static X11IdleMonitor open() throws UnavailableException {
            LibX11 x11;
            LibXss xss;
            try {
                x11 = LibX11.INSTANCE;
                xss = LibXss.load();
            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                throw new UnavailableException("libX11 or libXss (package libxss1) not available", e);
            }
            Pointer display = x11.XOpenDisplay(null);
            if (display == null) throw new UnavailableException("cannot open X display " + System.getenv("DISPLAY"), null);
            if (xss.XScreenSaverQueryExtension(display, new IntByReference(), new IntByReference()) == 0) {
                x11.XCloseDisplay(display);
                throw new UnavailableException("X server has no MIT-SCREEN-SAVER extension", null);
            }
            return new X11IdleMonitor(x11, xss, display);
        }

        @Override
        public synchronized long idleMillis() {
            if (xss.XScreenSaverQueryInfo(display, root, info) == 0) return 0;
            return info.idle == null ? 0 : info.idle.longValue();
        }

        @Override
        public synchronized boolean locked() {
            return LockSelection.isLocked(x11, display);
        }

        @Override
        public synchronized void close() {
            x11.XCloseDisplay(display);
        }
    }
}
