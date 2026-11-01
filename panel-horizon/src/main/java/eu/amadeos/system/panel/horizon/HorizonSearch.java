package eu.amadeos.system.panel.horizon;

import com.sun.jna.Native;
import eu.amadeos.system.base.process.InstanceChannel;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.xdg.XdgApplicationManager;
import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import eu.amadeos.system.panel.cde.PanelTexts;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Horizon's search: a field in the upper middle of the screen, opened from the magnifier in the top bar or with
 * Super+Space (the window manager sends {@code open} over the {@link InstanceChannel#SEARCH} socket). As one
 * types it lists the result of a sum, the programs, the pages of Settings and the files in the home folder that
 * match. The arrow keys choose, Enter opens (a sum's result goes to the clipboard), Esc or a click elsewhere
 * closes it.
 */
final class HorizonSearch extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(HorizonSearch.class.getName());
    static final String WINDOW_TITLE = "amadeOS Horizon Search";
    static final int WIDTH = 640;
    static final int FIELD_HEIGHT = 56;
    static final int ROW_HEIGHT = 40;
    static final int HEADING_HEIGHT = 24;
    private static final int ARC = 18;

    private final X11EwmhHelper helper;
    private final Path home;
    private final JTextField field = new JTextField() {
        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (!getText().isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setColor(colors().dim());
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(TopBarTexts.get("search.hint"), getInsets().left + 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    };
    private final Results results = new Results();
    private final AtomicInteger indexGeneration = new AtomicInteger();
    private volatile List<Path> fileIndex = List.of();
    private List<XdgDesktopEntry> entries = List.of();
    private List<SearchIndex.Hit> hits = List.of();
    private int selected;
    private long openedAt;
    /** The window that had the keyboard before; it gets it back when Esc closes the search. */
    private long previous;

    HorizonSearch(X11EwmhHelper helper, Path home) {
        super(WINDOW_TITLE);
        this.helper = helper;
        this.home = home;
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setAlwaysOnTop(true);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                BarColors c = colors();
                g.setColor(c.bar());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(BarColors.mix(c.bar(), c.text(), 0.18));
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, ARC, ARC));
                if (!hits.isEmpty()) g.fillRect(14, FIELD_HEIGHT - 1, getWidth() - 28, 1);
                g.dispose();
            }
        };
        setContentPane(root);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setPreferredSize(new Dimension(WIDTH, FIELD_HEIGHT));
        JComponent glyph = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                paintMagnifier((Graphics2D) g, 24, getHeight() / 2.0, 18, colors().dim());
            }
        };
        glyph.setPreferredSize(new Dimension(44, FIELD_HEIGHT));
        top.add(glyph, BorderLayout.WEST);
        field.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
        field.setOpaque(false);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        top.add(field, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        root.add(results, BorderLayout.CENTER);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
        key("ESCAPE", this::close);
        key("DOWN", () -> move(1));
        key("UP", () -> move(-1));
        key("TAB", () -> move(1));
        key("shift TAB", () -> move(-1));
        key("ENTER", () -> open(selected));
        field.setFocusTraversalKeysEnabled(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                // A click elsewhere closes the search, as a menu does; the error dialog of a failed start is
                // a child window and does not count, nor does the focus moving about while the window appears.
                boolean child = e.getOppositeWindow() != null && e.getOppositeWindow().getOwner() == HorizonSearch.this;
                if (!child && System.currentTimeMillis() - openedAt > 400) close();
            }
        });
        ThemeManager.addListener(theme -> SwingUtilities.invokeLater(this::applyColors));
        applyColors();
    }

    /**
     * The search, ready to be shown: its window exists and asks the window manager for no frame, so that it is
     * mapped as it is, with the keyboard focus ({@code _AMADEOS_FRAMELESS}).
     */
    static HorizonSearch create(X11EwmhHelper helper, Path home) {
        HorizonSearch search = new HorizonSearch(helper, home);
        search.addNotify();
        long win = search.nativeWindowId();
        if (win != 0) helper.setFrameless(win);
        return search;
    }

    /** Opens and closes the search on Super+Space, which the window manager sends over a socket. */
    static void listen(HorizonSearch search) {
        try {
            InstanceChannel.listen(InstanceChannel.defaultPath(InstanceChannel.SEARCH),
                    args -> SwingUtilities.invokeLater(search::toggle));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Super+Space cannot reach the search", e);
        }
    }

    private void key(String stroke, Runnable action) {
        field.getInputMap().put(KeyStroke.getKeyStroke(stroke), stroke);
        field.getActionMap().put(stroke, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    static BarColors colors() {
        return BarColors.of(ThemeManager.currentTheme());
    }

    private void applyColors() {
        BarColors c = colors();
        field.setForeground(c.text());
        field.setCaretColor(c.accent());
        field.setSelectionColor(BarColors.mix(c.bar(), c.accent(), 0.45));
        field.setSelectedTextColor(c.text());
        repaint();
    }

    /** Super+Space and the magnifier: opens the search, or closes it when it is open. */
    void toggle() {
        if (isVisible()) {
            close();
        } else {
            open();
        }
    }

    void open() {
        entries = XdgApplicationManager.getInstance().getAllApplications();
        startIndexing();
        field.setText("");
        update();
        openedAt = System.currentTimeMillis();
        previous = helper.getActiveWindow();
        setVisible(true);
        toFront();
        field.requestFocusInWindow();
    }

    void close() {
        indexGeneration.incrementAndGet();
        boolean hadFocus = isFocused();
        setVisible(false);
        if (hadFocus && previous != 0) helper.activateWindow(previous);
    }

    /** Reads the home folder again in the background each time the search opens; the results follow. */
    private void startIndexing() {
        int generation = indexGeneration.incrementAndGet();
        Thread t = new Thread(() -> {
            List<Path> found = SearchIndex.indexFiles(home, () -> indexGeneration.get() != generation);
            if (indexGeneration.get() != generation) return;
            fileIndex = found;
            SwingUtilities.invokeLater(() -> {
                if (isVisible()) update();
            });
        }, "search-index");
        t.setDaemon(true);
        t.start();
    }

    /** The hits for the text in the field: the sum first, then programs, Settings pages and files. */
    static List<SearchIndex.Hit> search(String query, List<XdgDesktopEntry> entries, Path home, List<Path> files) {
        List<SearchIndex.Hit> all = new ArrayList<>();
        Optional<Double> sum = SearchCalculator.evaluate(query);
        sum.ifPresent(v -> {
            String value = SearchCalculator.format(v, Locale.getDefault());
            all.add(new SearchIndex.Hit(SearchIndex.Kind.CALCULATION, "= " + value, query.strip(),
                    "accessories-calculator", value, 1000));
        });
        all.addAll(SearchIndex.programs(query, entries));
        all.addAll(SearchIndex.settings(query));
        all.addAll(SearchIndex.files(query, home, files));
        return all;
    }

    private void update() {
        hits = search(field.getText(), entries, home, fileIndex);
        selected = 0;
        place();
        results.revalidate();
        getContentPane().repaint();
    }

    private void move(int delta) {
        if (hits.isEmpty()) return;
        selected = Math.floorMod(selected + delta, hits.size());
        results.repaint();
    }

    private void open(int index) {
        if (index < 0 || index >= hits.size()) return;
        SearchIndex.Hit hit = hits.get(index);
        switch (hit.kind()) {
            case CALCULATION -> {
                StringSelection value = new StringSelection(hit.target());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(value, value);
            }
            case PROGRAM -> entries.stream().filter(e -> e.id().equals(hit.target())).findFirst().ifPresent(e -> {
                try {
                    e.launch();
                } catch (Exception ex) {
                    launchFailed(e.name(), ex);
                }
            });
            case SETTING -> launch("amadeos-settings", hit.target());
            case FILE -> launch(Files.isDirectory(Path.of(hit.target())) ? "amade-files" : "xdg-open", hit.target());
        }
        close();
    }

    private void launch(String... command) {
        try {
            ProcessLauncher.start(List.of(command));
        } catch (Exception e) {
            launchFailed(command[0], e);
        }
    }

    private void launchFailed(String program, Exception e) {
        JOptionPane.showMessageDialog(this, PanelTexts.format("panel.launch.failed", program, e.getMessage()),
                PanelTexts.get("panel.error"), JOptionPane.ERROR_MESSAGE);
    }

    /** The height of the result list: a heading above each kind, a row for each hit. */
    static int resultsHeight(List<SearchIndex.Hit> hits) {
        if (hits.isEmpty()) return 0;
        int h = 12;
        SearchIndex.Kind last = null;
        for (SearchIndex.Hit hit : hits) {
            if (hit.kind() != last) h += HEADING_HEIGHT;
            last = hit.kind();
            h += ROW_HEIGHT;
        }
        return h;
    }

    /** Centred, a fifth of the way down the screen. */
    private void place() {
        int h = FIELD_HEIGHT + resultsHeight(hits) + (hits.isEmpty() && !field.getText().isBlank() ? ROW_HEIGHT : 0);
        int w = Math.min(WIDTH, helper.screenWidth() - 32);
        java.awt.Rectangle r = new java.awt.Rectangle((helper.screenWidth() - w) / 2, helper.screenHeight() / 5, w, h);
        if (!getBounds().equals(r)) setBounds(r);
        try {
            setShape(new RoundRectangle2D.Double(0, 0, r.width, r.height, ARC, ARC));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException e) {
            LOGGER.fine("No shaped search: " + e.getMessage());
        }
    }

    /** A magnifier: a ring with a handle to the lower right, centred at (cx, cy). */
    static void paintMagnifier(Graphics2D g0, double cx, double cy, double size, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        double r = size * 0.38;
        double ox = cx - size * 0.12;
        double oy = cy - size * 0.12;
        g.setStroke(new BasicStroke((float) Math.max(1.6, size / 7), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(ox - r, oy - r, 2 * r, 2 * r));
        double d = r * 0.7071;
        g.draw(new Line2D.Double(ox + d, oy + d, cx + size * 0.45, cy + size * 0.45));
        g.dispose();
    }

    private String heading(SearchIndex.Kind kind) {
        return TopBarTexts.get("search.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    private Icon icon(SearchIndex.Hit hit) {
        return HorizonDock.themeIcon(hit.iconKey(), 24);
    }

    /** The list under the field: a dim heading per kind, a row per hit, the chosen one in the accent colour. */
    private final class Results extends JComponent {
        Results() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = rowAt(e.getY());
                    if (i >= 0 && i != selected) {
                        selected = i;
                        repaint();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = rowAt(e.getY());
                    if (i >= 0 && SwingUtilities.isLeftMouseButton(e)) open(i);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private int rowAt(int y) {
            int top = 4;
            SearchIndex.Kind last = null;
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).kind() != last) top += HEADING_HEIGHT;
                last = hits.get(i).kind();
                if (y >= top && y < top + ROW_HEIGHT) return i;
                top += ROW_HEIGHT;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            BarColors c = colors();
            Font base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
            if (hits.isEmpty()) {
                if (!field.getText().isBlank()) {
                    g.setFont(base);
                    g.setColor(c.dim());
                    g.drawString(TopBarTexts.get("search.nothing"), 44, ROW_HEIGHT / 2 + 4);
                }
                g.dispose();
                return;
            }
            int top = 4;
            SearchIndex.Kind last = null;
            for (int i = 0; i < hits.size(); i++) {
                SearchIndex.Hit hit = hits.get(i);
                if (hit.kind() != last) {
                    g.setFont(base.deriveFont(Font.BOLD, 11f));
                    g.setColor(c.dim());
                    g.drawString(heading(hit.kind()).toUpperCase(Locale.getDefault()), 18, top + HEADING_HEIGHT - 7);
                    top += HEADING_HEIGHT;
                }
                last = hit.kind();
                boolean sel = i == selected;
                if (sel) {
                    g.setColor(c.accent());
                    g.fillRoundRect(8, top + 1, getWidth() - 16, ROW_HEIGHT - 2, 10, 10);
                }
                Color text = sel ? Color.WHITE : c.text();
                Color dim = sel ? BarColors.mix(c.accent(), Color.WHITE, 0.75) : c.dim();
                Color mark = sel ? Color.WHITE : c.accent();
                int my = top + (ROW_HEIGHT - 24) / 2;
                if (hit.kind() == SearchIndex.Kind.CALCULATION) {
                    paintCalculatorMark(g, 18, my, mark);
                } else if (hit.kind() == SearchIndex.Kind.SETTING) {
                    paintSettingsMark(g, 18, my, mark);
                } else if (hit.kind() == SearchIndex.Kind.FILE && hit.iconKey().equals("folder")) {
                    // On the chosen row, a darker folder stands out from the accent colour
                    BarColors fc = sel ? new BarColors(c.bar(), c.text(), c.dim(), c.hover(), c.pressed(),
                            BarColors.mix(c.accent(), Color.BLACK, 0.35)) : c;
                    new FolderIcon(FolderIcon.Kind.DOCUMENTS, 24, () -> fc).paintIcon(this, g, 18, my);
                } else if (hit.kind() == SearchIndex.Kind.FILE) {
                    paintFileMark(g, 18, my, mark);
                } else {
                    Icon icon = icon(hit);
                    icon.paintIcon(this, g, 18 + (24 - icon.getIconWidth()) / 2, top + (ROW_HEIGHT - icon.getIconHeight()) / 2);
                }
                int x = 54;
                Font titleFont = base.deriveFont(hit.kind() == SearchIndex.Kind.CALCULATION ? Font.BOLD : Font.PLAIN, 14f);
                g.setFont(titleFont);
                FontMetrics fm = g.getFontMetrics();
                int baseline = top + (ROW_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
                int room = getWidth() - x - 20;
                String title = clip(hit.title(), fm, room * 3 / 5);
                g.setColor(text);
                g.drawString(title, x, baseline);
                int tx = x + fm.stringWidth(title) + 12;
                g.setFont(base);
                FontMetrics dm = g.getFontMetrics();
                String detail = clip(hit.detail(), dm, getWidth() - tx - 20);
                g.setColor(dim);
                g.drawString(detail, tx, baseline);
                if (sel) {
                    String hint = TopBarTexts.get(hit.kind() == SearchIndex.Kind.CALCULATION ? "search.copy" : "search.open");
                    int hw = dm.stringWidth(hint);
                    if (tx + dm.stringWidth(detail) + hw + 36 < getWidth()) {
                        g.drawString(hint, getWidth() - hw - 20, baseline);
                    }
                }
                top += ROW_HEIGHT;
            }
            g.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(WIDTH, resultsHeight(hits));
        }
    }

    /** A small calculator: a rounded box with a display bar and four keys. */
    private static void paintCalculatorMark(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRoundRect(x + 3, y + 1, 18, 22, 5, 5);
        g.fillRect(x + 6, y + 4, 12, 4);
        for (int r = 0; r < 2; r++) {
            for (int k = 0; k < 2; k++) g.fillRect(x + 6 + k * 7, y + 11 + r * 6, 5, 4);
        }
    }

    /** Three sliders with their knobs: a page of Settings. */
    private static void paintSettingsMark(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int[] knob = {15, 7, 12};
        for (int i = 0; i < 3; i++) {
            int ly = y + 5 + i * 7;
            g.drawLine(x + 3, ly, x + 21, ly);
            g.fillOval(x + knob[i] - 3, ly - 3, 7, 7);
        }
    }

    /** A sheet with a folded corner: a file. */
    private static void paintFileMark(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int[] xs = {x + 5, x + 14, x + 20, x + 20, x + 5};
        int[] ys = {y + 2, y + 2, y + 8, y + 22, y + 22};
        g.drawPolygon(xs, ys, 5);
        g.drawPolyline(new int[]{x + 14, x + 14, x + 20}, new int[]{y + 2, y + 8, y + 8}, 3);
        for (int i = 0; i < 3; i++) g.drawLine(x + 8, y + 12 + i * 3, x + 17 - (i == 2 ? 4 : 0), y + 12 + i * 3);
    }

    static String clip(String text, FontMetrics fm, int width) {
        if (text == null) return "";
        if (fm.stringWidth(text) <= width) return text;
        String s = text;
        while (!s.isEmpty() && fm.stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "" : s + "…";
    }

    private long nativeWindowId() {
        try {
            return Native.getWindowID(this);
        } catch (Exception e) {
            return 0;
        }
    }
}
