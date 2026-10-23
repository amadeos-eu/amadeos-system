package eu.amadeos.system.display;

import java.util.List;

/**
 * A resolution a monitor offers, with its refresh rates in the order xrandr lists them.
 *
 * @param preferredRate the rate the monitor names as its own (xrandr's {@code +}), 0 if this is not its
 *                      preferred resolution
 */
public record DisplayMode(int width, int height, List<Double> rates, double preferredRate) {
    public DisplayMode {
        rates = List.copyOf(rates);
    }

    public boolean preferred() {
        return preferredRate > 0;
    }

    /** {@code 1920x1080}, the name xrandr knows the mode by. */
    public String size() {
        return width + "x" + height;
    }

    /** The aspect ratio as people say it, e.g. {@code 16:9}, or null for an unusual one. */
    public String aspect() {
        double ratio = (double) width / height;
        String[][] known = {{"16:9", "1.7778"}, {"16:10", "1.6"}, {"4:3", "1.3333"}, {"5:4", "1.25"},
                {"21:9", "2.3333"}, {"32:9", "3.5556"}, {"3:2", "1.5"}};
        for (String[] k : known) {
            if (Math.abs(ratio - Double.parseDouble(k[1])) < 0.02) return k[0];
        }
        // 1366x768 and 2560x1080 and friends are near enough to be sold as 16:9 and 21:9.
        if (Math.abs(ratio - 1.7778) < 0.05) return "16:9";
        if (Math.abs(ratio - 2.37) < 0.05) return "21:9";
        return null;
    }
}
