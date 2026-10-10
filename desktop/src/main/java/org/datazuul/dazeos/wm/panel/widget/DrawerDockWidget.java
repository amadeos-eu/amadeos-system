package org.datazuul.dazeos.wm.panel.widget;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.datazuul.dazeos.wm.theme.DesktopTheme;
import org.datazuul.dazeos.wm.theme.IconManager;

public final class DrawerDockWidget extends AbstractDockWidget {
    public record DrawerTool(String title, String iconKey, ActionListener action) {}

    private final JPanel slotPanel;
    private final JButton tabBtn;
    private final JButton mainBtn;
    private final String iconKey;
    private final String tooltip;
    private final List<DrawerTool> tools;
    private final JPopupMenu drawerPopup;

    public DrawerDockWidget(String id, String tooltip, String iconKey, ActionListener primaryAction, List<DrawerTool> tools) {
        super(id);
        this.tooltip = tooltip;
        this.iconKey = iconKey;
        this.tools = (tools != null) ? tools : List.of();

        slotPanel = new JPanel(new BorderLayout(0, 1));
        slotPanel.setPreferredSize(new Dimension(50, 52));
        slotPanel.setOpaque(false);

        // Slide-up drawer tab (small triangle ▲)
        tabBtn = new JButton("▲");
        tabBtn.setPreferredSize(new Dimension(48, 10));
        tabBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 7));
        tabBtn.setMargin(new Insets(0, 0, 0, 0));
        tabBtn.setFocusPainted(false);
        tabBtn.setToolTipText("Open " + tooltip + " Drawer");

        // Primary application button
        mainBtn = new JButton();
        mainBtn.setPreferredSize(new Dimension(48, 40));
        mainBtn.setFocusPainted(false);
        mainBtn.setToolTipText(tooltip);
        if (primaryAction != null) {
            mainBtn.addActionListener(primaryAction);
        }

        // Subpanel tools popup
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

        tabBtn.addActionListener(e -> {
            if (this.tools.isEmpty()) return;
            drawerPopup.show(tabBtn, 0, -drawerPopup.getPreferredSize().height - 2);
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

        tabBtn.setBackground(theme.base());
        tabBtn.setForeground(theme.textActive());
        tabBtn.setBorder(new MotifBevelBorder(true, 1));

        mainBtn.setBackground(theme.base());
        mainBtn.setBorder(new MotifBevelBorder(true, 2));
        mainBtn.setIcon(IconManager.getIcon(iconKey, 24));

        drawerPopup.setBackground(theme.base());
        for (int i = 0; i < drawerPopup.getComponentCount(); i++) {
            if (drawerPopup.getComponent(i) instanceof JMenuItem item) {
                item.setBackground(theme.base());
                item.setForeground(theme.textActive());
            }
        }
    }
}

