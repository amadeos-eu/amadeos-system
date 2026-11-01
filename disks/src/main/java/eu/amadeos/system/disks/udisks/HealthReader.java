package eu.amadeos.system.disks.udisks;

import eu.amadeos.system.disks.model.Health;
import eu.amadeos.system.disks.model.Health.Row;
import eu.amadeos.system.disks.model.Health.Verdict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns what a drive reports into a {@link Health}: the SMART attributes of SATA drives, the health log of NVMe
 * drives. The verdict is plain on purpose: REPLACE when the drive itself says it is failing or a value is past its
 * limit, WATCH when sectors had to be replaced or errors were logged, else GOOD. Pure, so it can be tested.
 */
public final class HealthReader {
    /** SATA attributes that count damaged sectors: any count above 0 is a reason to watch the drive. */
    private static final Set<Integer> SECTOR_COUNTS = Set.of(5, 196, 197, 198);
    /** Above this many damaged sectors, replace. */
    static final long REPLACE_SECTORS = 100;

    private HealthReader() {
    }

    /** One SATA attribute, as udisks' SmartGetAttributes gives it (pretty value already in its unit). */
    public record AtaValue(int id, String name, int value, int worst, int threshold, long pretty, int unit) {
    }

    /**
     * @param drive the properties of {@code org.freedesktop.UDisks2.Drive.Ata}
     */
    public static Health ata(Map<String, Object> drive, List<AtaValue> attributes) {
        Verdict verdict = Verdict.GOOD;
        List<String> reasons = new ArrayList<>();
        if (UDisksObjects.bool(drive, "SmartFailing")) {
            verdict = Verdict.REPLACE;
            reasons.add("health.reason.failing");
        }
        long hours = UDisksObjects.number(drive, "SmartPowerOnSeconds") / 3600;
        int temperature = -1;
        if (drive.get("SmartTemperature") instanceof Number n && n.doubleValue() > 0) {
            temperature = (int) Math.round(n.doubleValue() - 273.15);
        }
        long cycles = -1, written = -1, damaged = 0;
        List<Row> rows = new ArrayList<>();
        for (AtaValue a : attributes) {
            boolean past = a.threshold() > 0 && a.value() > 0 && a.value() <= a.threshold();
            boolean ok = !past;
            if (SECTOR_COUNTS.contains(a.id()) && a.pretty() > 0) {
                damaged += a.pretty();
                ok = false;
            }
            if (past && verdict != Verdict.REPLACE) {
                verdict = Verdict.REPLACE;
                reasons.add("health.reason.past-limit");
            }
            if (a.id() == 12) cycles = a.pretty();
            if (a.id() == 241 && a.unit() == 3) written = a.pretty() * 512;
            if (a.id() == 241 && a.unit() == 1 && a.name().toLowerCase().contains("lba")) written = a.pretty() * 512;
            rows.add(new Row(a.name().replace('_', ' '), pretty(a.pretty(), a.unit()) + "  (" + a.value() + ")",
                    a.threshold() > 0 ? Integer.toString(a.threshold()) : "", ok));
        }
        if (damaged > REPLACE_SECTORS && verdict != Verdict.REPLACE) {
            verdict = Verdict.REPLACE;
            reasons.add("health.reason.many-sectors");
        } else if (damaged > 0 && verdict == Verdict.GOOD) {
            verdict = Verdict.WATCH;
            reasons.add("health.reason.sectors");
        }
        if (temperature >= 60 && verdict == Verdict.GOOD) {
            verdict = Verdict.WATCH;
            reasons.add("health.reason.hot");
        }
        return new Health(verdict, reasons, temperature, -1, written, hours > 0 ? hours : -1, cycles,
                UDisksObjects.string(drive, "SmartSelftestStatus"),
                (int) (drive.containsKey("SmartSelftestPercentRemaining")
                        ? UDisksObjects.number(drive, "SmartSelftestPercentRemaining") : -1),
                rows);
    }

    /**
     * @param controller the properties of {@code org.freedesktop.UDisks2.NVMe.Controller}
     * @param log        what its SmartGetAttributes returned
     */
    public static Health nvme(Map<String, Object> controller, Map<String, Object> log) {
        Verdict verdict = Verdict.GOOD;
        List<String> reasons = new ArrayList<>();
        List<String> warnings = UDisksObjects.strings(controller, "SmartCriticalWarning");
        for (String w : warnings) {
            if (w.equals("temperature")) {
                if (verdict == Verdict.GOOD) verdict = Verdict.WATCH;
                reasons.add("health.reason.hot");
            } else {
                verdict = Verdict.REPLACE;
                reasons.add("health.reason.nvme." + w);
            }
        }
        int used = (int) UDisksObjects.number(log, "percent_used");
        long spare = UDisksObjects.number(log, "avail_spare");
        long spareLimit = UDisksObjects.number(log, "spare_thresh");
        long mediaErrors = UDisksObjects.number(log, "media_errors");
        if (used >= 100 || spareLimit > 0 && spare <= spareLimit) {
            if (verdict != Verdict.REPLACE) reasons.add("health.reason.worn");
            verdict = Verdict.REPLACE;
        } else if (used >= 90 && verdict == Verdict.GOOD) {
            verdict = Verdict.WATCH;
            reasons.add("health.reason.worn");
        }
        if (mediaErrors > 0 && verdict == Verdict.GOOD) {
            verdict = Verdict.WATCH;
            reasons.add("health.reason.media-errors");
        }
        long kelvin = UDisksObjects.number(controller, "SmartTemperature");
        int temperature = kelvin > 0 ? (int) (kelvin - 273) : -1;
        long warnTemp = UDisksObjects.number(log, "wctemp");
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("critical-warning", warnings.isEmpty() ? "–" : String.join(", ", warnings), "",
                warnings.isEmpty()));
        rows.add(new Row("available-spare", spare + " %", spareLimit + " %", spareLimit == 0 || spare > spareLimit));
        rows.add(new Row("percentage-used", used + " %", "100 %", used < 100));
        rows.add(new Row("media-errors", Long.toString(mediaErrors), "", mediaErrors == 0));
        rows.add(new Row("unsafe-shutdowns", Long.toString(UDisksObjects.number(log, "unsafe_shutdowns")), "",
                true));
        rows.add(new Row("error-log-entries", Long.toString(UDisksObjects.number(log, "num_err_log_entries")), "",
                true));
        if (warnTemp > 0) {
            rows.add(new Row("warning-temperature", (warnTemp - 273) + " °C", "", true));
        }
        long hours = UDisksObjects.number(controller, "SmartPowerOnHours");
        return new Health(verdict, reasons, temperature, used, nonZero(UDisksObjects.number(log, "total_data_written")),
                hours > 0 ? hours : -1, nonZero(UDisksObjects.number(log, "power_cycles")),
                UDisksObjects.string(controller, "SmartSelftestStatus"),
                (int) (controller.containsKey("SmartSelftestPercentRemaining")
                        ? UDisksObjects.number(controller, "SmartSelftestPercentRemaining") : -1),
                rows);
    }

    private static long nonZero(long v) {
        return v > 0 ? v : -1;
    }

    /** A SATA value in its unit: ms as hours, mK as °C, sectors and plain numbers as they are. */
    static String pretty(long value, int unit) {
        return switch (unit) {
            case 2 -> (value / 3_600_000) + " h";
            case 3 -> Long.toString(value);
            case 4 -> Math.round((value - 273_150) / 1000.0) + " °C";
            default -> Long.toString(value);
        };
    }

    static boolean empty(Collection<?> c) {
        return c == null || c.isEmpty();
    }
}
