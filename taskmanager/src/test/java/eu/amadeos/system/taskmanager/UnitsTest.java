package eu.amadeos.system.taskmanager;

import eu.amadeos.system.taskmanager.ui.Units;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitsTest {
    @Test
    void sizesRatesAndShares() {
        assertEquals("512 B", Units.bytes(512, Locale.US));
        assertEquals("1.9 GB", Units.bytes(1.9 * 1024 * 1024 * 1024, Locale.US));
        assertEquals("1,9 GB", Units.bytes(1.9 * 1024 * 1024 * 1024, Locale.GERMANY));
        assertEquals("312 MB", Units.bytes(312L * 1024 * 1024, Locale.US));
        assertEquals("0", Units.rate(0.2, Locale.US));
        assertEquals("48 kB/s", Units.rate(48 * 1024, Locale.US));
        assertEquals("–", Units.rate(-1, Locale.US));
        assertEquals("0 %", Units.percent(0, Locale.US));
        assertEquals("0.4 %", Units.percent(0.4, Locale.US));
        assertEquals("38 %", Units.percent(38.2, Locale.US));
        assertEquals("3 h 12 min", Units.duration(3 * 3600 + 12 * 60 + 5));
        assertEquals("4 min 05 s", Units.duration(245));
    }
}
