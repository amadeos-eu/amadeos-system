package eu.amadeos.system.wm.menu;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;
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
 * Its texts follow the desktop language, and the program groups carry the same names and icons
 * as the Programs menu of the Horizon top bar.
 */
public final class DesktopRootMenu {
    private static final Logger LOGGER = Logger.getLogger(DesktopRootMenu.class.getName());
    public static final String BASE_NAME = "eu.amadeos.system.wm.menu.menu";

    private final DesktopMenuActions actions;
    private final Messages texts;
    private JPopupMenu popupMenu;
    private JWindow invoker;

    public DesktopRootMenu(DesktopMenuActions actions) {
        this(actions, desktopLocale());
    }

    /** A menu with its texts in the given language; the window manager uses the desktop language. */
    public DesktopRootMenu(DesktopMenuActions actions, Locale locale) {
        this.actions = Objects.requireNonNull(actions, "actions must not be null");
        this.texts = Messages.load(DesktopRootMenu.class, BASE_NAME, locale);
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
        JLabel titleLabel = new JLabel(" " + texts.get("menu.title") + " ", JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.0f));
        titleLabel.setForeground(theme.activeTitleBg());
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.shadow()),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        menu.add(titleLabel);

        // 2. Programs Submenu (Categorized XDG apps)
        JMenu programsMenu = createStyledMenu(texts.get("menu.programs"), theme);
        populateProgramsMenu(programsMenu, theme);
        menu.add(programsMenu);

        // 3. New Window Submenu
        JMenu newWindowMenu = createStyledMenu(texts.get("menu.new-window"), theme);
        newWindowMenu.add(createStyledMenuItem(texts.get("menu.terminal"), "terminal", e -> launchFirst(cmd("amade-terminal"), cmd("x-terminal-emulator"), cmd("xterm")), theme));
        newWindowMenu.add(createStyledMenuItem(texts.get("menu.text-editor"), "text-editor",
                e -> launchFirst(cmd("amade-pad"), cmd("xed"), cmd("mousepad"), cmd("gedit"), cmd("amade-terminal", "-e", "nano"), cmd("xterm", "-e", "nano")), theme));
        newWindowMenu.add(createStyledMenuItem(texts.get("menu.file-manager"), "file-manager", e -> launchFirst(cmd("xdg-open", userHome())), theme));
        menu.add(newWindowMenu);

        menu.add(createSeparator(theme));

        // 4. Workspaces Submenu
        JMenu workspacesMenu = createStyledMenu(texts.get("menu.workspaces"), theme);
        int currentWs = actions.currentWorkspace();
        for (int i = 0; i < 4; i++) {
            final int wsIndex = i;
            boolean isActive = (currentWs == i);
            String prefix = isActive ? "✔ " : "   ";
            JMenuItem wsItem = createStyledMenuItem(prefix + texts.format("menu.workspace", i + 1, actions.workspaceName(i)), "workspace", e -> actions.switchWorkspace(wsIndex), theme);
            if (isActive) {
                wsItem.setFont(wsItem.getFont().deriveFont(Font.BOLD));
            }
            workspacesMenu.add(wsItem);
        }
        menu.add(workspacesMenu);

        // 5. Window Controls Submenu
        JMenu winControlsMenu = createStyledMenu(texts.get("menu.windows"), theme);
        winControlsMenu.add(createStyledMenuItem(texts.get("menu.shuffle-up"), "window", e -> actions.shuffleUp(), theme));
        winControlsMenu.add(createStyledMenuItem(texts.get("menu.shuffle-down"), "window", e -> actions.shuffleDown(), theme));
        winControlsMenu.add(createSeparator(theme));
        winControlsMenu.add(createStyledMenuItem(texts.get(actions.showingDesktop() ? "menu.bring-back" : "menu.show-desktop"), "user-desktop", e -> actions.toggleShowDesktop(), theme));
        winControlsMenu.add(createStyledMenuItem(texts.get("menu.minimize-all"), "window", e -> actions.minimizeAll(), theme));
        winControlsMenu.add(createStyledMenuItem(texts.get("menu.restore-all"), "window", e -> actions.restoreAll(), theme));
        menu.add(winControlsMenu);

        menu.add(createSeparator(theme));

        // 6. Widgets (Horizon only: the widget host runs only in that look), then Settings
        if (actions.widgetsAvailable()) {
            menu.add(createStyledMenuItem(texts.get("menu.widgets"), "user-desktop", e -> actions.openWidgets(), theme));
        }
        menu.add(createStyledMenuItem(texts.get("menu.settings"), "style", e -> actions.openSettings(), theme));

        // 7. Refresh Desktop
        menu.add(createStyledMenuItem(texts.get("menu.refresh"), "view-refresh", e -> actions.refreshDesktop(), theme));

        menu.add(createSeparator(theme));

        // 8. Lock Screen
        menu.add(createStyledMenuItem(texts.get("menu.lock"), "system-lock-screen", e -> actions.lockScreen(), theme));

        // 9. Log Out...
        menu.add(createStyledMenuItem(texts.get("menu.logout"), "system-log-out", e -> actions.logoutSession(), theme));

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

        List<String> seen = new ArrayList<>();
        for (XdgCategory category : XdgCategory.values()) {
            // A program sits in its first group only, as in the top bar's Programs menu.
            List<XdgDesktopEntry> apps = new ArrayList<>();
            for (XdgDesktopEntry app : xdg.getApplicationsByCategory(category)) {
                if (!seen.contains(app.id())) apps.add(app);
            }
            if (apps.isEmpty()) {
                continue;
            }
            hasAny = true;
            JMenu catMenu = createStyledMenu(categoryName(category), theme);
            catMenu.setIcon(IconManager.getIcon(category.iconKey(), 16));
            for (XdgDesktopEntry app : apps) {
                seen.add(app.id());
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
            parent.add(createStyledMenuItem(texts.get("menu.terminal"), "terminal", e -> launchFirst(cmd("amade-terminal"), cmd("x-terminal-emulator"), cmd("xterm")), theme));
            parent.add(createStyledMenuItem(texts.get("menu.text-editor"), "text-editor", e -> launchFirst(cmd("amade-pad"), cmd("xed"), cmd("mousepad"), cmd("gedit")), theme));
            parent.add(createStyledMenuItem(texts.get("menu.file-manager"), "file-manager", e -> launchFirst(cmd("xdg-open", userHome())), theme));
            parent.add(createStyledMenuItem(texts.get("menu.calculator"), "accessories-calculator", e -> launchFirst(cmd("gnome-calculator"), cmd("xcalc")), theme));
        }
    }

    /** The group's name in the menu's language, as the front panel and the top bar name it. */
    String categoryName(XdgCategory category) {
        String key = "category." + category.name().toLowerCase(Locale.ROOT);
        return texts.has(key) ? texts.get(key) : category.displayName();
    }

    private static Locale desktopLocale() {
        return LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                .language().locale();
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
