package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Dimension;

/**
 * A flat button of the top bar: text (or a painted mark) on the bar, a rounded shade under the pointer and
 * while its menu is open.
 */
class BarButton extends JButton {
    private boolean bold;
    private boolean menuOpen;

    BarButton(String text) {
        super(text);
        setFocusPainted(false);
        setFocusable(false);
        setRolloverEnabled(true);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    }

    void setBold(boolean bold) {
        this.bold = bold;
        setFont(getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
        revalidate();
    }

    /** Keeps the shade while the button's menu is open. */
    void setMenuOpen(boolean open) {
        this.menuOpen = open;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        int w = fm.stringWidth(getText() == null ? "" : getText()) + in.left + in.right + markWidth();
        return new Dimension(w, HorizonTopBar.BAR_HEIGHT);
    }

    /** Width of a mark painted before the text; none by default. */
    int markWidth() {
        return 0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        BarColors c = BarColors.of(ThemeManager.currentTheme());
        ButtonModel m = getModel();
        if (menuOpen || (m.isArmed() && m.isPressed())) {
            g2.setColor(c.pressed());
            g2.fillRoundRect(2, 3, getWidth() - 4, getHeight() - 6, 10, 10);
        } else if (m.isRollover()) {
            g2.setColor(c.hover());
            g2.fillRoundRect(2, 3, getWidth() - 4, getHeight() - 6, 10, 10);
        }
        paintMark(g2, c);
        String text = getText();
        if (text != null && !text.isEmpty()) {
            g2.setFont(getFont());
            g2.setColor(c.text());
            FontMetrics fm = g2.getFontMetrics();
            int x = getInsets().left + markWidth();
            g2.drawString(text, x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }

    /** Paints a mark before the text, such as the system menu's ring; nothing by default. */
    void paintMark(Graphics2D g, BarColors colors) {
    }
}
