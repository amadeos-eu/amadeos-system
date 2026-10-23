package eu.amadeos.system.display;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the output of {@code xrandr --query} and writes the command line that sets a layout. Pure, so it can
 * be tested; {@link DisplayApplier} runs the commands.
 */
public final class Xrandr {
    // HDMI-1 connected primary 1080x1920+1920+0 left (normal left inverted right x axis y axis) 527mm x 296mm
    private static final Pattern OUTPUT = Pattern.compile(
            "^(\\S+) (connected|disconnected|unknown connection)( primary)?"
                    + "(?: (\\d+)x(\\d+)\\+(-?\\d+)\\+(-?\\d+))?(?: \\(0x[0-9a-f]+\\))?"
                    + "(?: (normal|left|right|inverted))?(?: (?:X axis|Y axis|X and Y axis))?"
                    + "(?: \\([^)]*\\))?(?: (\\d+)mm x (\\d+)mm)?.*$");
    //    1920x1080     60.02*+  59.93    48.00
    private static final Pattern MODE = Pattern.compile("^\\s+(\\d+)x(\\d+)(i?)\\S*\\s+(.*)$");
    private static final Pattern RATE = Pattern.compile("(\\d+(?:\\.\\d+)?)?([*]?)\\s*([+]?)");

    private Xrandr() {
    }

    /** The connectors of {@code xrandr --query}, connected or not, in xrandr's order. */
    public static List<Output> parse(String query) {
        List<Output> outputs = new ArrayList<>();
        Builder current = null;
        for (String line : query.split("\\R")) {
            if (line.startsWith("Screen ")) continue;
            Matcher o = OUTPUT.matcher(line);
            if (!line.startsWith(" ") && !line.startsWith("\t") && o.matches()) {
                if (current != null) outputs.add(current.build());
                current = new Builder(o);
                continue;
            }
            Matcher m = MODE.matcher(line);
            if (current != null && m.matches() && m.group(3).isEmpty()) {
                current.mode(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(4));
            }
        }
        if (current != null) outputs.add(current.build());
        return outputs;
    }

    // --8<-- [start:command]
    /**
     * The arguments of one xrandr call that sets every monitor of the layout at once. Outputs that are not in
     * the layout stay as they are.
     */
    public static List<String> command(List<OutputSetting> layout) {
        List<String> args = new ArrayList<>(List.of("xrandr"));
        for (OutputSetting s : normalized(layout)) {
            args.add("--output");
            args.add(s.name());
            if (!s.enabled()) {
                args.add("--off");
                continue;
            }
            args.add("--mode");
            args.add(s.width() + "x" + s.height());
            if (s.rate() > 0) {
                args.add("--rate");
                args.add(String.format(Locale.ROOT, "%.2f", s.rate()));
            }
            args.add("--pos");
            args.add(s.x() + "x" + s.y());
            args.add("--rotate");
            args.add(s.rotation().xrandr());
            if (s.primary()) args.add("--primary");
        }
        return args;
    }
    // --8<-- [end:command]

    /** The layout moved so that the monitors that are on start at 0,0, as X wants it. */
    public static List<OutputSetting> normalized(List<OutputSetting> layout) {
        int minX = layout.stream().filter(OutputSetting::enabled).mapToInt(OutputSetting::x).min().orElse(0);
        int minY = layout.stream().filter(OutputSetting::enabled).mapToInt(OutputSetting::y).min().orElse(0);
        return layout.stream().map(s -> s.enabled() ? s.withPosition(s.x() - minX, s.y() - minY) : s).toList();
    }

    /** The monitors that are on side by side from left to right, in the order of their current left edges. */
    public static List<OutputSetting> sideBySide(List<OutputSetting> layout) {
        List<OutputSetting> on = layout.stream().filter(OutputSetting::enabled)
                .sorted(Comparator.comparingInt(OutputSetting::x)).toList();
        Map<String, OutputSetting> placed = new LinkedHashMap<>();
        int x = 0;
        for (OutputSetting s : on) {
            placed.put(s.name(), s.withPosition(x, 0));
            x += s.screenWidth();
        }
        return layout.stream().map(s -> placed.getOrDefault(s.name(), s)).toList();
    }

    private static final class Builder {
        private final String name;
        private final boolean connected;
        private final boolean primary;
        private final boolean enabled;
        private final int x;
        private final int y;
        private final Rotation rotation;
        private final int widthMm;
        private final int heightMm;
        private final Map<String, ModeBuilder> modes = new LinkedHashMap<>();
        private int width;
        private int height;
        private double rate;

        Builder(Matcher o) {
            name = o.group(1);
            connected = o.group(2).equals("connected");
            primary = o.group(3) != null;
            enabled = o.group(4) != null;
            x = enabled ? Integer.parseInt(o.group(6)) : 0;
            y = enabled ? Integer.parseInt(o.group(7)) : 0;
            rotation = Rotation.of(o.group(8));
            if (enabled) {
                // The geometry is what the monitor covers on the screen, after rotation.
                int w = Integer.parseInt(o.group(4));
                int h = Integer.parseInt(o.group(5));
                width = rotation.portrait() ? h : w;
                height = rotation.portrait() ? w : h;
            }
            widthMm = o.group(9) == null ? 0 : Integer.parseInt(o.group(9));
            heightMm = o.group(10) == null ? 0 : Integer.parseInt(o.group(10));
        }

        void mode(int w, int h, String rates) {
            ModeBuilder mode = modes.computeIfAbsent(w + "x" + h, k -> new ModeBuilder(w, h));
            Matcher r = RATE.matcher(rates.strip());
            Double last = null;
            while (r.find()) {
                if (r.group().isBlank()) {
                    if (r.hitEnd()) break;
                    continue;
                }
                if (r.group(1) != null) {
                    last = Double.parseDouble(r.group(1));
                    if (!mode.rates.contains(last)) mode.rates.add(last);
                }
                if (last == null) continue;
                if (!r.group(2).isEmpty()) {
                    width = w;
                    height = h;
                    rate = last;
                }
                if (!r.group(3).isEmpty()) mode.preferredRate = last;
            }
        }

        Output build() {
            List<DisplayMode> list = modes.values().stream()
                    .map(m -> new DisplayMode(m.width, m.height, m.rates, m.preferredRate)).toList();
            return new Output(name, connected, primary, enabled, x, y, enabled ? width : 0, enabled ? height : 0,
                    rate, rotation, widthMm, heightMm, list);
        }
    }

    private static final class ModeBuilder {
        final int width;
        final int height;
        final List<Double> rates = new ArrayList<>();
        double preferredRate;

        ModeBuilder(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
