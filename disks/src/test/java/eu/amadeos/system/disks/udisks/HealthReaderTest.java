package eu.amadeos.system.disks.udisks;

import eu.amadeos.system.disks.model.Health;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthReaderTest {
    static final Map<String, Object> GOOD_NVME = Map.of("SmartCriticalWarning", List.of(), "SmartPowerOnHours", 4210L,
            "SmartTemperature", 314L, "SmartSelftestStatus", "success");

    static Map<String, Object> log(long used, long spare, long mediaErrors) {
        return Map.of("avail_spare", spare, "spare_thresh", 10L, "percent_used", used, "total_data_written",
                38_600_000_000_000L, "power_cycles", 1377L, "media_errors", mediaErrors, "unsafe_shutdowns", 3L,
                "num_err_log_entries", 0L);
    }

    @Test
    void healthyNvme() {
        Health h = HealthReader.nvme(GOOD_NVME, log(3, 100, 0));
        assertEquals(Health.Verdict.GOOD, h.verdict());
        assertEquals(41, h.temperature());
        assertEquals(3, h.lifeUsed());
        assertEquals(38_600_000_000_000L, h.written());
        assertEquals(4210, h.powerOnHours());
        assertEquals(1377, h.powerCycles());
        assertTrue(h.rows().stream().allMatch(Health.Row::ok));
    }

    @Test
    void wornNvme() {
        assertEquals(Health.Verdict.WATCH, HealthReader.nvme(GOOD_NVME, log(92, 100, 0)).verdict());
        assertEquals(Health.Verdict.REPLACE, HealthReader.nvme(GOOD_NVME, log(100, 100, 0)).verdict());
        assertEquals(Health.Verdict.REPLACE, HealthReader.nvme(GOOD_NVME, log(40, 8, 0)).verdict());
        assertEquals(Health.Verdict.WATCH, HealthReader.nvme(GOOD_NVME, log(3, 100, 2)).verdict());
    }

    @Test
    void criticalWarnings() {
        Map<String, Object> hot = Map.of("SmartCriticalWarning", List.of("temperature"), "SmartTemperature", 360L);
        Health h = HealthReader.nvme(hot, log(3, 100, 0));
        assertEquals(Health.Verdict.WATCH, h.verdict());
        assertEquals(List.of("health.reason.hot"), h.reasons());
        Map<String, Object> ro = Map.of("SmartCriticalWarning", List.of("readonly"));
        Health r = HealthReader.nvme(ro, log(3, 100, 0));
        assertEquals(Health.Verdict.REPLACE, r.verdict());
        assertEquals(List.of("health.reason.nvme.readonly"), r.reasons());
        assertFalse(r.rows().get(0).ok());
    }

    static HealthReader.AtaValue attr(int id, String name, int value, int threshold, long pretty, int unit) {
        return new HealthReader.AtaValue(id, name, value, value, threshold, pretty, unit);
    }

    static final Map<String, Object> ATA = Map.of("SmartFailing", false, "SmartTemperature", 308.15,
            "SmartPowerOnSeconds", 3600L * 12000, "SmartSelftestStatus", "success");

    @Test
    void healthySata() {
        Health h = HealthReader.ata(ATA, List.of(attr(5, "reallocated-sector-count", 100, 10, 0, 3),
                attr(9, "power-on-hours", 99, 0, 12000L * 3_600_000, 2), attr(12, "power-cycle-count", 99, 0, 812, 1),
                attr(241, "total-lbas-written", 99, 0, 1_000_000_000L, 3)));
        assertEquals(Health.Verdict.GOOD, h.verdict());
        assertEquals(35, h.temperature());
        assertEquals(12000, h.powerOnHours());
        assertEquals(812, h.powerCycles());
        assertEquals(512_000_000_000L, h.written());
        assertEquals("12000 h  (99)", h.rows().get(1).value());
    }

    @Test
    void sataWithBadSectors() {
        Health some = HealthReader.ata(ATA, List.of(attr(5, "reallocated-sector-count", 100, 10, 8, 3)));
        assertEquals(Health.Verdict.WATCH, some.verdict());
        assertFalse(some.rows().get(0).ok());
        Health many = HealthReader.ata(ATA, List.of(attr(197, "current-pending-sector", 100, 0, 400, 3)));
        assertEquals(Health.Verdict.REPLACE, many.verdict());
    }

    @Test
    void sataPastItsLimitOrFailing() {
        assertEquals(Health.Verdict.REPLACE, HealthReader.ata(ATA,
                List.of(attr(1, "raw-read-error-rate", 5, 6, 0, 1))).verdict());
        Map<String, Object> failing = Map.of("SmartFailing", true);
        Health h = HealthReader.ata(failing, List.of());
        assertEquals(Health.Verdict.REPLACE, h.verdict());
        assertEquals(List.of("health.reason.failing"), h.reasons());
    }

    @Test
    void prettyUnits() {
        assertEquals("2 h", HealthReader.pretty(2 * 3_600_000L, 2));
        assertEquals("35 °C", HealthReader.pretty(308_150, 4));
        assertEquals("17", HealthReader.pretty(17, 3));
    }
}
