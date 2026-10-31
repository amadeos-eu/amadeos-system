package eu.amadeos.system.wm.hang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which windows answer the window manager. The window manager sends {@code _NET_WM_PING} to a window when
 * its Close button is pressed and, every few seconds, to the active window; a program whose event loop runs
 * answers at once. A window that has not answered for {@link #TIMEOUT_MILLIS} counts as <i>not
 * responding</i> until its answer arrives. Not thread-safe: used on the window manager's event thread.
 */
public final class PingTracker {
    /** How long a window may take to answer, as in other desktops. */
    public static final long TIMEOUT_MILLIS = 5000;

    private final long timeoutMillis;
    private final Map<Long, Long> waitingSince = new HashMap<>();
    private final Set<Long> notResponding = new HashSet<>();

    public PingTracker() {
        this(TIMEOUT_MILLIS);
    }

    public PingTracker(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /** True if a ping to the window should go out now: none is waiting for an answer. */
    public boolean shouldPing(long window) {
        return !waitingSince.containsKey(window);
    }

    /** A ping went out to the window at {@code now}. */
    public void sent(long window, long now) {
        waitingSince.putIfAbsent(window, now);
    }

    /**
     * The window answered. Returns true if it counted as not responding, so the caller takes the mark
     * away again.
     */
    public boolean answered(long window) {
        waitingSince.remove(window);
        return notResponding.remove(window);
    }

    /** The windows that have just passed the time limit; each is reported once until it answers. */
    public List<Long> newlyNotResponding(long now) {
        List<Long> result = new ArrayList<>();
        waitingSince.forEach((window, since) -> {
            if (now - since >= timeoutMillis && notResponding.add(window)) {
                result.add(window);
            }
        });
        result.sort(null);
        return result;
    }

    public boolean isNotResponding(long window) {
        return notResponding.contains(window);
    }

    /** The window is gone. */
    public void forget(long window) {
        waitingSince.remove(window);
        notResponding.remove(window);
    }
}
