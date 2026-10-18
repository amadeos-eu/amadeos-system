package eu.amadeos.system.lock;

import eu.amadeos.system.base.x11.LockSelection;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import eu.amadeos.system.base.pam.PamAuthenticator;
import eu.amadeos.system.base.x11.LibX11;

/**
 * The screen lock process ({@code amadeos.jar lock}, usually started through
 * {@code /usr/bin/amadeos-lock}).
 *
 * <ol>
 *   <li>Takes the {@code _AMADEOS_LOCK} selection, so there is exactly one lock and every
 *       client can see that the session is locked.</li>
 *   <li>Covers all monitors with an override-redirect window.</li>
 *   <li>Grabs keyboard and pointer on its own X connection. While the grab is active no other
 *       client, including window manager shortcuts, receives input. If the grab cannot be
 *       taken the process gives up instead of pretending to lock.</li>
 *   <li>Reads the password from the grabbed key events and checks it with PAM.</li>
 * </ol>
 *
 * <p>Exit codes: 0 unlocked, 3 another lock is active, 4 input could not be grabbed,
 * 5 no X display.
 */
public final class ScreenLock {
    /** The PAM service of the lock, /etc/pam.d/amadeos-lock from the package amadeos-session. */
    static final String PAM_SERVICE = "amadeos-lock";
    private static final Logger LOGGER = Logger.getLogger(ScreenLock.class.getName());
    public static final int UNLOCKED = 0;
    public static final int ALREADY_LOCKED = 3;
    public static final int GRAB_FAILED = 4;
    public static final int NO_DISPLAY = 5;
    private static final int GRAB_ATTEMPTS = 40;
    private static final int GRAB_RETRY_MS = 50;
    private static final int POLL_MS = 15;

    private final LibX11 x11;
    private final String user;
    private final PasswordBuffer password = new PasswordBuffer();
    private final AtomicReference<Boolean> authResult = new AtomicReference<>();
    private volatile boolean authenticating;
    private LockScreenUi ui;

    ScreenLock(LibX11 x11, String user) {
        this.x11 = x11;
        this.user = user;
    }

    public static void main(String[] args) {
        int code = new ScreenLock(LibX11.INSTANCE, System.getProperty("user.name", "")).run();
        System.exit(code);
    }

    int run() {
        Pointer display = x11.XOpenDisplay(null);
        if (display == null) {
            LOGGER.severe("Cannot open X display");
            return NO_DISPLAY;
        }
        NativeLong root = x11.XRootWindow(display, x11.XDefaultScreen(display));
        NativeLong owner = x11.XCreateSimpleWindow(display, root, -10, -10, 1, 1, 0,
                new NativeLong(0), new NativeLong(0));
        try {
            if (!LockSelection.acquire(x11, display, owner)) {
                LOGGER.info("The screen is already locked by another process");
                return ALREADY_LOCKED;
            }
            // Grab first: from here on no other client gets input, even before the UI is drawn.
            if (!grabInput(display, root)) {
                LOGGER.severe("Could not grab keyboard and pointer; not locking");
                return GRAB_FAILED;
            }
            try {
                onEdt(() -> {
                    ui = new LockScreenUi(user);
                    ui.show();
                });
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Cannot show the lock screen", e);
                return GRAB_FAILED;
            }
            Timer raise = new Timer(500, e -> ui.raise());
            SwingUtilities.invokeLater(raise::start);
            LOGGER.info("Screen locked for " + user);
            eventLoop(display);
            SwingUtilities.invokeLater(raise::stop);
            LOGGER.info("Screen unlocked for " + user);
            return UNLOCKED;
        } finally {
            x11.XUngrabKeyboard(display, new NativeLong(LibX11.CurrentTime));
            x11.XUngrabPointer(display, new NativeLong(LibX11.CurrentTime));
            // Destroying the owner window releases the selection.
            x11.XDestroyWindow(display, owner);
            x11.XCloseDisplay(display);
            password.clear();
            if (ui != null) {
                onEdt(ui::dispose);
            }
        }
    }

