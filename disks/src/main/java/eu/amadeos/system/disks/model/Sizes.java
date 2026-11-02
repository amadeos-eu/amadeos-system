package eu.amadeos.system.disks.model;

import java.util.Locale;

/**
 * How Disks writes sizes: in powers of 1000, as drive makers print them on the box (a 32 GB stick shows as
 * 32 GB, not 29.8 GB).
 */
public final class Sizes {
    public static final long KB = 1000L;
    public static final long MB = 1000L * KB;
    public static final long GB = 1000L * MB;
    public static final long MIB = 1024L * 1024L;
    private static final String[] UNITS = {"B", "kB", "MB", "GB", "TB", "PB"};

    private Sizes() {
    }

    /** 512 B, 4.1 GB, 32 GB, 1.0 TB. */
    public static String bytes(long bytes, Locale locale) {
        if (bytes < 0) return "–";
        int unit = 0;
        double v = bytes;
        while (v >= 999.5 && unit < UNITS.length - 1) {
            v /= 1000;
            unit++;
        }
        if (unit == 0) return bytes + " B";
        String pattern = v < 9.95 ? "%.1f %s" : "%.0f %s";
        return String.format(locale, pattern, v, UNITS[unit]);
    }

    /** A share in whole percent, 0 to 100. */
    public static int percent(long part, long whole) {
        if (whole <= 0) return 0;
        return (int) Math.max(0, Math.min(100, Math.round(100.0 * part / whole)));
    }
}
