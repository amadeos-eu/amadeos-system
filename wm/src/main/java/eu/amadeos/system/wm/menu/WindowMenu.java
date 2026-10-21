package eu.amadeos.system.wm.menu;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * CDE's window menu, opened by the button at the left end of a title bar: Restore, Minimize, Maximize,
 * Lower and Close for that one window. A double click on the button closes the window, as in dtwm.
 */
public final class WindowMenu {
    private static final Logger LOGGER = Logger.getLogger(WindowMenu.class.getName());

    /** What the entries do; called on the Swing EDT, so implementations hand over to the X event thread. */
    public interface Actions {
        void restore(long clientWin);

        void minimize(long clientWin);

        void maximize(long clientWin);

        void lower(long clientWin);

        void close(long clientWin);
    }

    private final Actions actions;
    private JPopupMenu popupMenu;
    private JWindow invoker;

    public WindowMenu(Actions actions) {
        this.actions = actions;
    }

    /** Shows the menu for the window at the screen position (below the menu button). */
    public void show(long clientWin, boolean maximized, int screenX, int screenY) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                if (popupMenu != null && popupMenu.isVisible()) {
                    popupMenu.setVisible(false);
                }
                if (invoker == null) {
                    invoker = new JWindow();
                    // POPUP windows are override-redirect on X11, so this anchor gets no frame.
                    invoker.setType(Window.Type.POPUP);
                    invoker.setAlwaysOnTop(true);
                    invoker.setSize(1, 1);
                }
                invoker.setLocation(screenX, screenY);
                if (!invoker.isVisible()) {
                    invoker.setVisible(true);
                }
                popupMenu = buildPopupMenu(clientWin, maximized);
                popupMenu.show(invoker, 0, 0);
            } catch (Exception e) {
                LOGGER.warning("Error displaying the window menu: " + e.getMessage());
            }
        });
    }

    public void hide() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (popupMenu != null && popupMenu.isVisible()) {
                popupMenu.setVisible(false);
            }
            if (invoker != null && invoker.isVisible()) {
                invoker.setVisible(false);
            }
        });
    }

    public void dispose() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (invoker != null) {
                invoker.dispose();
                invoker = null;
            }
        });
    }

    /** The entries; Restore only for a maximised window, Maximize only for one that is not. */
    public JPopupMenu buildPopupMenu(long clientWin, boolean maximized) {
        DesktopTheme theme = ThemeManager.currentTheme();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(theme.base());
        menu.setBorder(BorderFactory.createCompoundBorder(
                new AmadeUI.MotifBevelBorder(true, 2),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        JMenuItem restore = DesktopRootMenu.createStyledMenuItem("Restore", "window", e -> actions.restore(clientWin), theme);
        restore.setEnabled(maximized);
        menu.add(restore);
        menu.add(DesktopRootMenu.createStyledMenuItem("Minimize", "window", e -> actions.minimize(clientWin), theme));
        JMenuItem maximize = DesktopRootMenu.createStyledMenuItem("Maximize", "window", e -> actions.maximize(clientWin), theme);
        maximize.setEnabled(!maximized);
        menu.add(maximize);
        menu.add(DesktopRootMenu.createStyledMenuItem("Lower", "window", e -> actions.lower(clientWin), theme));
        menu.add(DesktopRootMenu.createSeparator(theme));
        menu.add(DesktopRootMenu.createStyledMenuItem("Close", "window-close", e -> actions.close(clientWin), theme));

        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (invoker != null && invoker.isVisible()) {
                    invoker.setVisible(false);
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                if (invoker != null && invoker.isVisible()) {
                    invoker.setVisible(false);
                }
            }
        });
        return menu;
    }
}