    /**
     * Grabs on the root window with owner_events off, so every key and button goes to this
     * connection. Retries for a while because a client may hold a grab (e.g. an open menu).
     */
    private boolean grabInput(Pointer display, NativeLong root) {
        NativeLong now = new NativeLong(LibX11.CurrentTime);
        NativeLong none = new NativeLong(0);
        long pointerMask = LibX11.ButtonPressMask | LibX11.ButtonReleaseMask | LibX11.PointerMotionMask;
        boolean keyboard = false;
        boolean pointer = false;
        for (int attempt = 0; attempt < GRAB_ATTEMPTS && !(keyboard && pointer); attempt++) {
            if (!keyboard) {
                keyboard = x11.XGrabKeyboard(display, root, 0, LibX11.GrabModeAsync, LibX11.GrabModeAsync, now)
                        == LibX11.GrabSuccess;
            }
            if (!pointer) {
                pointer = x11.XGrabPointer(display, root, 0, new NativeLong(pointerMask),
                        LibX11.GrabModeAsync, LibX11.GrabModeAsync, none, none, now) == LibX11.GrabSuccess;
            }
            if (!(keyboard && pointer)) {
                sleep(GRAB_RETRY_MS);
            }
        }
        return keyboard && pointer;
    }

    /** Polls the connection so the unlock result from the PAM thread is noticed without X events. */
    private void eventLoop(Pointer display) {
        LibX11.XEvent event = new LibX11.XEvent();
        while (true) {
            while (x11.XPending(display) > 0) {
                x11.XNextEvent(display, event.getPointer());
                event.read();
                if (event.type == LibX11.KeyPress) {
                    handleKey(event.getPointer());
                }
            }
            Boolean result = authResult.getAndSet(null);
            if (Boolean.TRUE.equals(result)) {
                return;
            }
            if (Boolean.FALSE.equals(result)) {
                SwingUtilities.invokeLater(() -> {
                    ui.setPasswordLength(0);
                    ui.setStatus("Authentication failed. Try again.", true);
                });
            }
            sleep(POLL_MS);
        }
    }

    private void handleKey(Pointer keyEvent) {
        if (authenticating) {
            return;
        }
        byte[] buffer = new byte[32];
        NativeLongByReference keysym = new NativeLongByReference();
        int count = x11.XLookupString(keyEvent, buffer, buffer.length, keysym, null);
        long sym = keysym.getValue() == null ? 0 : keysym.getValue().longValue();
        if (sym == LibX11.XK_Return || sym == LibX11.XK_KP_Enter) {
            submit();
            return;
        }
        if (sym == LibX11.XK_BackSpace) {
            password.backspace();
        } else if (sym == LibX11.XK_Escape) {
            password.clear();
        } else if (count > 0) {
            // XLookupString returns Latin-1 for the current keyboard layout.
            password.append(new String(buffer, 0, count, StandardCharsets.ISO_8859_1));
        }
        int length = password.length();
        SwingUtilities.invokeLater(() -> {
            ui.setPasswordLength(length);
            ui.setStatus("Type your password and press Enter.", false);
        });
    }

    private void submit() {
        authenticating = true;
        String typed = password.take();
        SwingUtilities.invokeLater(() -> {
            ui.setPasswordLength(0);
            ui.setStatus("Verifying...", false);
        });
        Thread auth = new Thread(() -> {
            boolean ok;
            try {
                ok = new PamAuthenticator(PAM_SERVICE).verify(user, typed).success();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "PAM check failed", e);
                ok = false;
            }
            authResult.set(ok);
            authenticating = false;
        }, "amadeos-lock-auth");
        auth.setDaemon(true);
        auth.start();
    }

    private static void onEdt(Runnable action) {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Lock screen UI failed", e.getCause());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
