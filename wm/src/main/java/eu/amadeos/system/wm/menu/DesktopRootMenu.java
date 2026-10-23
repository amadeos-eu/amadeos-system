package eu.amadeos.system.wm.menu;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgCategory;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;

/**
 * Authentic Motif/CDE Desktop Root Window Context Menu:
 * Triggered on Button 3 (right mouse click) on the X11 root window desktop background.
 */
public final class DesktopRootMenu {
    private static final Logger LOGGER = Logger.getLogger(DesktopRootMenu.class.getName());

    private final DesktopMenuActions actions;
    private JPopupMenu popupMenu;
    private JWindow invoker;

    public DesktopRootMenu(DesktopMenuActions actions) {
        this.actions = Objects.requireNonNull(actions, "actions must not be null");
        if (!GraphicsEnvironment.isHeadless()) {
            AmadeUI.install(ThemeManager.currentTheme());
        }
    }

    public void show(int screenX, int screenY) {
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
                    // POPUP windows are override-redirect on X11; otherwise the window manager frames this anchor.
                    invoker.setType(Window.Type.POPUP);
                    invoker.setAlwaysOnTop(true);
                    invoker.setSize(1, 1);
                }
                invoker.setLocation(screenX, screenY);
                if (!invoker.isVisible()) {
                    invoker.setVisible(true);
                }

