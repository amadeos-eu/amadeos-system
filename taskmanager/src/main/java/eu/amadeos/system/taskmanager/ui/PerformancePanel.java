package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.taskmanager.model.ProcessRow;
import eu.amadeos.system.taskmanager.model.Sample;
import eu.amadeos.system.taskmanager.model.Sampler;
import eu.amadeos.system.taskmanager.proc.ProcFs;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** The Performance tab: processor, memory, network and disk over the last minute. */
final class PerformancePanel extends JPanel {
    /** The update speeds of the list, in seconds; 0 is paused. */
    static final int[] SPEEDS = {1, 2, 5, 0};
    private static final Color[] CORES = {new Color(0x2E, 0x6E, 0xC8), new Color(0x2E, 0x9E, 0x50),
            new Color(0xD0, 0x80, 0x10), new Color(0xA0, 0x40, 0xB0), new Color(0xC0, 0x30, 0x30),
            new Color(0x1E, 0x8A, 0x8A), new Color(0x80, 0x70, 0x20), new Color(0x50, 0x50, 0x50)};
    private static final Color MEMORY = new Color(0x8E, 0x3E, 0xA8);
    private static final Color CACHE = new Color(0xC8, 0xA8, 0xD8);
    private static final Color DOWN = new Color(0x1E, 0x8A, 0x8A);
    private static final Color UP = new Color(0xC0, 0x50, 0x30);
    private static final Color READ = new Color(0x30, 0x80, 0x30);
    private static final Color WRITE = new Color(0xB8, 0x70, 0x10);

    private final Messages t;
    private final Locale locale;
    private final ProcFs proc;
    private final Box cpu = new Box();
    private final Box memory = new Box();
    private final Box network = new Box();
    private final Box disk = new Box();
    private final JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
    private final JComboBox<String> speed;
    private final String cpuModel;
    private Box large;

