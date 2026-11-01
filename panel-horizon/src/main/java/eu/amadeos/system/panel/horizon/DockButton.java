package eu.amadeos.system.panel.horizon;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One icon in the dock: a program, a drawer, a folder or the Trash. A soft square shows under the mouse; below
 * the icon a dot in the accent colour shows a running program (a hollow dot when all its windows are minimised),
 * and a small triangle marks a drawer. The name is the tooltip.
 */
class DockButton extends JComponent {
    enum Mark { NONE, RUNNING, MINIMIZED, DRAWER }

    private final Supplier<BarColors> colors;
    private Icon icon;
    private int iconSize;
    private Mark mark = Mark.NONE;
    private boolean active;
    private boolean hover;
    private boolean pressed;

    DockButton(Icon icon, int iconSize, String name, Supplier<BarColors> colors, Consumer<MouseEvent> click) {
        this.icon = icon;
        this.iconSize = iconSize;
        this.colors = colors;
        setToolTipText(name);
        setOpaque(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean inside = contains(e.getPoint());
                pressed = false;
                repaint();
                if (inside) click.accept(e);
            }
        });
    }

    void setMark(Mark mark, boolean active) {
        if (this.mark != mark || this.active != active) {
            this.mark = mark;
            this.active = active;
            repaint();
        }
    }

    void setIcon(Icon icon) {
        this.icon = icon;
        repaint();
    }

    /** The space around an icon: a quarter of it at the sides, room for the dot below. */
    static int cellWidth(int iconSize) {
        return iconSize + iconSize / 4;
    }

    static int cellHeight(int iconSize) {
        return iconSize + iconSize / 4 + 6;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(cellWidth(iconSize), cellHeight(iconSize));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        BarColors c = colors.get();
        int pad = iconSize / 8;
        int boxX = (getWidth() - iconSize) / 2 - pad;
        int boxY = pad / 2 + 1;
        int box = iconSize + 2 * pad;
        if (hover || pressed || active) {
            Color shade = pressed ? c.pressed() : hover ? c.hover() : BarColors.mix(c.bar(), c.text(), 0.08);
            g.setColor(shade);
            g.fillRoundRect(boxX, boxY, box, box, box / 3, box / 3);
        }
        if (icon != null) {
            int x = (getWidth() - icon.getIconWidth()) / 2;
            int y = boxY + pad + (iconSize - icon.getIconHeight()) / 2 + (pressed ? 1 : 0);
            icon.paintIcon(this, g, x, y);
        }
        int cx = getWidth() / 2;
        int cy = getHeight() - 5;
        switch (mark) {
            case RUNNING -> {
                g.setColor(c.accent());
                g.fillOval(cx - 2, cy - 2, 5, 5);
            }
            case MINIMIZED -> {
                g.setColor(c.accent());
                g.drawOval(cx - 2, cy - 2, 4, 4);
            }
            case DRAWER -> {
                g.setColor(c.dim());
                g.fillPolygon(new int[]{cx - 3, cx + 4, cx}, new int[]{cy + 1, cy + 1, cy - 3}, 3);
            }
            case NONE -> {
            }
        }
        g.dispose();
    }
}
