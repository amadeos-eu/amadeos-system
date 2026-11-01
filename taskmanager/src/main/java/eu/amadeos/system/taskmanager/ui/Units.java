package eu.amadeos.system.taskmanager.ui;

import java.util.Locale;

/** How the Task Manager writes sizes, rates, shares and durations, in the user's locale. */
public final class Units {
    private static final String[] SIZES = {"B", "kB", "MB", "GB", "TB"};

    private Units() {
    }

    /** 0 B, 812 kB, 1.9 GB (powers of 1024, as the memory figures of the kernel). */
    public static String bytes(double bytes, Locale locale) {
        if (bytes < 0) return "–";
        int unit = 0;
        double v = bytes;
        while (v >= 1000 && unit < SIZES.length - 1) {
            v /= 1024;
            unit++;
        }
        if (unit == 0) return String.format(locale, "%.0f %s", v, SIZES[0]);
        return String.format(locale, v < 10 ? "%.1f %s" : "%.0f %s", v, SIZES[unit]);
    }

    /** A rate: "0" for nothing, else like {@link #bytes} with "/s". */
    public static String rate(double bytesPerSecond, Locale locale) {
        if (bytesPerSecond < 0) return "–";
        if (bytesPerSecond < 1) return "0";
        return bytes(bytesPerSecond, locale) + "/s";
    }

    /** 0 %, 0.4 %, 38 %. */
    public static String percent(double share, Locale locale) {
        if (share <= 0) return "0 %";
        if (share < 10) return String.format(locale, "%.1f %%", share);
        return String.format(locale, "%.0f %%", share);
    }

    /** 3 h 12 min, 4 min 05 s, 2 d 3 h. */
    public static String duration(double seconds) {
        long s = (long) seconds;
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        if (d > 0) return d + " d " + h + " h";
        if (h > 0) return h + " h " + m + " min";
        return m + " min " + String.format("%02d s", s % 60);
    }
}