    PerformancePanel(Context context, IntConsumer speedChosen) {
        super(new BorderLayout(0, 6));
        this.t = context.texts();
        this.locale = context.locale();
        this.proc = context.monitor().sampler().proc();
        this.cpuModel = proc.cpuModel();
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        cpu.graph.setMaximum(100);
        cpu.graph.setLabels(v -> "100 %", t.get("perf.ago"), t.get("perf.now"));
        memory.graph.setLabels(v -> Units.bytes(v, locale), t.get("perf.ago"), t.get("perf.now"));
        network.graph.setAutoScale(64 * 1024);
        network.graph.setLabels(v -> Units.rate(v, locale), t.get("perf.ago"), t.get("perf.now"));
        disk.graph.setAutoScale(1024 * 1024);
        disk.graph.setLabels(v -> Units.rate(v, locale), t.get("perf.ago"), t.get("perf.now"));
        for (Box b : List.of(cpu, memory, network, disk)) {
            b.graph.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    enlarge(b);
                }
            });
            b.graph.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }
        layoutGrid();
        add(grid, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        JLabel hint = new JLabel(t.get("perf.hint"));
        hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() * 0.85f));
        bar.add(hint, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        speed = new JComboBox<>(new String[]{t.get("update.1"), t.get("update.2"), t.get("update.5"),
                t.get("update.paused")});
        JLabel speedLabel = new JLabel(t.get("update.label"));
        speedLabel.setLabelFor(speed);
        speed.addActionListener(e -> speedChosen.accept(SPEEDS[speed.getSelectedIndex()]));
        right.add(speedLabel);
        right.add(speed);
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);
    }

    JComboBox<String> speedBox() {
        return speed;
    }

    /** Shows one graph over the whole tab, or all four again. */
    private void enlarge(Box b) {
        large = large == b ? null : b;
        layoutGrid();
        grid.revalidate();
        grid.repaint();
    }

    private void layoutGrid() {
        grid.removeAll();
        if (large != null) {
            grid.setLayout(new GridLayout(1, 1));
            grid.add(large);
        } else {
            grid.setLayout(new GridLayout(2, 2, 8, 8));
            grid.add(cpu);
            grid.add(memory);
            grid.add(network);
            grid.add(disk);
        }
    }

    // --8<-- [start:show]
    void show(Snapshot snapshot, Sampler sampler) {
        Sample s = snapshot.sample();
        int cores = s.cores().size();
        cpu.title.setText(t.format("perf.processor", Units.percent(s.cpu(), locale))
                + "   (" + t.format("perf.processor.cores", Integer.toString(cores)) + ")");
        List<Graph.Series> coreSeries = new ArrayList<>();
        List<eu.amadeos.system.taskmanager.model.History> histories = sampler.cores();
        for (int i = 0; i < histories.size(); i++) {
            coreSeries.add(new Graph.Series(histories.get(i).values(), CORES[i % CORES.length]));
        }
        // With many processors, one line each would be a tangle: then the total.
        if (cores > CORES.length) coreSeries = List.of(new Graph.Series(sampler.cpu().values(), CORES[0]));
        cpu.graph.setSeries(coreSeries, false);
        StringBuilder perCore = new StringBuilder();
        for (int i = 0; i < Math.min(cores, 16); i++) {
            if (i > 0) perCore.append(" · ");
            perCore.append(String.format(locale, "%.0f", s.cores().get(i)));
        }
        double[] load = s.load();
        cpu.figures.setText(html((cpuModel.isEmpty() ? "" : cpuModel + "<br>")
                + t.format("perf.cores", perCore + " %") + "   "
                + t.format("perf.load", String.format(locale, "%.2f  %.2f  %.2f", load[0], load[1], load[2]),
                Units.duration(s.uptimeSeconds()))));

        var m = s.memory();
        memory.title.setText(t.format("perf.memory", Units.bytes(m.used(), locale), Units.bytes(m.total(), locale))
                + "   " + t.format("perf.swap", Units.bytes(m.swapUsed(), locale), Units.bytes(m.swapTotal(), locale)));
        memory.graph.setMaximum(Math.max(1, m.total()));
        memory.graph.setSeries(List.of(new Graph.Series(sampler.memoryUsed().values(), MEMORY),
                new Graph.Series(sampler.memoryCache().values(), CACHE)), true);
        String largest = s.processes().values().stream().max(Comparator.comparingLong(ProcessRow::memory))
                .map(r -> "<br>" + t.format("perf.memory.largest", EndProcessDialog.escape(r.name()),
                        Units.bytes(r.memory(), locale))).orElse("");
        memory.figures.setText(html(t.format("perf.memory.figures", Units.bytes(m.used(), locale),
                Units.bytes(m.cache(), locale), Units.bytes(m.free(), locale)) + largest));

        network.title.setText(t.format("perf.network", Units.rate(s.netDown(), locale), Units.rate(s.netUp(), locale)));
        network.graph.setSeries(List.of(new Graph.Series(sampler.netDown().values(), DOWN),
                new Graph.Series(sampler.netUp().values(), UP)), false);
        List<String> devices = proc.networkDevices();
        network.figures.setText(html(devices.isEmpty() ? t.get("perf.network.none")
                : t.format("perf.network.devices", String.join(", ", devices))));

        disk.title.setText(t.format("perf.disk", Units.rate(s.diskRead(), locale), Units.rate(s.diskWrite(), locale)));
        disk.graph.setSeries(List.of(new Graph.Series(sampler.diskRead().values(), READ),
                new Graph.Series(sampler.diskWrite().values(), WRITE)), false);
        disk.figures.setText(" ");
    }
    // --8<-- [end:show]

    private static String html(String s) {
        return "<html>" + s + "</html>";
    }

    /** A titled graph with its figures below. */
    private static final class Box extends JPanel {
        final JLabel title = new JLabel(" ");
        final Graph graph = new Graph();
        final JLabel figures = new JLabel(" ");

        Box() {
            super(new BorderLayout(0, 3));
            setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            add(title, BorderLayout.NORTH);
            JPanel frame = new JPanel(new BorderLayout());
            frame.setBorder(AmadeUI.sunken());
            frame.add(graph);
            add(frame, BorderLayout.CENTER);
            figures.setFont(figures.getFont().deriveFont(figures.getFont().getSize2D() * 0.85f));
            add(figures, BorderLayout.SOUTH);
        }
    }
}
