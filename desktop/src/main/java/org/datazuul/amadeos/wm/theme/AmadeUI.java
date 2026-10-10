package org.datazuul.amadeos.wm.theme;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;

/**
 * Installs central Motif 3D styling and theme colors across Swing controls,
 * replacing default Metal/Ocean blueish buttons with authentic 3D chiseled bevels.
 */
public final class AmadeUI {

    private AmadeUI() {}

    /**
     * Installs or updates the amadeOS central Motif 3D look-and-feel in UIManager
     * using the current DesktopTheme.
     */
    public static void install() {
        install(ThemeManager.currentTheme());
    }

    /**
     * Installs or updates the amadeOS central Motif 3D look-and-feel in UIManager
     * matching the given DesktopTheme palette.
     */
    public static void install(DesktopTheme theme) {
        if (theme == null) {
            theme = DesktopTheme.WARM_MOTIF;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        Color base = theme.base();
        Color highlight = theme.highlight();
        Color softHighlight = theme.softHighlight();
        Color shadow = theme.shadow();
        Color darkShadow = theme.darkShadow();
        Color sunken = theme.sunkenBg();
        Color text = theme.textActive();

        // Control Palette
        UIManager.put("control", new ColorUIResource(base));
        UIManager.put("controlHighlight", new ColorUIResource(highlight));
        UIManager.put("controlLtHighlight", new ColorUIResource(highlight));
        UIManager.put("controlShadow", new ColorUIResource(shadow));
        UIManager.put("controlDkShadow", new ColorUIResource(darkShadow));
        UIManager.put("controlText", new ColorUIResource(text));

        // Panels & Dialogs
        UIManager.put("Panel.background", new ColorUIResource(base));
        UIManager.put("OptionPane.background", new ColorUIResource(base));
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(text));

        // 3D Motif Buttons
        UIManager.put("ButtonUI", AmadeButtonUI.class.getName());
        UIManager.put("Button.background", new ColorUIResource(base));
        UIManager.put("Button.foreground", new ColorUIResource(text));
        UIManager.put("Button.select", new ColorUIResource(sunken));
        UIManager.put("Button.focus", new ColorUIResource(shadow));
        UIManager.put("Button.border", new MotifButtonBorder(2));

        // Toggle Buttons
        UIManager.put("ToggleButtonUI", AmadeToggleButtonUI.class.getName());
        UIManager.put("ToggleButton.background", new ColorUIResource(base));
        UIManager.put("ToggleButton.foreground", new ColorUIResource(text));
        UIManager.put("ToggleButton.select", new ColorUIResource(sunken));
        UIManager.put("ToggleButton.border", new MotifButtonBorder(2));

        // Menus
        UIManager.put("MenuBar.background", new ColorUIResource(base));
        UIManager.put("MenuBar.border", new MotifBevelBorder(true, 2));
        UIManager.put("Menu.background", new ColorUIResource(base));
        UIManager.put("Menu.foreground", new ColorUIResource(text));
        UIManager.put("Menu.selectionBackground", new ColorUIResource(softHighlight));
        UIManager.put("Menu.selectionForeground", new ColorUIResource(text));
        UIManager.put("MenuItem.background", new ColorUIResource(base));
        UIManager.put("MenuItem.foreground", new ColorUIResource(text));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(softHighlight));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(text));
        UIManager.put("PopupMenu.background", new ColorUIResource(base));
        UIManager.put("PopupMenu.border", new MotifBevelBorder(true, 2));

        // Text Fields & Areas
        UIManager.put("TextField.background", new ColorUIResource(Color.WHITE));
        UIManager.put("TextField.foreground", new ColorUIResource(text));
        UIManager.put("TextField.selectionBackground", new ColorUIResource(theme.activeTitleBg()));
        UIManager.put("TextField.selectionForeground", new ColorUIResource(theme.isDark(theme.activeTitleBg()) ? Color.WHITE : Color.BLACK));
        UIManager.put("TextField.border", new MotifBevelBorder(false, 2));

        // ScrollPanes & ScrollBars
        UIManager.put("ScrollPane.background", new ColorUIResource(base));
        UIManager.put("ScrollPane.border", new MotifBevelBorder(false, 2));
        UIManager.put("ScrollBar.background", new ColorUIResource(sunken));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(base));
        UIManager.put("ScrollBar.track", new ColorUIResource(sunken));

        // TabbedPane
        UIManager.put("TabbedPane.background", new ColorUIResource(base));
        UIManager.put("TabbedPane.foreground", new ColorUIResource(text));
        UIManager.put("TabbedPane.selected", new ColorUIResource(softHighlight));
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(2, 2, 2, 2));
    }

    /**
     * Helper to paint standard 3D beveled borders using theme colors.
     */
    public static void paintBevel(Graphics g, int x, int y, int width, int height, boolean raised, int thickness) {
        DesktopTheme t = ThemeManager.currentTheme();
        Graphics2D g2 = (Graphics2D) g;

        Color tl1 = raised ? t.highlight() : t.darkShadow();
        Color tl2 = raised ? t.softHighlight() : t.shadow();
        Color br1 = raised ? t.darkShadow() : t.highlight();
        Color br2 = raised ? t.shadow() : t.softHighlight();

        for (int i = 0; i < thickness; i++) {
            Color tl = (i == 0) ? tl1 : tl2;
            Color br = (i == 0) ? br1 : br2;

            g2.setColor(tl);
            g2.drawLine(x + i, y + i, x + width - 1 - i, y + i);
            g2.drawLine(x + i, y + i, x + i, y + height - 1 - i);

            g2.setColor(br);
            g2.drawLine(x + i, y + height - 1 - i, x + width - 1 - i, y + height - 1 - i);
            g2.drawLine(x + width - 1 - i, y + i, x + width - 1 - i, y + height - 1 - i);
        }
    }

    /**
     * Motif 3D Bevel Border drawing 3D chiseled workstation borders.
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
            paintBevel(g, x, y, width, height, raised, thickness);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            if (c instanceof javax.swing.JScrollPane) {
                return new Insets(thickness, thickness, thickness, thickness);
            }
            return new Insets(thickness + 3, thickness + 8, thickness + 3, thickness + 8);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets ins = getBorderInsets(c);
            insets.top = ins.top;
            insets.left = ins.left;
            insets.bottom = ins.bottom;
            insets.right = ins.right;
            return insets;
        }
    }

    /**
     * Dynamic Motif 3D Button Border that reflects the pressed/selected state of the button
     * without mutating component borders or triggering repaint/layout thrashing during paint().
     */
    public static class MotifButtonBorder extends AbstractBorder {
        private final int thickness;

        public MotifButtonBorder(int thickness) {
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            boolean isSunken = false;
            if (c instanceof AbstractButton b) {
                ButtonModel model = b.getModel();
                if (model.isArmed() && model.isPressed()) {
                    isSunken = true;
                } else if (model.isSelected()) {
                    isSunken = true;
                }
            }
            paintBevel(g, x, y, width, height, !isSunken, thickness);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 3, thickness + 8, thickness + 3, thickness + 8);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.top = thickness + 3;
            insets.left = thickness + 8;
            insets.bottom = thickness + 3;
            insets.right = thickness + 8;
            return insets;
        }
    }

    /**
     * Authentic 3D Motif ButtonUI matching workstation window buttons.
     */
    public static class AmadeButtonUI extends BasicButtonUI {
        private static final AmadeButtonUI INSTANCE = new AmadeButtonUI();

        public static javax.swing.plaf.ComponentUI createUI(JComponent c) {
            return INSTANCE;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            DesktopTheme t = ThemeManager.currentTheme();
            b.setBackground(t.base());
            b.setForeground(t.textActive());
            b.setBorder(new MotifButtonBorder(2));
            b.setFont(b.getFont().deriveFont(Font.BOLD, 12.0f));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();
            DesktopTheme t = ThemeManager.currentTheme();
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            Color bg;
            if (isPressed) {
                bg = t.sunkenBg();
            } else if (isHovered) {
                bg = t.softHighlight();
            } else {
                bg = t.base();
            }

            g2.setColor(bg);
            g2.fillRect(0, 0, c.getWidth(), c.getHeight());

            if (isPressed) {
                g2.translate(1, 1);
            }

            super.paint(g2, c);

            if (isPressed) {
                g2.translate(-1, -1);
            }
        }
    }

    /**
     * Authentic 3D Motif ToggleButtonUI (sunken when selected).
     */
    public static class AmadeToggleButtonUI extends BasicToggleButtonUI {
        private static final AmadeToggleButtonUI INSTANCE = new AmadeToggleButtonUI();

        public static javax.swing.plaf.ComponentUI createUI(JComponent c) {
            return INSTANCE;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            DesktopTheme t = ThemeManager.currentTheme();
            b.setBackground(t.base());
            b.setForeground(t.textActive());
            b.setBorder(new MotifButtonBorder(2));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();
            DesktopTheme t = ThemeManager.currentTheme();
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean isSelected = model.isSelected();
            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            Color bg;
            if (isPressed || isSelected) {
                bg = t.sunkenBg();
            } else if (isHovered) {
                bg = t.softHighlight();
            } else {
                bg = t.base();
            }

            g2.setColor(bg);
            g2.fillRect(0, 0, c.getWidth(), c.getHeight());

            if (isPressed || isSelected) {
                g2.translate(1, 1);
            }

            super.paint(g2, c);

            if (isPressed || isSelected) {
                g2.translate(-1, -1);
            }
        }
    }
}

