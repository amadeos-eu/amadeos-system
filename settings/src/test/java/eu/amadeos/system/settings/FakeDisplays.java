package eu.amadeos.system.settings;

import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.display.OutputSetting;
import eu.amadeos.system.display.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An xrandr that knows a laptop with a 14" built-in screen and a 24" monitor on HDMI to its right, and records
 * every layout it is asked to set; the next query reports the last layout set.
 */
final class FakeDisplays extends DisplayApplier {
    final List<List<String>> commands = new ArrayList<>();
    boolean refuse;
    private List<OutputSetting> current = List.of(
            new OutputSetting("eDP-1", true, 1920, 1080, 60.02, 0, 0, Rotation.NORMAL, true),
            new OutputSetting("HDMI-1", true, 1920, 1080, 60.00, 1920, 0, Rotation.NORMAL, false));

    @Override
    public boolean apply(List<OutputSetting> layout) {
        if (refuse) {
            commands.add(List.of("refused"));
            return false;
        }
        boolean ok = super.apply(layout);
        if (ok) current = layout;
        return ok;
    }

    @Override
    protected int run(List<String> command, StringBuilder output) {
        commands.add(command);
        if (command.contains("--query") && output != null) output.append(query(current));
        return 0;
    }

    static String query(List<OutputSetting> layout) {
        StringBuilder out = new StringBuilder("Screen 0: minimum 320 x 200, current 3840 x 1080, maximum 16384 x 16384\n");
        for (OutputSetting s : layout) {
            boolean laptop = s.name().startsWith("eDP");
            out.append(s.name()).append(" connected").append(s.primary() ? " primary" : "");
            if (s.enabled()) {
                out.append(String.format(Locale.ROOT, " %dx%d+%d+%d", s.screenWidth(), s.screenHeight(), s.x(), s.y()));
                if (s.rotation() != Rotation.NORMAL) out.append(' ').append(s.rotation().xrandr());
            }
            out.append(" (normal left inverted right x axis y axis)");
            out.append(laptop ? " 309mm x 174mm\n" : " 527mm x 296mm\n");
            String[][] modes = laptop
                    ? new String[][]{{"1920x1080", "60.02+", "59.93", "48.00"}, {"1680x1050", "59.95"},
                    {"1280x1024", "60.02"}, {"1280x720", "60.00"}, {"1024x768", "60.04"}}
                    : new String[][]{{"2560x1440", "59.95"}, {"1920x1080", "60.00+", "74.97", "50.00", "59.94"},
                    {"1920x1080i", "60.00"}, {"1680x1050", "59.88"}, {"1280x1024", "75.02", "60.02"},
                    {"1280x720", "60.00"}, {"1024x768", "75.03", "60.00"}};
            for (String[] mode : modes) {
                out.append("   ").append(mode[0]);
                for (int i = 1; i < mode.length; i++) {
                    String rate = mode[i];
                    String plain = rate.replace("+", "");
                    boolean on = s.enabled() && mode[0].equals(s.width() + "x" + s.height())
                            && Math.abs(Double.parseDouble(plain) - s.rate()) < 0.005;
                    out.append("    ").append(plain).append(on ? "*" : " ").append(rate.endsWith("+") ? "+" : " ");
                }
                out.append('\n');
            }
        }
        out.append("DP-1 disconnected (normal left inverted right x axis y axis)\n");
        return out.toString();
    }
}
