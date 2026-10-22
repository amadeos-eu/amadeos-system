package eu.amadeos.system.panel.cde.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.panel.cde.PanelTexts;
import eu.amadeos.system.base.theme.IconManager;

/**
 * Authentic CDE Workstation Front Panel drawer module:
 * - Top: Graphic drawer tab with centered 3D beveled triangle (carved ▲ when closed, ▼ when open).
 * - Center: Flat launcher icon button (borderless at rest, direct on panel surface, tactile press).
 * - Modular slot bay dividers with engraved 3D horizontal and vertical grooves.
 */
public final class DrawerDockWidget extends AbstractDockWidget {
    public record DrawerTool(String title, String iconKey, ActionListener action) {}

    private final JPanel slotPanel;
    final DrawerTabButton tabBtn;
    final FlatLauncherButton mainBtn;
    private final String iconKey;
    private final String tooltip;
    private List<DrawerTool> tools;
    private ActionListener primaryAction;
    final JPopupMenu drawerPopup;
    private final boolean hasDrawer;
    private final boolean hasRightDivider;
    private boolean isDrawerOpen = false;
    private long popupClosedTime = 0;
    private boolean wasOpenWhenPressed = false;

    public DrawerDockWidget(String id, String tooltip, String iconKey, ActionListener primaryAction) {
        this(id, tooltip, iconKey, primaryAction, List.of(), false, true);
    }

    public DrawerDockWidget(String id, String tooltip, String iconKey, ActionListener primaryAction, List<DrawerTool> tools) {
        this(id, tooltip, iconKey, primaryAction, tools, tools != null && !tools.isEmpty(), true);
    }

    public DrawerDockWidget(String id, String tooltip, String iconKey, ActionListener primaryAction, List<DrawerTool> tools, boolean hasDrawer) {
        this(id, tooltip, iconKey, primaryAction, tools, hasDrawer, true);
    }

