package eu.amadeos.system.panel.cde.status;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * quarter of the button plate and the room for more indicators is visible. When more than four cells have
 * something to show (a laptop with battery, Bluetooth and waiting updates), a second slot of four opens
 * beside the first; it closes again when they fit into one.
 */
public final class StatusDockWidget extends AbstractDockWidget {
    static final int CELLS_PER_SLOT = 4;
    static final int MAX_SLOTS = 2;
    static final int MAX_CELLS = CELLS_PER_SLOT * MAX_SLOTS;

    private final List<StatusCell> cells;
    private final JPanel holder;
    private final JPanel[] slots = new JPanel[MAX_SLOTS];
    private final JPanel[] grids = new JPanel[MAX_SLOTS];
    private final Set<StatusCell> hidden = new HashSet<>();
    private int shownCells;
    private int shownSlots;

    public StatusDockWidget(List<StatusCell> cells) {
        super("status");
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("at most " + MAX_CELLS + " cells");
        this.cells = List.copyOf(cells);
        for (StatusCell cell : this.cells) cell.onAvailabilityChanged(this::relayout);

        holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.setOpaque(false);
        for (int i = 0; i < MAX_SLOTS; i++) {
            JPanel slot = new JPanel(new BorderLayout(0, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    CdePanelChrome.paintSlotBay(g, getWidth(), getHeight(), currentTheme);
                }
            };
            slot.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.SLOT_HEIGHT));

            JPanel tabSpacer = new JPanel();
            tabSpacer.setOpaque(false);
            tabSpacer.setPreferredSize(new Dimension(CdePanelChrome.SLOT_WIDTH, CdePanelChrome.TAB_HEIGHT + 1));
            slot.add(tabSpacer, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(2, 2, 2, 2));
            grid.setOpaque(false);
            grid.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            slot.add(grid, BorderLayout.CENTER);
            slots[i] = slot;
            grids[i] = grid;
        }

        relayout();
        updateThemeStyles(currentTheme);
    }

    /** Puts the available cells into the slots again, e.g. after one gained or lost its hardware. */
    public void relayout() {
        List<StatusCell> shown = cells.stream().filter(c -> c.available() && !hidden.contains(c)).toList();
        int slotCount = Math.max(1, (shown.size() + CELLS_PER_SLOT - 1) / CELLS_PER_SLOT);
        holder.removeAll();
        for (int s = 0; s < slotCount; s++) {
            grids[s].removeAll();
            for (int i = s * CELLS_PER_SLOT; i < (s + 1) * CELLS_PER_SLOT; i++) {
                grids[s].add(i < shown.size() ? shown.get(i).component() : emptyBay());
            }
            holder.add(slots[s]);
        }
        shownCells = shown.size();
        shownSlots = slotCount;
        holder.revalidate();
        holder.repaint();
    }

    /** Shows or hides a cell as the user chose in Settings > Front Panel; on the event dispatch thread. */
    public void setShown(StatusCell cell, boolean show) {
        if (show ? hidden.remove(cell) : hidden.add(cell)) relayout();
    }

    int shownCells() {
        return shownCells;
    }

    int shownSlots() {
        return shownSlots;
    }

    private static JComponent emptyBay() {
        JPanel bay = new JPanel();
        bay.setOpaque(false);
        bay.setBorder(new MotifBevelBorder(false, 1));
        return bay;
    }

    @Override
    public JComponent component() {
        return holder;
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
        for (JPanel slot : slots) slot.setBackground(theme.base());
        for (StatusCell cell : cells) cell.onThemeChanged(theme);
    }
}
