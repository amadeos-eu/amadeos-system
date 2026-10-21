package eu.amadeos.system.software.ui;

import java.util.Locale;

/** Sizes as people read them: 820 KB, 14.2 MB, 1.3 GB. */
final class Sizes {
    private Sizes() {
    }

    static String bytes(long bytes) {
        if (bytes < 1000) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return value >= 100 || unit == 0
                ? String.format(Locale.getDefault(), "%.0f %s", value, units[unit])
                : String.format(Locale.getDefault(), "%.1f %s", value, units[unit]);
    }

    /** dpkg's Installed-Size, in KiB. */
    static String kib(long kib) {
        return bytes(kib * 1024);
    }
}