    public DrawerDockWidget(String id, String tooltip, String iconKey, ActionListener primaryAction, List<DrawerTool> tools, boolean hasDrawer, boolean hasRightDivider) {
        super(id);
        this.tooltip = tooltip;
        this.iconKey = iconKey;
        this.tools = (tools != null) ? tools : List.of();
        this.primaryAction = primaryAction;
        this.hasDrawer = hasDrawer;
        this.hasRightDivider = hasRightDivider;

        // 1. Modular CDE slot bay with etched groove lines
        slotPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                DesktopTheme t = currentTheme;
                int w = getWidth();
                int h = getHeight();

                // Raised tab segment + seamless button plate (no vertical dividers in plate)
                CdePanelChrome.paintSlotBay(g, w, h, t);
            }
        };
        slotPanel.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT));
        slotPanel.setBackground(currentTheme.base());

        // 2. Graphical drawer tab button with 3D beveled triangle
        tabBtn = new DrawerTabButton();

        // 3. Flat application launcher button directly on panel surface
        mainBtn = new FlatLauncherButton(e -> {
            if (this.primaryAction != null) this.primaryAction.actionPerformed(e);
        });

        // 4. Subpanel tools popup
        drawerPopup = new JPopupMenu();
        drawerPopup.setBorder(new MotifBevelBorder(true, 2));
        fillDrawer();

        drawerPopup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                isDrawerOpen = true;
                tabBtn.repaint();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                isDrawerOpen = false;
                popupClosedTime = System.currentTimeMillis();
                tabBtn.repaint();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                isDrawerOpen = false;
                popupClosedTime = System.currentTimeMillis();
                tabBtn.repaint();
            }
        });

        tabBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!hasDrawer) return;
                // When clicking the tab while popup is open, Swing closes the popup
                // just before delivering mousePressed to tabBtn. If closed within 350ms,
                // record that the popup was already open so the release action won't re-open it.
                wasOpenWhenPressed = (System.currentTimeMillis() - popupClosedTime < 350);
            }
        });

        tabBtn.addActionListener(e -> {
            if (!hasDrawer || this.tools.isEmpty()) return;
            if (wasOpenWhenPressed || (System.currentTimeMillis() - popupClosedTime < 350)) {
                wasOpenWhenPressed = false;
                if (drawerPopup.isVisible()) {
                    drawerPopup.setVisible(false);
                }
                return;
            }
            if (drawerPopup.isVisible()) {
                drawerPopup.setVisible(false);
            } else {
                if (tabBtn.isShowing()) {
                    drawerPopup.show(tabBtn, 0, -drawerPopup.getPreferredSize().height - 2);
                } else {
                    drawerPopup.setVisible(true);
                }
            }
        });

        slotPanel.add(tabBtn, BorderLayout.NORTH);
        slotPanel.add(mainBtn, BorderLayout.CENTER);

        updateThemeStyles(currentTheme);
    }

    /**
     * Replaces the button's action and the drawer's entries, when programs were installed or removed; on the
     * event dispatch thread. An open drawer closes first.
     */
    public void update(ActionListener primaryAction, List<DrawerTool> tools) {
        this.primaryAction = primaryAction;
        this.tools = (tools != null) ? tools : List.of();
        if (drawerPopup.isVisible()) {
            drawerPopup.setVisible(false);
        }
        drawerPopup.removeAll();
        fillDrawer();
        updateThemeStyles(currentTheme);
    }

    private void fillDrawer() {
        for (DrawerTool tool : tools) {
            JMenuItem item = new JMenuItem(tool.title());
            if (tool.iconKey() != null) {
                item.setIcon(IconManager.getIcon(tool.iconKey(), 16));
            }
            item.addActionListener(tool.action());
            drawerPopup.add(item);
        }
    }

    public boolean hasDrawer() {
        return hasDrawer;
    }

    @Override
    public JComponent component() {
        return slotPanel;
    }

    @Override
    protected void updateThemeStyles(DesktopTheme theme) {
        slotPanel.setBackground(theme.base());
        tabBtn.repaint();
        mainBtn.repaint();

        drawerPopup.setBackground(theme.base());
        for (int i = 0; i < drawerPopup.getComponentCount(); i++) {
            if (drawerPopup.getComponent(i) instanceof JMenuItem item) {
                item.setBackground(theme.base());
                item.setForeground(theme.textActive());
            }
        }
    }

    /**
     * Graphical CDE drawer tab button featuring an authentic carved 3D triangle.
     */
    final class DrawerTabButton extends JButton {
        DrawerTabButton() {
            setFocusPainted(false);
            setRolloverEnabled(hasDrawer);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.TAB_HEIGHT));
            if (hasDrawer) {
                setToolTipText(PanelTexts.format("panel.drawer.open", tooltip));
            } else {
                setToolTipText(null);
                setFocusable(false);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            DesktopTheme t = currentTheme;

            if (hasDrawer) {
                ButtonModel model = getModel();
                boolean isPressed = model.isArmed() && model.isPressed();
                boolean isHovered = model.isRollover();

                // Raised flat drawer tab, highlight left edge / shadow right edge using active theme colors
                Color tabBg = isPressed || isDrawerOpen ? t.panelFrameShadow() : (isHovered ? t.shadow() : t.panelFrameBg());
                CdePanelChrome.paintTab(g2, 0, 0, w, h, tabBg, t);

                // Draw authentic 11x6 CDE drawer triangle
                CdePanelChrome.paintDrawerTriangle(g2, w, h, isDrawerOpen, isPressed, t);
            } else {
                // Static raised flat notch tab (no triangle)
                CdePanelChrome.paintTab(g2, 0, 0, w, h, t.panelFrameBg(), t);
            }

            g2.dispose();
        }
    }

    /**
     * Flat application launcher button directly on the panel surface at rest,
     * providing tactile click feedback without bulky borders.
     */
    private final class FlatLauncherButton extends JButton {
        FlatLauncherButton(ActionListener action) {
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            setPreferredSize(new Dimension(52, 41));
            setToolTipText(tooltip);
            if (action != null) {
                addActionListener(action);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            ButtonModel model = getModel();
            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            DesktopTheme t = currentTheme;

            // In CDE, launcher icons are flat at rest without borders.
            // On hover: subtle soft highlight tint.
            // On press: tactile sunken background with 1px border bevel and 1px icon shift.
            if (isPressed) {
                g2.setColor(t.sunkenBg());
                g2.fillRect(1, 1, w - 2, h - 2);

                // 1px sunken bevel feedback while held down
                g2.setColor(t.darkShadow());
                g2.drawLine(0, 0, w - 1, 0);
                g2.drawLine(0, 0, 0, h - 1);
                g2.setColor(t.highlight());
                g2.drawLine(0, h - 1, w - 1, h - 1);
                g2.drawLine(w - 1, 0, w - 1, h - 1);
            } else if (isHovered) {
                g2.setColor(t.softHighlight());
                g2.fillRect(1, 1, w - 2, h - 2);
            }

            // Draw icon centered directly on the panel surface
            javax.swing.Icon icon = IconManager.getIcon(iconKey, 42);
            if (icon != null) {
                int ix = (w - icon.getIconWidth()) / 2;
                int iy = (h - icon.getIconHeight()) / 2;
                if (isPressed) {
                    ix += 1;
                    iy += 1;
                }
                icon.paintIcon(this, g2, ix, iy);
            }

            g2.dispose();
        }
    }
}
