package eu.amadeos.system.taskmanager.x;

import java.util.List;

/** The program windows of the desktop, read from the window manager. */
public interface Windows extends AutoCloseable {
    /** The windows in the window manager's order; an empty list without a window manager. */
    List<WindowInfo> list();

    /** The names of the workspaces, index 0 first. */
    List<String> workspaceNames();

    /** Shows the window: goes to its workspace, restores and raises it. */
    void switchTo(WindowInfo window);

    /** The window with this id, if it is still there. */
    default java.util.Optional<WindowInfo> find(long id) {
        return list().stream().filter(w -> w.id() == id).findFirst();
    }

    @Override
    void close();

    /** No X display: no windows. */
    Windows NONE = new Windows() {
        @Override
        public List<WindowInfo> list() {
            return List.of();
        }

        @Override
        public List<String> workspaceNames() {
            return List.of();
        }

        @Override
        public void switchTo(WindowInfo window) {
        }

        @Override
        public void close() {
        }
    };
}
