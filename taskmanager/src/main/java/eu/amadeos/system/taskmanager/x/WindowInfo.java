package eu.amadeos.system.taskmanager.x;

/**
 * A program window on the desktop.
 *
 * @param id            the X window id
 * @param title         the window title
 * @param pid           the process id from {@code _NET_WM_PID}, 0 if the program gave none
 * @param workspace     the workspace index (0 ..), or -1 for all workspaces
 * @param minimized     whether it is minimized
 * @param notResponding whether the window manager marked it as not responding
 */
public record WindowInfo(long id, String title, long pid, int workspace, boolean minimized, boolean notResponding) {
}