                popupMenu = buildPopupMenu();
                popupMenu.show(invoker, 0, 0);
            } catch (Exception e) {
                LOGGER.warning("Error displaying Desktop Root Menu: " + e.getMessage());
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

    public JPopupMenu buildPopupMenu() {
        DesktopTheme theme = ThemeManager.currentTheme();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(theme.base());
        menu.setBorder(BorderFactory.createCompoundBorder(
                new AmadeUI.MotifBevelBorder(true, 2),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        // 1. Title Banner: "Workspace Menu"
        JLabel titleLabel = new JLabel(" Workspace Menu ", JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.0f));
        titleLabel.setForeground(theme.activeTitleBg());
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.shadow()),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        menu.add(titleLabel);

        // 2. Programs Submenu (Categorized XDG apps)
        JMenu programsMenu = createStyledMenu("Programs", theme);
        populateProgramsMenu(programsMenu, theme);
        menu.add(programsMenu);

        // 3. New Window Submenu
        JMenu newWindowMenu = createStyledMenu("New Window", theme);
        newWindowMenu.add(createStyledMenuItem("Terminal Emulator", "terminal", e -> launchFirst(cmd("xterm")), theme));
        newWindowMenu.add(createStyledMenuItem("Text Editor", "text-editor",
                e -> launchFirst(cmd("amade-pad"), cmd("xed"), cmd("mousepad"), cmd("gedit"), cmd("xterm", "-e", "nano")), theme));
        newWindowMenu.add(createStyledMenuItem("File Manager", "file-manager", e -> launchFirst(cmd("xdg-open", userHome())), theme));
        menu.add(newWindowMenu);

        menu.add(createSeparator(theme));

        // 4. Workspaces Submenu
        JMenu workspacesMenu = createStyledMenu("Workspaces", theme);
        int currentWs = actions.currentWorkspace();
        for (int i = 0; i < 4; i++) {
            final int wsIndex = i;
            boolean isActive = (currentWs == i);
            String prefix = isActive ? "✔ " : "   ";
            JMenuItem wsItem = createStyledMenuItem(prefix + "Workspace " + (i + 1) + ": " + actions.workspaceName(i), "workspace", e -> actions.switchWorkspace(wsIndex), theme);
            if (isActive) {
                wsItem.setFont(wsItem.getFont().deriveFont(Font.BOLD));
            }
            workspacesMenu.add(wsItem);
        }
        menu.add(workspacesMenu);

        // 5. Window Controls Submenu
        JMenu winControlsMenu = createStyledMenu("Window Management", theme);
        winControlsMenu.add(createStyledMenuItem("Shuffle Up (Cycle Top)", "window", e -> actions.shuffleUp(), theme));
        winControlsMenu.add(createStyledMenuItem("Shuffle Down (Cycle Bottom)", "window", e -> actions.shuffleDown(), theme));
        winControlsMenu.add(createSeparator(theme));
        winControlsMenu.add(createStyledMenuItem("Minimize All Windows", "window", e -> actions.minimizeAll(), theme));
        winControlsMenu.add(createStyledMenuItem("Restore All Windows", "window", e -> actions.restoreAll(), theme));
        menu.add(winControlsMenu);

        menu.add(createSeparator(theme));

        // 6. Style Manager
        menu.add(createStyledMenuItem("Style Manager...", "style", e -> actions.openSettings(), theme));

        // 7. Refresh Desktop
        menu.add(createStyledMenuItem("Refresh Desktop", "view-refresh", e -> actions.refreshDesktop(), theme));

        menu.add(createSeparator(theme));

        // 8. Lock Screen
        menu.add(createStyledMenuItem("Lock Screen", "system-lock-screen", e -> actions.lockScreen(), theme));

        // 9. Log Out...
        menu.add(createStyledMenuItem("Log Out...", "system-log-out", e -> actions.logoutSession(), theme));

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

    private void populateProgramsMenu(JMenu parent, DesktopTheme theme) {
        XdgApplicationManager xdg = XdgApplicationManager.getInstance();
        boolean hasAny = false;

        for (XdgCategory category : XdgCategory.values()) {
            List<XdgDesktopEntry> apps = xdg.getApplicationsByCategory(category);
            if (apps.isEmpty() || category == XdgCategory.OTHER) {
                continue;
            }
            hasAny = true;
            JMenu catMenu = createStyledMenu(category.displayName(), theme);
            for (XdgDesktopEntry app : apps) {
                JMenuItem appItem = new JMenuItem(app.name(), IconManager.getIcon(app.icon(), 16));
                styleMenuItem(appItem, theme);
                appItem.addActionListener(e -> {
                    try {
                        app.launch();
                    } catch (Exception ex) {
                        LOGGER.warning("Failed to launch " + app.name() + ": " + ex.getMessage());
                    }
                });
                catMenu.add(appItem);
            }
            parent.add(catMenu);
        }

        if (!hasAny) {
            parent.add(createStyledMenuItem("Terminal Emulator", "terminal", e -> launchFirst(cmd("xterm")), theme));
            parent.add(createStyledMenuItem("Text Editor", "text-editor", e -> launchFirst(cmd("amade-pad"), cmd("xed"), cmd("mousepad"), cmd("gedit")), theme));
            parent.add(createStyledMenuItem("File Manager", "file-manager", e -> launchFirst(cmd("xdg-open", userHome())), theme));
            parent.add(createStyledMenuItem("Calculator", "accessories-calculator", e -> launchFirst(cmd("gnome-calculator"), cmd("xcalc")), theme));
        }
    }

    static JMenu createStyledMenu(String text, DesktopTheme theme) {
        JMenu menu = new JMenu(text);
        menu.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        menu.setBackground(theme.base());
        menu.setForeground(theme.textActive());
        menu.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        return menu;
    }

    static JMenuItem createStyledMenuItem(String text, String iconName, java.awt.event.ActionListener action, DesktopTheme theme) {
        JMenuItem item = new JMenuItem(text, IconManager.getIcon(iconName, 16));
        styleMenuItem(item, theme);
        if (action != null) {
            item.addActionListener(action);
        }
        return item;
    }

    static void styleMenuItem(JMenuItem item, DesktopTheme theme) {
        item.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        item.setBackground(theme.base());
        item.setForeground(theme.textActive());
        item.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    }

    static JSeparator createSeparator(DesktopTheme theme) {
        JSeparator sep = new JSeparator();
        sep.setBackground(theme.shadow());
        sep.setForeground(theme.highlight());
        return sep;
    }

    /**
     * Starts the first installed candidate. Each candidate is a full argument list, so
     * arguments such as the home directory are passed as one argument, never split on spaces.
     */
    @SafeVarargs
    private static void launchFirst(List<String>... candidates) {
        try {
            if (ProcessLauncher.startFirstAvailable(List.of(candidates)).isEmpty()) {
                LOGGER.warning(() -> "None of these programs is installed: " + List.of(candidates));
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to launch process: " + e.getMessage());
        }
    }

    private static List<String> cmd(String... arguments) {
        return List.of(arguments);
    }

    private static String userHome() {
        return System.getProperty("user.home", "/");
    }
}
