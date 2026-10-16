package org.datazuul.amadeos.wm.panel.status;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.wm.panel.widget.AbstractDockWidget;
import org.datazuul.amadeos.wm.panel.widget.CdePanelChrome;

/**
 * Front panel status slot: up to four small indicators in a 2x2 grid below a plain tab, like the four
 * workspace buttons of the switcher. With fewer cells available the grid shrinks, so a single cell uses
 * the whole button plate.
 */
public final class StatusDockWidget extends AbstractDockWidget {
    static final int MAX_CELLS = 4;

    private final List<StatusCell> cells;
    private final JPanel slotPanel;
    private final JPanel grid;

    public StatusDockWidget(List<StatusCell> cells) {
        super("status");
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("at most " + MAX_CELLS + " cells");
        this.cells = List.copyOf(cells);

        slotPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                CdePanelChrome.paintSlotBay(g, getWidth(), getHeight(), currentTheme);
            }
        };
        slotPanel.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT));

        JPanel tabSpacer = new JPanel();
        tabSpacer.setOpaque(false);
        tabSpacer.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.TAB_HEIGHT + 1));
        slotPanel.add(tabSpacer, BorderLayout.NORTH);

        grid = new JPanel();
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        slotPanel.add(grid, BorderLayout.CENTER);

        relayout();
        updateThemeStyles(currentTheme);
    }

    /** Puts the available cells into the grid again, e.g. after one gained or lost its hardware. */
    public void relayout() {
        grid.removeAll();
        List<StatusCell> shown = cells.stream().filter(StatusCell::available).toList();
        int columns = shown.size() <= 1 ? 1 : 2;
        int rows = shown.size() <= 2 ? 1 : 2;
        grid.setLayout(new GridLayout(rows, columns, 2, 2));
        for (StatusCell cell : shown) grid.add(cell.component());
        grid.revalidate();
        grid.repaint();
    }

    int shownCells() {
        return grid.getComponentCount();
    }

    @Override
    public JComponent component() {
        return slotPanel;
    }

    @Override
    public void start() {
        cells.forEach(StatusCell::start);
    }

    @Override
    public void stop() {
        cells.forEach(StatusCell::stop);
    }

    @Override
    protected void updateThemeStyles(DesktopTheme theme) {
        slotPanel.setBackground(theme.base());
        for (StatusCell cell : cells) cell.onThemeChanged(theme);
    }
}
