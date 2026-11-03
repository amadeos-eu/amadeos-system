package eu.amadeos.system.settings;

import eu.amadeos.system.base.shortcuts.KeyCombo;
import eu.amadeos.system.base.x11.LibX11;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Reads the keys of a new shortcut. The window manager has grabbed the keys of the current shortcuts, so a
 * window of the Settings would never see Super+L or Ctrl+Alt+T: the capture takes the whole keyboard with an
 * active grab on its own X connection instead, which comes before every passive grab, and reads the key presses
 * there until a key that is not a modifier comes.
 */
interface KeyCapture {

    /** What came of a capture. */
    sealed interface Result {
        /** The new keys. */
        record Keys(KeyCombo keys) implements Result {
        }

        /** Backspace on its own: the shortcut is to be switched off. */
        record Off() implements Result {
        }

        /** Esc on its own, or the dialog was closed. */
        record Cancelled() implements Result {
        }

        /** The keyboard could not be grabbed; the dialog reads the keys itself then. */
        record Unavailable() implements Result {
        }
    }

    /** A running capture. */
    interface Session {
        void cancel();
    }

    /**
     * Starts reading on a thread of its own.
     *
     * @param held called with the modifiers held so far, as a mask of {@link KeyCombo}
     * @param done called once with the result; both are called on that thread
     */
    Session start(Consumer<Integer> held, Consumer<Result> done);

    /** The capture through the X server. */
    static KeyCapture x11() {
        return X11.INSTANCE;
    }

    /** Turns a key press into a result: a modifier alone gives null (go on reading). */
    static Result result(String keysym, int state) {
        if (keysym == null || KeyCombo.isModifierKey(keysym)) return null;
        int modifiers = state & KeyCombo.ALL_MODIFIERS;
        if (modifiers == 0 && keysym.equals("Escape")) return new Result.Cancelled();
        if (modifiers == 0 && keysym.equals("BackSpace")) return new Result.Off();
        return new Result.Keys(KeyCombo.of(modifiers, keysym));
    }

    // --8<-- [start:capture]
    /** {@link KeyCapture} with XGrabKeyboard on a connection of its own. */
    final class X11 implements KeyCapture {
        static final X11 INSTANCE = new X11();
        private static final Logger LOGGER = Logger.getLogger(X11.class.getName());

        @Override
        public Session start(Consumer<Integer> held, Consumer<Result> done) {
            Thread thread = Thread.ofPlatform().daemon().name("amadeos-settings-key-capture").start(() -> {
                Result result;
                try {
                    result = read(held);
                } catch (RuntimeException | UnsatisfiedLinkError e) {
                    LOGGER.fine(() -> "No key capture through X: " + e.getMessage());
                    result = new Result.Unavailable();
                }
                if (!Thread.currentThread().isInterrupted()) done.accept(result);
            });
            return thread::interrupt;
        }

        private static Result read(Consumer<Integer> held) {
            LibX11 x11 = LibX11.INSTANCE;
            Pointer display = x11.XOpenDisplay(null);
            if (display == null) return new Result.Unavailable();
            try {
                NativeLong root = x11.XRootWindow(display, x11.XDefaultScreen(display));
                // The click that opened the dialog may still hold a grab for a moment.
                int grab = -1;
                for (int i = 0; i < 20 && grab != LibX11.GrabSuccess; i++) {
                    grab = x11.XGrabKeyboard(display, root, 0, LibX11.GrabModeAsync, LibX11.GrabModeAsync,
                            new NativeLong(LibX11.CurrentTime));
                    if (grab != LibX11.GrabSuccess) sleep(50);
                }
                if (grab != LibX11.GrabSuccess) return new Result.Unavailable();
                LibX11.XEvent event = new LibX11.XEvent();
                while (!Thread.currentThread().isInterrupted()) {
                    if (x11.XPending(display) == 0) {
                        sleep(15);
                        continue;
                    }
                    x11.XNextEvent(display, event.getPointer());
                    event.read();
                    if (event.type != LibX11.KeyPress) continue;
                    LibX11.XKeyEvent key = new LibX11.XKeyEvent(event.getPointer());
                    // Level 0: the key without Shift, so Shift+1 is "Shift+1", not "!".
                    String keysym = x11.XKeysymToString(x11.XkbKeycodeToKeysym(display, key.keycode, 0, 0));
                    Result result = result(keysym, key.state);
                    if (result != null) return result;
                    held.accept((key.state | modifierOf(keysym)) & KeyCombo.ALL_MODIFIERS);
                }
                return new Result.Cancelled();
            } finally {
                x11.XUngrabKeyboard(display, new NativeLong(LibX11.CurrentTime));
                x11.XCloseDisplay(display);
            }
        }

        /** The mask of a modifier key that is just being pressed (its own press does not have it in the state yet). */
        private static int modifierOf(String keysym) {
            if (keysym == null) return 0;
            if (keysym.startsWith("Shift")) return KeyCombo.SHIFT;
            if (keysym.startsWith("Control")) return KeyCombo.CTRL;
            if (keysym.startsWith("Alt") || keysym.startsWith("Meta")) return KeyCombo.ALT;
            if (keysym.startsWith("Super") || keysym.startsWith("Hyper")) return KeyCombo.SUPER;
            return 0;
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    // --8<-- [end:capture]
}
