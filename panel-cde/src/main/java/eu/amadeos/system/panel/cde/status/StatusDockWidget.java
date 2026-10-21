package eu.amadeos.system.panel.cde.status;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.panel.cde.widget.AbstractDockWidget;
import eu.amadeos.system.panel.cde.widget.AbstractDockWidget.MotifBevelBorder;
import eu.amadeos.system.panel.cde.widget.CdePanelChrome;

/**
 * Front panel status slot: up to four small indicators in a 2x2 grid below a plain tab, like the four
 * workspace buttons of the switcher. Free places stay as sunken empty bays, so a single cell takes a
 * quarter of the button plate and the room for more indicators is visible.
 */
public final class StatusDockWidget extends AbstractDockWidget {
    static final int MAX_CELLS = 4;

    private final List<StatusCell> cells;
    private final JPanel slotPanel;
    private final JPanel grid;
    private int shownCells;

    public StatusDockWidget(List<StatusCell> cells) {
        super("status");
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("at most " + MAX_CELLS + " cells");
        this.cells = List.copyOf(cells);
        for (StatusCell cell : this.cells) cell.onAvailabilityChanged(this::relayout);

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
        grid.setLayout(new GridLayout(2, 2, 2, 2));
        for (StatusCell cell : shown) grid.add(cell.component());
        for (int i = shown.size(); i < MAX_CELLS; i++) grid.add(emptyBay());
        shownCells = shown.size();
        grid.revalidate();
        grid.repaint();
    }

    int shownCells() {
        return shownCells;
    }

    private static JComponent emptyBay() {
        JPanel bay = new JPanel();
        bay.setOpaque(false);
        bay.setBorder(new MotifBevelBorder(false, 1));
        return bay;
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
