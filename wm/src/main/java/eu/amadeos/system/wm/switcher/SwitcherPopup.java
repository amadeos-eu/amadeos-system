package eu.amadeos.system.wm.switcher;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * The Alt+Tab popup: one tile per window with its preview, its icon and its title, the selected window
 * highlighted and its full title along the bottom. It only shows what the window manager tells it; keys stay
 * with the window manager, which holds the keyboard while the popup is open. A click on a tile picks that
 * window, pointing at one selects it.
 */
public final class SwitcherPopup {
    private static final Logger LOGGER = Logger.getLogger(SwitcherPopup.class.getName());

    /** What the mouse does; called on the Swing EDT, so implementations hand over to the X event thread. */
    public interface Actions {
        void select(int index);

        void pick(int index);
    }

    static final int PREVIEW_WIDTH = 196;
    static final int PREVIEW_HEIGHT = 132;
    private static final int ICON_SIZE = 32;
    private static final int PAD = 14;
    private static final int GAP = 8;
    private static final int CELL_INSET = 10;
    private static final int SCREEN_MARGIN = 40;

    private final Actions actions;
    private JWindow window;
    private Tiles tiles;

    public SwitcherPopup(Actions actions) {
        this.actions = actions;
    }

    /** Opens the popup in the middle of the screen, or shows new contents if it is open already. */
    public void show(List<SwitcherEntry> entries, int selected) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        List<SwitcherEntry> copy = List.copyOf(entries);
        SwingUtilities.invokeLater(() -> {
            try {
                if (window == null) {
                    window = new JWindow();
                    // POPUP windows are override-redirect on X11: the window manager neither frames nor
                    // focuses the switcher.
                    window.setType(Window.Type.POPUP);
                    window.setAlwaysOnTop(true);
                    window.setFocusableWindowState(false);
                    tiles = new Tiles();
                    window.setContentPane(tiles);
                }
                tiles.setEntries(copy, selected, ThemeManager.currentTheme());
                window.pack();
                Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                window.setLocation(screen.x + (screen.width - window.getWidth()) / 2,
                        screen.y + (screen.height - window.getHeight()) / 2);
                if (!window.isVisible()) {
                    window.setVisible(true);
                }
                window.toFront();
            } catch (Exception e) {
                LOGGER.warning("Cannot show the window switcher: " + e.getMessage());
            }
        });
    }

    /** Moves the highlight. */
    public void select(int index) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (tiles != null) tiles.setSelected(index);
        });
    }

    public void hide() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (window != null && window.isVisible()) {
                window.setVisible(false);
            }
            if (tiles != null) tiles.clear();
        });
    }

    public void dispose() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                window.dispose();
                window = null;
                tiles = null;
            }
        });
    }

    /** How many tiles go in a row so that the popup fits on a screen {@code screenWidth} wide. */
    static int columns(int count, int screenWidth) {
        int cell = PREVIEW_WIDTH + 2 * CELL_INSET;
        int fit = (screenWidth - 2 * SCREEN_MARGIN - 2 * PAD + GAP) / (cell + GAP);
        return Math.max(1, Math.min(count, fit));
    }

    private final class Tiles extends JComponent {
        private List<SwitcherEntry> entries = List.of();
        private int selected;
        private DesktopTheme theme = ThemeManager.currentTheme();
        private int columns = 1;
        private final Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(AmadeUI.scaled(12)));
        private final Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(AmadeUI.scaled(13)));

        Tiles() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int index = tileAt(e.getX(), e.getY());
                    if (index >= 0 && index != selected) {
                        setSelected(index);
                        actions.select(index);
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int index = tileAt(e.getX(), e.getY());
                    if (index >= 0) actions.pick(index);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setEntries(List<SwitcherEntry> entries, int selected, DesktopTheme theme) {
            this.entries = entries;
            this.selected = selected;
            this.theme = theme;
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            this.columns = columns(entries.size(), screen.width);
            revalidate();
            repaint();
        }

        void setSelected(int index) {
            if (index >= 0 && index < entries.size()) {
                selected = index;
                repaint();
            }
        }

        /** Lets go of the previews once the popup is closed. */
        void clear() {
            entries = List.of();
        }

        private int cellWidth() {
            return PREVIEW_WIDTH + 2 * CELL_INSET;
        }

        private int labelHeight() {
            return getFontMetrics(labelFont).getHeight() + 10;
        }

        private int cellHeight() {
            return PREVIEW_HEIGHT + 2 * CELL_INSET + labelHeight();
        }

        private int titleHeight() {
            return getFontMetrics(titleFont).getHeight() + 8;
        }

        private int rows() {
            return Math.max(1, (entries.size() + columns - 1) / columns);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = 2 * PAD + columns * cellWidth() + (columns - 1) * GAP;
            int h = 2 * PAD + rows() * cellHeight() + (rows() - 1) * GAP + GAP + titleHeight();
            return new Dimension(w, h);
        }

        private Rectangle cell(int index) {
            int col = index % columns;
            int row = index / columns;
            return new Rectangle(PAD + col * (cellWidth() + GAP), PAD + row * (cellHeight() + GAP),
                    cellWidth(), cellHeight());
        }

        private int tileAt(int x, int y) {
            for (int i = 0; i < entries.size(); i++) {
                if (cell(i).contains(x, y)) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setColor(theme.base());
                g.fillRect(0, 0, getWidth(), getHeight());
                AmadeUI.paintBevel(g, 0, 0, getWidth(), getHeight(), true, 2);
                AmadeUI.paintBevel(g, 4, 4, getWidth() - 8, getHeight() - 8, false, 1);
                for (int i = 0; i < entries.size(); i++) {
                    paintTile(g, entries.get(i), cell(i), i == selected);
                }
                paintTitle(g);
            } finally {
                g.dispose();
            }
        }

        private void paintTile(Graphics2D g, SwitcherEntry entry, Rectangle cell, boolean isSelected) {
            if (isSelected) {
                g.setColor(theme.selectionBackground());
                g.fillRect(cell.x, cell.y, cell.width, cell.height);
                AmadeUI.paintBevel(g, cell.x, cell.y, cell.width, cell.height, false, 2);
            }
            Rectangle box = new Rectangle(cell.x + CELL_INSET, cell.y + CELL_INSET, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            Image preview = entry.preview();
            if (preview != null && preview.getWidth(null) > 0) {
                double scale = Math.min((double) box.width / preview.getWidth(null),
                        (double) box.height / preview.getHeight(null));
                scale = Math.min(scale, 1.0);
                int w = (int) Math.round(preview.getWidth(null) * scale);
                int h = (int) Math.round(preview.getHeight(null) * scale);
                int x = box.x + (box.width - w) / 2;
                int y = box.y + (box.height - h) / 2;
                g.setColor(theme.darkShadow());
                g.fillRect(x - 2, y - 2, w + 4, h + 4);
                g.drawImage(preview, x, y, w, h, null);
                if (entry.minimized()) {
                    // Minimized windows show their last picture, faded towards the panel colour.
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                    g.setColor(theme.base());
                    g.fillRect(x, y, w, h);
                    g.setComposite(AlphaComposite.SrcOver);
                }
            } else {
                // Never seen whole: a sunken plate with the application's icon, large.
                int w = box.width * 3 / 4;
                int h = box.height * 3 / 4;
                int x = box.x + (box.width - w) / 2;
                int y = box.y + (box.height - h) / 2;
                g.setColor(theme.sunkenBg());
                g.fillRect(x, y, w, h);
                AmadeUI.paintBevel(g, x, y, w, h, false, 2);
                paintIcon(g, entry, x + w / 2 - 32, y + h / 2 - 32, 64);
            }

            // The application's icon sits on the lower left corner of the preview.
            int ix = cell.x + 8;
            int iy = box.y + box.height - ICON_SIZE + 10;
            g.setColor(theme.base());
            g.fillRect(ix - 2, iy - 2, ICON_SIZE + 4, ICON_SIZE + 4);
            AmadeUI.paintBevel(g, ix - 2, iy - 2, ICON_SIZE + 4, ICON_SIZE + 4, true, 1);
            paintIcon(g, entry, ix, iy, ICON_SIZE);

            g.setFont(labelFont);
            FontMetrics fm = g.getFontMetrics();
            String label = fit(entry.title(), fm, cell.width - 8);
            g.setColor(isSelected ? theme.selectionText() : theme.textActive());
            g.drawString(label, cell.x + (cell.width - fm.stringWidth(label)) / 2,
                    box.y + box.height + 8 + (labelHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }

        private void paintIcon(Graphics2D g, SwitcherEntry entry, int x, int y, int size) {
            if (entry.icon() != null) {
                g.drawImage(entry.icon(), x, y, size, size, null);
                return;
            }
            Icon generic = IconManager.getIcon("window", size);
            if (generic != null) {
                generic.paintIcon(this, g, x + (size - generic.getIconWidth()) / 2,
                        y + (size - generic.getIconHeight()) / 2);
            }
        }

        private void paintTitle(Graphics2D g) {
            int y = getHeight() - PAD - titleHeight();
            int x = PAD;
            int w = getWidth() - 2 * PAD;
            g.setColor(theme.fieldBackground());
            g.fillRect(x, y, w, titleHeight());
            AmadeUI.paintBevel(g, x, y, w, titleHeight(), false, 1);
            if (entries.isEmpty()) return;
            g.setFont(titleFont);
            FontMetrics fm = g.getFontMetrics();
            String title = entries.get(selected).title();
            if (entries.get(selected).minimized()) title += " (minimized)";
            title = fit(title, fm, w - 12);
            g.setColor(theme.fieldText());
            g.drawString(title, x + (w - fm.stringWidth(title)) / 2,
                    y + (titleHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }

        private static String fit(String text, FontMetrics fm, int width) {
            String s = text == null || text.isBlank() ? "Window" : text;
            if (fm.stringWidth(s) <= width) return s;
            while (s.length() > 1 && fm.stringWidth(s + "…") > width) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "…";
        }
    }
}
