package eu.amadeos.system.disks.model;

import java.util.List;

/**
 * What a drive says about its health (SATA SMART or the NVMe health log), boiled down to a verdict.
 *
 * @param verdict        good, watch it, replace soon, or unknown
 * @param reasons        message keys that explain a verdict other than good
 * @param temperature    °C or -1
 * @param lifeUsed       percent of the rated life used (NVMe), or -1
 * @param written        bytes written over the drive's life, or -1
 * @param powerOnHours   hours switched on, or -1
 * @param powerCycles    starts, or -1
 * @param selfTest       udisks' self-test status: success, inprogress, aborted, fatal, ... or empty
 * @param selfTestLeft   percent of a running self-test still to do, or -1
 * @param rows           the raw values: name, value now, limit, OK
 */
public record Health(Verdict verdict, List<String> reasons, int temperature, int lifeUsed, long written,
        long powerOnHours, long powerCycles, String selfTest, int selfTestLeft, List<Row> rows) {

    public enum Verdict {
        GOOD, WATCH, REPLACE, UNKNOWN
    }

    /** One raw value. */
    public record Row(String name, String value, String limit, boolean ok) {
    }

    public Health {
        reasons = List.copyOf(reasons);
        rows = List.copyOf(rows);
    }

    public static final Health UNKNOWN = new Health(Verdict.UNKNOWN, List.of(), -1, -1, -1, -1, -1, "", -1,
            List.of());

    public boolean selfTestRunning() {
        return "inprogress".equals(selfTest);
    }
}
