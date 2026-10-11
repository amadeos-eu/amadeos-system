package org.datazuul.amadeos.wm.panel.widget;

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
import org.datazuul.amadeos.wm.theme.DesktopTheme;
import org.datazuul.amadeos.wm.theme.IconManager;

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
    private final List<DrawerTool> tools;
    final JPopupMenu drawerPopup;
    private final boolean hasRightDivider;
    private boolean isDrawerOpen = false;
    private long popupClosedTime = 0;
    private boolean wasOpenWhenPressed = false;

    public DrawerDockWidget(String id, String tooltip, String iconKey, ActionListener primaryAction, List<DrawerTool> tools) {
        this(id, tooltip, iconKey, primaryAction, tools, true);
    }

    public DrawerDockWidget(String id, String tooltip, String iconKey, ActionListener primaryAction, List<DrawerTool> tools, boolean hasRightDivider) {
        super(id);
        this.tooltip = tooltip;
        this.iconKey = iconKey;
        this.tools = (tools != null) ? tools : List.of();
        this.hasRightDivider = hasRightDivider;

        // 1. Modular CDE slot bay with etched groove lines
        slotPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                DesktopTheme t = currentTheme;
                int w = getWidth();
                int h = getHeight();

                // Horizontal groove between drawer tab and application icon area
                int grooveEnd = hasRightDivider ? (w - 2) : (w - 1);
                g.setColor(t.darkShadow());
                g.drawLine(1, 15, grooveEnd, 15);
                g.setColor(t.highlight());
                g.drawLine(1, 16, grooveEnd, 16);

                // Right slot divider groove (CDE modular bay separator)
                if (hasRightDivider) {
                    g.setColor(t.darkShadow());
                    g.drawLine(w - 2, 0, w - 2, h - 1);
                    g.setColor(t.highlight());
                    g.drawLine(w - 1, 0, w - 1, h - 1);
                }
            }
        };
        slotPanel.setPreferredSize(new Dimension(hasRightDivider ? 54 : 52, 74));
        slotPanel.setBackground(currentTheme.base());

        // 2. Graphical drawer tab button with 3D beveled triangle
        tabBtn = new DrawerTabButton();

        // 3. Flat application launcher button directly on panel surface
        mainBtn = new FlatLauncherButton(primaryAction);

        // 4. Subpanel tools popup
        drawerPopup = new JPopupMenu();
        drawerPopup.setBorder(new MotifBevelBorder(true, 2));
        for (DrawerTool tool : this.tools) {
            JMenuItem item = new JMenuItem(tool.title());
            if (tool.iconKey() != null) {
                item.setIcon(IconManager.getIcon(tool.iconKey(), 16));
            }
            item.addActionListener(tool.action());
            drawerPopup.add(item);
        }

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
                // When clicking the tab while popup is open, Swing closes the popup
                // just before delivering mousePressed to tabBtn. If closed within 350ms,
                // record that the popup was already open so the release action won't re-open it.
                wasOpenWhenPressed = (System.currentTimeMillis() - popupClosedTime < 350);
            }
        });

        tabBtn.addActionListener(e -> {
            if (this.tools.isEmpty()) return;
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
            setRolloverEnabled(true);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setPreferredSize(new Dimension(52, 15));
            setToolTipText("Open " + tooltip + " Drawer");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int usableW = hasRightDivider ? (w - 2) : w;
            int h = getHeight();
            ButtonModel model = getModel();
            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            DesktopTheme t = currentTheme;

            // Darker drawer tab handle background plate matching authentic CDE
            Color tabBg = new Color(0x99, 0x99, 0x99);
            g2.setColor(isPressed || isDrawerOpen ? t.darkShadow() : (isHovered ? t.shadow() : tabBg));
            g2.fillRect(0, 0, usableW, h);

            // Tab 3D borders
            g2.setColor(t.highlight());
            g2.drawLine(0, 0, usableW - 1, 0);
            g2.drawLine(0, 0, 0, h - 1);
            g2.setColor(t.darkShadow());
            g2.drawLine(0, h - 1, usableW - 1, h - 1);

            // Draw authentic 3D CDE drawer triangle (12x7px)
            int triW = 12;
            int triH = 7;
            int cx = usableW / 2;
            int cy = h / 2;

            if (isPressed) {
                cx += 1;
                cy += 1;
            }

            // Triangle orientation: points UP when closed, points DOWN when drawer is open
            if (!isDrawerOpen) {
                int topX = cx;
                int topY = cy - triH / 2;
                int blX = cx - triW / 2;
                int blY = cy + triH / 2;
                int brX = cx + triW / 2;
                int brY = cy + triH / 2;

                // Triangle fill
                int[] xPoints = { topX, blX, brX };
                int[] yPoints = { topY, blY, brY };
                g2.setColor(isPressed ? t.darkShadow() : (isHovered ? t.base() : t.sunkenBg()));
                g2.fillPolygon(xPoints, yPoints, 3);

                // Carved 3D beveled edges:
                // Left slope: Dark shadow (light blocks from top-left)
                // Right slope and bottom base: Highlight
                Color leftEdge = isPressed ? t.highlight() : t.darkShadow();
                Color rightEdge = isPressed ? t.darkShadow() : t.highlight();
                Color bottomEdge = isPressed ? t.darkShadow() : t.highlight();

                g2.setColor(leftEdge);
                g2.drawLine(topX, topY, blX, blY);

                g2.setColor(rightEdge);
                g2.drawLine(topX, topY, brX, brY);

                g2.setColor(bottomEdge);
                g2.drawLine(blX, blY, brX, brY);
            } else {
                // Pointing DOWN (drawer is open)
                int botX = cx;
                int botY = cy + triH / 2;
                int tlX = cx - triW / 2;
                int tlY = cy - triH / 2;
                int trX = cx + triW / 2;
                int trY = cy - triH / 2;

                int[] xPoints = { tlX, trX, botX };
                int[] yPoints = { tlY, trY, botY };
                g2.setColor(isPressed ? t.darkShadow() : (isHovered ? t.base() : t.sunkenBg()));
                g2.fillPolygon(xPoints, yPoints, 3);

                Color topEdge = isPressed ? t.highlight() : t.darkShadow();
                Color leftEdge = isPressed ? t.highlight() : t.darkShadow();
                Color rightEdge = isPressed ? t.darkShadow() : t.highlight();

                g2.setColor(topEdge);
                g2.drawLine(tlX, tlY, trX, trY);

                g2.setColor(leftEdge);
                g2.drawLine(tlX, tlY, botX, botY);

                g2.setColor(rightEdge);
                g2.drawLine(trX, trY, botX, botY);
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

            int usableW = hasRightDivider ? (getWidth() - 2) : getWidth();
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
                g2.fillRect(1, 1, usableW - 2, h - 2);

                // 1px sunken bevel feedback while held down
                g2.setColor(t.darkShadow());
                g2.drawLine(0, 0, usableW - 1, 0);
                g2.drawLine(0, 0, 0, h - 1);
                g2.setColor(t.highlight());
                g2.drawLine(0, h - 1, usableW - 1, h - 1);
                g2.drawLine(usableW - 1, 0, usableW - 1, h - 1);
            } else if (isHovered) {
                g2.setColor(t.softHighlight());
                g2.fillRect(1, 1, usableW - 2, h - 2);
            }

            // Draw icon centered directly on the panel surface
            javax.swing.Icon icon = IconManager.getIcon(iconKey, 28);
            if (icon != null) {
                int ix = (usableW - icon.getIconWidth()) / 2;
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
