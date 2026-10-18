package eu.amadeos.wm.menu;

/**
 * Callbacks invoked by the Motif Desktop Root Menu for workspace and window management.
 */
public interface DesktopMenuActions {
    void switchWorkspace(int workspaceIndex);

    int currentWorkspace();

    void shuffleUp();

    void shuffleDown();

    void minimizeAll();

    void restoreAll();

    void refreshDesktop();

    void openSettings();

    void lockScreen();

    void logoutSession();
}

