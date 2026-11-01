package eu.amadeos.system.wm.menu;

/**
 * Callbacks invoked by the Motif Desktop Root Menu for workspace and window management.
 */
public interface DesktopMenuActions {
    void switchWorkspace(int workspaceIndex);

    int currentWorkspace();

    /** The name of workspace {@code workspaceIndex} (0 to 3), as the user named it in the Front Panel. */
    default String workspaceName(int workspaceIndex) {
        return new String[]{"One", "Two", "Three", "Four"}[workspaceIndex];
    }

    void shuffleUp();

    void shuffleDown();

    void minimizeAll();

    void restoreAll();

    /** Shows the desktop, or brings the windows back when it is shown (Super+D). */
    default void toggleShowDesktop() {
    }

    /** True while the desktop is shown. */
    default boolean showingDesktop() {
        return false;
    }

    void refreshDesktop();

    void openSettings();

    void lockScreen();

    void logoutSession();
}

