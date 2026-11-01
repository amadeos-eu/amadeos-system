package eu.amadeos.system.taskmanager.x;

import eu.amadeos.system.base.x11.X11EwmhHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * {@link Windows} through the EWMH properties the amadeOS window manager keeps: {@code _NET_CLIENT_LIST},
 * {@code _NET_WM_PID}, {@code _NET_WM_DESKTOP} and {@code _AMADEOS_NOT_RESPONDING}; dialogs are left out. Uses its own X
 * connection; call it from one thread only.
 */
public final class X11Windows implements Windows {
    private static final Logger LOGGER = Logger.getLogger(X11Windows.class.getName());

    private final X11EwmhHelper x;
    private final Predicate<WindowInfo> hidden;

    private X11Windows(X11EwmhHelper x, Predicate<WindowInfo> hidden) {
        this.x = x;
        this.hidden = hidden;
    }

    /**
     * The windows of the X display in $DISPLAY, or {@link Windows#NONE} if there is none.
     *
     * @param hidden windows not to list (the Task Manager's own questions, which AWT marks as normal windows)
     */
    public static Windows open(Predicate<WindowInfo> hidden) {
        try {
            return new X11Windows(X11EwmhHelper.open(null), hidden);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            LOGGER.warning(() -> "No window list: " + e.getMessage());
            return NONE;
        }
    }

    @Override
    public List<WindowInfo> list() {
        List<WindowInfo> result = new ArrayList<>();
        for (long id : x.getClientList()) {
            // One line per program: its dialogs are not listed.
            if (x.isDialog(id)) continue;
            WindowInfo w = new WindowInfo(id, x.getWindowTitle(id), x.getWindowPid(id), x.getWindowDesktop(id),
                    x.isWindowMinimized(id), x.isNotResponding(id));
            if (!hidden.test(w)) result.add(w);
        }
        return result;
    }

    @Override
    public List<String> workspaceNames() {
        return x.getDesktopNames();
    }

    @Override
    public void switchTo(WindowInfo window) {
        if (window.workspace() >= 0 && window.workspace() != x.getCurrentDesktop()) {
            x.setCurrentDesktop(window.workspace());
        }
        x.activateWindow(window.id());
    }

    @Override
    public void close() {
        x.close();
    }
}
