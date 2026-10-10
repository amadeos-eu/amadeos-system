package org.datazuul.amadeos.wm.panel.widget;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import org.datazuul.amadeos.wm.theme.DesktopTheme;
import org.datazuul.amadeos.wm.theme.ThemeManager;

public abstract class AbstractDockWidget implements DockWidget {
    private final String id;
    protected DesktopTheme currentTheme;

    public AbstractDockWidget(String id) {
        this.id = id;
        this.currentTheme = ThemeManager.currentTheme();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void onThemeChanged(DesktopTheme theme) {
        this.currentTheme = theme;
        updateThemeStyles(theme);
        if (component() != null) {
            component().repaint();
        }
    }

    protected abstract void updateThemeStyles(DesktopTheme theme);

    /**
     * Helper border class to draw Motif 3D bevels that adapt dynamically to DesktopTheme.
     */
    public static class MotifBevelBorder extends AbstractBorder {
        private final boolean raised;
        private final int thickness;

        public MotifBevelBorder(boolean raised, int thickness) {
            this.raised = raised;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            org.datazuul.amadeos.wm.theme.AmadeUI.paintBevel(g, x, y, width, height, raised, thickness);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.top = insets.right = insets.bottom = thickness;
            return insets;
        }
    }
}

