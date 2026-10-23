package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.display.DisplayMode;
import eu.amadeos.system.display.DisplaySettingsStore;
import eu.amadeos.system.display.Output;
import eu.amadeos.system.display.OutputSetting;
import eu.amadeos.system.display.Rotation;
import eu.amadeos.system.display.Xrandr;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Display: the monitors, through xrandr. Resolution, refresh rate and rotation of each monitor, which monitors
 * are on, where they stand next to each other (dragged in the arrangement) and which is the primary one, or the
 * same picture on all. Apply sets the layout and asks whether to keep it; without an answer the old layout
 * comes back after {@value #KEEP_SECONDS} seconds, as on other systems. A kept layout is saved in
 * {@code display.conf} for this set of monitors and set again at every login.
 */
final class DisplayPage extends SettingsPage {
    static final int KEEP_SECONDS = 15;

    private final DisplayApplier displays;
    private final DisplaySettingsStore store;
    private final List<Output> outputs = new ArrayList<>();
    private final Map<String, OutputSetting> layout = new LinkedHashMap<>();
    private List<OutputSetting> applied = List.of();
    private String selected;
    private boolean updating;

    private final Arrangement arrangement = new Arrangement();
    private final JComboBox<String> monitor = new JComboBox<>();
    private final JCheckBox use = new JCheckBox(SettingsTexts.get("display.use"));
    private final JCheckBox primary = new JCheckBox(SettingsTexts.get("display.primary"));
    private final JComboBox<DisplayMode> resolution = new JComboBox<>();
    private final JComboBox<Double> rate = new JComboBox<>();
    private final JComboBox<Rotation> rotation = new JComboBox<>(Rotation.values());
    private final JCheckBox mirror = new JCheckBox(SettingsTexts.get("display.mirror"));
    private final JLabel empty = new JLabel(SettingsTexts.get("display.none"));
    /** Asks whether to keep a new layout; tests answer at once. */
    private KeepQuestion keepQuestion = this::askToKeep;

    /** Shows the question after a layout was set and reports the answer, true to keep it. */
    interface KeepQuestion {
        void ask(Consumer<Boolean> answer);
    }

    DisplayPage(Consumer<String> status, DisplayApplier displays, DisplaySettingsStore store) {
        super(SettingsCategory.DISPLAY, status);
        this.displays = displays;
        this.store = store;

        monitor.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof String name) setText(monitorLabel(name));
                return this;
            }
        });
        resolution.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof DisplayMode mode) setText(modeLabel(mode));
                return this;
            }
        });
        rate.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Double hz) setText(rateLabel(hz));
                return this;
            }
        });
        rotation.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Rotation r) {
                    setText(SettingsTexts.get("display.rotation." + r.name().toLowerCase(Locale.ROOT)));
                }
                return this;
            }
        });

        monitor.addActionListener(e -> {
            if (!updating && monitor.getSelectedItem() instanceof String name) select(name);
        });
        use.addActionListener(e -> changeSelected(s -> s.withEnabled(use.isSelected())));
        primary.addActionListener(e -> {
            if (updating || !primary.isSelected()) return;
            layout.replaceAll((n, s) -> s.withPrimary(n.equals(selected)));
            refresh();
        });
        resolution.addActionListener(e -> {
            if (updating || !(resolution.getSelectedItem() instanceof DisplayMode mode)) return;
            double hz = mode.preferred() ? mode.preferredRate() : mode.rates().isEmpty() ? 0 : mode.rates().getFirst();
            if (mirror.isSelected()) {
                layout.replaceAll((n, s) -> s.enabled() ? s.withMode(mode.width(), mode.height(), bestRate(n, mode)) : s);
                refresh();
            } else {
                changeSelected(s -> s.withMode(mode.width(), mode.height(), hz));
            }
        });
        rate.addActionListener(e -> {
            if (!updating && rate.getSelectedItem() instanceof Double hz) {
                changeSelected(s -> s.withMode(s.width(), s.height(), hz));
            }
        });
        rotation.addActionListener(e -> {
            if (!updating && rotation.getSelectedItem() instanceof Rotation r) changeSelected(s -> s.withRotation(r));
        });
        mirror.addActionListener(e -> {
            if (updating) return;
            if (mirror.isSelected()) mirrorAll();
            else replaceLayout(Xrandr.sideBySide(settings()));
            refresh();
        });

        JButton identify = new JButton(SettingsTexts.get("display.identify"));
        identify.setToolTipText(SettingsTexts.get("display.identify.tooltip"));
        identify.addActionListener(e -> identify());
        JButton detect = new JButton(SettingsTexts.get("display.detect"));
        detect.setToolTipText(SettingsTexts.get("display.detect.tooltip"));
        detect.addActionListener(e -> reload());

        JPanel arrangementBox = new JPanel(new BorderLayout(0, 4));
        arrangementBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("display.arrangement.title")));
        arrangementBox.add(arrangement, BorderLayout.CENTER);
        JPanel arrangementButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        arrangementButtons.add(identify);
        arrangementButtons.add(detect);
        arrangementButtons.add(mirror);
        arrangementBox.add(arrangementButtons, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("display.monitor.title")));
        addRow(form, 0, SettingsTexts.get("display.monitor"), monitor);
        addRow(form, 1, null, line0(use, primary));
        addRow(form, 2, SettingsTexts.get("display.resolution"), resolution);
        addRow(form, 3, SettingsTexts.get("display.rate"), rate);
        addRow(form, 4, SettingsTexts.get("display.rotation"), rotation);

        layoutSections(arrangementBox, form, note(SettingsTexts.get("display.arrangement.hint")),
                note(SettingsTexts.format("display.hint", KEEP_SECONDS)));
        reload();
    }

    private static JPanel line0(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (Component c : components) {
            row.add(c);
            row.add(javax.swing.Box.createHorizontalStrut(16));
        }
        return row;
    }

    private static void addRow(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(3, 8, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        if (label != null) {
            c.gridx = 0;
            form.add(new JLabel(label), c);
        }
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.NONE;
        form.add(field, c);
    }

    JComboBox<DisplayMode> resolution() {
        return resolution;
    }

    JComboBox<Rotation> rotation() {
        return rotation;
    }

    JCheckBox use() {
        return use;
    }

    JCheckBox mirror() {
        return mirror;
    }

    void setKeepQuestion(KeepQuestion question) {
        keepQuestion = question;
    }

    /** Asks xrandr again for the monitors and shows the layout they have now. */
    void reload() {
        outputs.clear();
        outputs.addAll(displays.connected());
        layout.clear();
        for (Output o : outputs) layout.put(o.name(), o.current());
        applied = List.copyOf(layout.values());
        if (selected == null || !layout.containsKey(selected)) {
            selected = outputs.stream().filter(Output::primary).map(Output::name).findFirst()
                    .orElse(outputs.isEmpty() ? null : outputs.getFirst().name());
        }
        mirror.setSelected(isMirrored());
        refresh();
    }

    List<OutputSetting> settings() {
        return List.copyOf(layout.values());
    }

    String selected() {
        return selected;
    }

    void select(String name) {
        if (!layout.containsKey(name)) return;
        selected = name;
        refresh();
    }

    private void changeSelected(java.util.function.UnaryOperator<OutputSetting> change) {
        if (updating || selected == null) return;
        OutputSetting before = layout.get(selected);
        OutputSetting after = change.apply(before);
        if (after.enabled() && after.width() == 0) {
            // A monitor that was off gets its preferred resolution, to the right of the others.
            DisplayMode mode = output(selected).preferredMode().orElse(null);
            if (mode == null) return;
            int right = layout.values().stream().filter(OutputSetting::enabled)
                    .mapToInt(s -> s.x() + s.screenWidth()).max().orElse(0);
            after = new OutputSetting(selected, true, mode.width(), mode.height(), mode.preferredRate(), right, 0,
                    Rotation.NORMAL, after.primary());
        }
        if (!after.enabled() && layout.values().stream().filter(OutputSetting::enabled).count() <= 1) {
            refresh(); // the last monitor stays on
            return;
        }
        layout.put(selected, after);
        if (before.screenWidth() != after.screenWidth() || before.screenHeight() != after.screenHeight()
                || before.enabled() != after.enabled()) {
            // Monitors that stood next to each other still do, rather than overlap or leave a gap.
            replaceLayout(mirror.isSelected() ? settings() : Xrandr.sideBySide(settings()));
        }
        refresh();
    }

    private void mirrorAll() {
        // The largest resolution all monitors that are on can show, everyone at 0,0.
        List<OutputSetting> on = layout.values().stream().filter(OutputSetting::enabled).toList();
        DisplayMode common = null;
        Output first = output(on.getFirst().name());
        for (DisplayMode mode : first.modes()) {
            if (on.stream().allMatch(s -> output(s.name()).mode(mode.width(), mode.height()).isPresent())) {
                common = mode;
                break;
            }
        }
        if (common == null) {
            mirror.setSelected(false);
            status(SettingsTexts.get("display.mirror.impossible"));
            return;
        }
        DisplayMode mode = common;
        layout.replaceAll((n, s) -> s.enabled()
                ? new OutputSetting(n, true, mode.width(), mode.height(), bestRate(n, mode), 0, 0, Rotation.NORMAL,
                s.primary()) : s);
    }

    private double bestRate(String name, DisplayMode mode) {
        return output(name).mode(mode.width(), mode.height())
                .map(m -> m.preferred() ? m.preferredRate() : m.rates().isEmpty() ? 0 : m.rates().getFirst())
                .orElse(0.0);
    }

    private boolean isMirrored() {
        List<OutputSetting> on = layout.values().stream().filter(OutputSetting::enabled).toList();
        return on.size() > 1 && on.stream().allMatch(s -> s.x() == on.getFirst().x() && s.y() == on.getFirst().y());
    }

    private void replaceLayout(List<OutputSetting> settings) {
        for (OutputSetting s : settings) layout.put(s.name(), s);
    }

    private Output output(String name) {
        return outputs.stream().filter(o -> o.name().equals(name)).findFirst().orElseThrow();
    }

    /** Puts the layout into the controls. */
    private void refresh() {
        updating = true;
        try {
            boolean any = !layout.isEmpty();
            monitor.setModel(new DefaultComboBoxModel<>(layout.keySet().toArray(String[]::new)));
            monitor.setSelectedItem(selected);
            mirror.setEnabled(layout.size() > 1);
            for (JComponent c : new JComponent[]{monitor, use, primary, resolution, rate, rotation}) c.setEnabled(any);
            if (!any) {
                arrangement.repaint();
                return;
            }
            OutputSetting s = layout.get(selected);
            Output o = output(selected);
            use.setSelected(s.enabled());
            use.setEnabled(layout.size() > 1);
            primary.setSelected(s.primary());
            primary.setEnabled(s.enabled() && layout.size() > 1);
            resolution.setModel(new DefaultComboBoxModel<>(o.modes().toArray(DisplayMode[]::new)));
            o.mode(s.width(), s.height()).ifPresent(resolution::setSelectedItem);
            DisplayMode mode = o.mode(s.width(), s.height()).orElse(null);
            List<Double> rates = mode == null ? List.of() : mode.rates().stream().filter(r -> r > 0).toList();
            rate.setModel(new DefaultComboBoxModel<>(rates.toArray(Double[]::new)));
            rates.stream().filter(r -> Math.abs(r - s.rate()) < 0.005).findFirst().ifPresent(rate::setSelectedItem);
            rotation.setSelectedItem(s.rotation());
            resolution.setEnabled(s.enabled());
            rate.setEnabled(s.enabled() && rates.size() > 1);
            rotation.setEnabled(s.enabled() && !mirror.isSelected());
        } finally {
            updating = false;
        }
        arrangement.repaint();
    }

    /** "1  Built-in screen (14″)" or "2  HDMI-1 (24″)": the number the arrangement and Identify show. */
    private String monitorLabel(String name) {
        int number = new ArrayList<>(layout.keySet()).indexOf(name) + 1;
        Output o = outputs.stream().filter(out -> out.name().equals(name)).findFirst().orElse(null);
        String label = o != null && o.builtIn() ? SettingsTexts.get("display.builtin") : name;
        if (o != null && o.diagonalInches() > 0) {
            String inches = o.diagonalInches() % 1 == 0 ? Integer.toString((int) o.diagonalInches())
                    : String.format(Locale.ROOT, "%.1f", o.diagonalInches());
            label = SettingsTexts.format("display.monitor.size", label, inches);
        }
        return number + "  " + label;
    }

    private static String modeLabel(DisplayMode mode) {
        String size = mode.width() + " × " + mode.height();
        if (mode.aspect() != null) size += " (" + mode.aspect() + ")";
        return mode.preferred() ? SettingsTexts.format("display.recommended", size) : size;
    }

    private static String rateLabel(double hz) {
        return String.format(SettingsTexts.locale(), "%.2f Hz", hz);
    }

    // ------------------------------------------------------------------------------------------ apply

    // --8<-- [start:apply]
    @Override
    protected String apply() {
        List<OutputSetting> wanted = Xrandr.normalized(settings());
        if (wanted.isEmpty()) return SettingsTexts.get("display.none");
        if (wanted.equals(Xrandr.normalized(applied))) {
            store.save(wanted);
            return SettingsTexts.format("status.applied", SettingsCategory.DISPLAY.label());
        }
        List<OutputSetting> previous = applied;
        if (!displays.apply(wanted)) {
            displays.apply(previous);
            throw new IllegalStateException(SettingsTexts.get("display.refused"));
        }
        keepQuestion.ask(keep -> {
            if (keep) {
                store.save(wanted);
                applied = wanted;
                status(SettingsTexts.format("status.applied", SettingsCategory.DISPLAY.label()));
            } else {
                displays.apply(previous);
                status(SettingsTexts.get("display.reverted"));
            }
            reload();
        });
        return null;
    }
    // --8<-- [end:apply]

    @Override
    protected void cancelled() {
        reload();
    }

    /** The question with the countdown; the old layout comes back unless Keep is pressed in time. */
    private void askToKeep(Consumer<Boolean> answer) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, SettingsTexts.get("display.keep.title"), JDialog.ModalityType.MODELESS);
        JLabel question = new JLabel(SettingsTexts.get("display.keep.question"));
        question.setFont(question.getFont().deriveFont(Font.BOLD));
        JLabel countdown = new JLabel(SettingsTexts.format("display.keep.countdown", KEEP_SECONDS));
        JButton keep = new JButton(SettingsTexts.get("display.keep"));
        JButton revert = new JButton(SettingsTexts.get("display.revert"));
        boolean[] answered = {false};
        int[] left = {KEEP_SECONDS};
        Timer timer = new Timer(1000, null);
        Consumer<Boolean> finish = k -> {
            if (answered[0]) return;
            answered[0] = true;
            timer.stop();
            dialog.dispose();
            answer.accept(k);
        };
        timer.addActionListener(e -> {
            left[0]--;
            countdown.setText(SettingsTexts.format("display.keep.countdown", left[0]));
            if (left[0] <= 0) finish.accept(false);
        });
        keep.addActionListener(e -> finish.accept(true));
        revert.addActionListener(e -> finish.accept(false));
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                finish.accept(false);
            }
        });
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel text = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
        text.add(question);
        text.add(countdown);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(keep);
        buttons.add(revert);
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(text, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(revert);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        timer.start();
    }

    /** A big number on every monitor that is on, for three seconds. */
    private void identify() {
        int number = 0;
        List<OutputSetting> on = Xrandr.normalized(applied);
        for (String name : layout.keySet()) {
            number++;
            OutputSetting s = on.stream().filter(o -> o.name().equals(name)).findFirst().orElse(null);
            if (s == null || !s.enabled()) continue;
            JWindow window = new JWindow();
            JLabel label = new JLabel(Integer.toString(number), SwingConstants.CENTER);
            DesktopTheme theme = ThemeManager.currentTheme();
            label.setOpaque(true);
            label.setBackground(theme.activeTitleBg());
            label.setForeground(theme.textActive());
            label.setFont(label.getFont().deriveFont(Font.BOLD, 96f));
            label.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
            window.setContentPane(label);
            window.setSize(180, 180);
            window.setLocation(s.x() + 40, s.y() + 40);
            window.setVisible(true);
            Timer close = new Timer(3000, e -> window.dispose());
            close.setRepeats(false);
            close.start();
        }
    }

    // ------------------------------------------------------------------------------------ arrangement

    /**
     * The monitors that are on as boxes in their places, numbered as in the list. A click selects a monitor;
     * dragging moves it, and on release it snaps to the nearest edge of another monitor.
     */
    private final class Arrangement extends JComponent {
        private static final int PAD = 14;
        private String dragged;
        private int grabX;
        private int grabY;

        Arrangement() {
            setPreferredSize(new Dimension(420, 150));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    String hit = monitorAt(e.getX(), e.getY());
                    if (hit == null) return;
                    select(hit);
                    if (mirror.isSelected()) return;
                    Rectangle r = boxOf(layout.get(hit));
                    dragged = hit;
                    grabX = e.getX() - r.x;
                    grabY = e.getY() - r.y;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragged == null) return;
                    double scale = scale();
                    OutputSetting s = layout.get(dragged);
                    int x = (int) Math.round((e.getX() - grabX - origin()[0]) / scale) + desktop().x;
                    int y = (int) Math.round((e.getY() - grabY - origin()[1]) / scale) + desktop().y;
                    layout.put(dragged, s.withPosition(x, y));
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragged == null) return;
                    layout.put(dragged, snap(layout.get(dragged)));
                    dragged = null;
                    replaceLayout(Xrandr.normalized(settings()));
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        /** The place next to another monitor nearest to where the monitor was dropped. */
        private OutputSetting snap(OutputSetting moved) {
            OutputSetting best = moved;
            long bestDistance = Long.MAX_VALUE;
            for (OutputSetting o : layout.values()) {
                if (!o.enabled() || o.name().equals(moved.name())) continue;
                int w = moved.screenWidth();
                int h = moved.screenHeight();
                int[][] candidates = {
                        {o.x() - w, clamp(moved.y(), o.y() - h + 1, o.y() + o.screenHeight() - 1)},
                        {o.x() + o.screenWidth(), clamp(moved.y(), o.y() - h + 1, o.y() + o.screenHeight() - 1)},
                        {clamp(moved.x(), o.x() - w + 1, o.x() + o.screenWidth() - 1), o.y() - h},
                        {clamp(moved.x(), o.x() - w + 1, o.x() + o.screenWidth() - 1), o.y() + o.screenHeight()}};
                for (int[] c : candidates) {
                    // Edges that nearly line up do: top with top, bottom with bottom, left with left.
                    if (Math.abs(c[1] - o.y()) < h / 10) c[1] = o.y();
                    if (Math.abs(c[1] + h - o.y() - o.screenHeight()) < h / 10) c[1] = o.y() + o.screenHeight() - h;
                    if (Math.abs(c[0] - o.x()) < w / 10) c[0] = o.x();
                    long dx = c[0] - moved.x();
                    long dy = c[1] - moved.y();
                    long distance = dx * dx + dy * dy;
                    OutputSetting candidate = moved.withPosition(c[0], c[1]);
                    if (distance < bestDistance && !overlapsAny(candidate)) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        private boolean overlapsAny(OutputSetting s) {
            Rectangle r = new Rectangle(s.x(), s.y(), s.screenWidth(), s.screenHeight());
            return layout.values().stream().filter(o -> o.enabled() && !o.name().equals(s.name()))
                    .anyMatch(o -> r.intersects(new Rectangle(o.x(), o.y(), o.screenWidth(), o.screenHeight())));
        }

        private static int clamp(int v, int min, int max) {
            return Math.max(min, Math.min(max, v));
        }

        private Rectangle desktop() {
            Rectangle all = null;
            for (OutputSetting s : layout.values()) {
                if (!s.enabled()) continue;
                Rectangle r = new Rectangle(s.x(), s.y(), s.screenWidth(), s.screenHeight());
                all = all == null ? r : all.union(r);
            }
            return all == null ? new Rectangle(0, 0, 1, 1) : all;
        }

        private double scale() {
            Rectangle all = desktop();
            // Room for a monitor dragged to either side.
            double sx = (getWidth() - 2 * PAD) / (all.width * 1.0);
            double sy = (getHeight() - 2 * PAD) / (all.height * 1.0);
            return Math.min(sx, sy) * (layout.size() > 1 ? 0.8 : 0.9);
        }

        private int[] origin() {
            Rectangle all = desktop();
            double scale = scale();
            return new int[]{(int) ((getWidth() - all.width * scale) / 2), (int) ((getHeight() - all.height * scale) / 2)};
        }

        private Rectangle boxOf(OutputSetting s) {
            double scale = scale();
            int[] origin = origin();
            Rectangle all = desktop();
            return new Rectangle(origin[0] + (int) Math.round((s.x() - all.x) * scale),
                    origin[1] + (int) Math.round((s.y() - all.y) * scale),
                    (int) Math.round(s.screenWidth() * scale), (int) Math.round(s.screenHeight() * scale));
        }

        private String monitorAt(int x, int y) {
            List<String> names = new ArrayList<>(layout.keySet());
            // The selected monitor is drawn last, on top, so it is hit first.
            if (selected != null) {
                names.remove(selected);
                names.add(selected);
            }
            for (int i = names.size() - 1; i >= 0; i--) {
                OutputSetting s = layout.get(names.get(i));
                if (s.enabled() && boxOf(s).contains(x, y)) return s.name();
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            DesktopTheme theme = ThemeManager.currentTheme();
            g.setColor(theme.sunkenBg());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(theme.shadow());
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            if (layout.isEmpty()) {
                g.dispose();
                return;
            }
            List<String> order = new ArrayList<>(layout.keySet());
            order.remove(selected);
            order.add(selected);
            List<String> numbers = new ArrayList<>(layout.keySet());
            for (String name : order) {
                OutputSetting s = layout.get(name);
                if (!s.enabled()) continue;
                Rectangle r = boxOf(s);
                boolean isSelected = name.equals(selected);
                Color face = isSelected ? theme.activeTitleBg() : theme.inactiveTitleBg();
                Color text = isSelected ? theme.textActive() : theme.textInactive();
                g.setColor(face);
                g.fillRect(r.x, r.y, r.width, r.height);
                // Bevel as the desktop draws its buttons.
                g.setColor(face.brighter());
                g.drawLine(r.x, r.y, r.x + r.width - 1, r.y);
                g.drawLine(r.x, r.y, r.x, r.y + r.height - 1);
                g.setColor(face.darker().darker());
                g.drawLine(r.x + r.width - 1, r.y, r.x + r.width - 1, r.y + r.height - 1);
                g.drawLine(r.x, r.y + r.height - 1, r.x + r.width - 1, r.y + r.height - 1);
                if (isSelected) {
                    g.setColor(text);
                    g.setStroke(new BasicStroke(2f));
                    g.drawRect(r.x + 3, r.y + 3, r.width - 7, r.height - 7);
                    g.setStroke(new BasicStroke(1f));
                }
                g.setColor(text);
                g.setFont(getFont().deriveFont(Font.BOLD, AmadeUI.scaled(Math.max(14f, Math.min(36f, r.height / 3f)))));
                String number = Integer.toString(numbers.indexOf(name) + 1);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(number, r.x + (r.width - fm.stringWidth(number)) / 2, r.y + r.height / 2 + fm.getAscent() / 3);
                g.setFont(getFont().deriveFont(Font.PLAIN, AmadeUI.scaled(10f)));
                fm = g.getFontMetrics();
                String caption = s.width() + "×" + s.height() + (s.primary() && layout.size() > 1 ? " ★" : "");
                if (fm.stringWidth(caption) < r.width - 6) {
                    g.drawString(caption, r.x + (r.width - fm.stringWidth(caption)) / 2, r.y + r.height - 6);
                }
                String label = name;
                if (fm.stringWidth(label) < r.width - 6) {
                    g.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2, r.y + fm.getAscent() + 4);
                }
            }
            g.dispose();
        }
    }
}
