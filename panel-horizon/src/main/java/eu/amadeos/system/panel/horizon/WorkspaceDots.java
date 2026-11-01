package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.workspace.WorkspaceNames;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * The four workspaces as dots in the top bar: the current one is a wider pill in the accent colour; a click
 * switches, the tooltip names the workspace.
 */
final class WorkspaceDots extends JComponent {
    static final int COUNT = 4;
    private static final int DOT = 7;
    private static final int PILL = 18;
    private static final int GAP = 6;
    private int active;
    private int hovered = -1;

    WorkspaceDots(IntConsumer switchTo) {
        setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i >= 0) {
                    switchTo.accept(i);
                    setActive(i);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i != hovered) {
                    hovered = i;
                    setToolTipText(i < 0 ? null : name(i));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private static String name(int i) {
        return WorkspaceNames.user().name(i, TopBarTexts.format("workspace.name", i + 1));
    }

    void setActive(int index) {
        if (index != active && index >= 0 && index < COUNT) {
            active = index;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width() + 20, HorizonTopBar.BAR_HEIGHT);
    }

    private int width() {
        return PILL + (COUNT - 1) * (DOT + GAP);
    }

    private int x(int i) {
        int x = 10;
        for (int k = 0; k < i; k++) x += (k == active ? PILL : DOT) + GAP;
        return x;
    }

    private int indexAt(int px) {
        for (int i = 0; i < COUNT; i++) {
            int w = i == active ? PILL : DOT;
            if (px >= x(i) - GAP / 2 && px < x(i) + w + GAP / 2) return i;
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        BarColors c = BarColors.of(ThemeManager.currentTheme());
        int y = (getHeight() - DOT) / 2;
        for (int i = 0; i < COUNT; i++) {
            int w = i == active ? PILL : DOT;
            g2.setColor(i == active ? c.accent() : i == hovered ? c.text() : c.dim());
            g2.fillRoundRect(x(i), y, w, DOT, DOT, DOT);
        }
        g2.dispose();
    }
}
