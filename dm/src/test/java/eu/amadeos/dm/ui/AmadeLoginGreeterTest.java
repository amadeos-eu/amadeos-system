package eu.amadeos.dm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the greeter that need no display. */
class AmadeLoginGreeterTest {
    @Test
    void timeZonesAreShownWithTheirOffset() {
        assertEquals("UTC  (UTC)", AmadeLoginGreeter.describeTimeZone("UTC"));
        String berlin = AmadeLoginGreeter.describeTimeZone("Europe/Berlin");
        assertTrue(berlin.equals("Europe/Berlin  (UTC+01:00)") || berlin.equals("Europe/Berlin  (UTC+02:00)"), berlin);
        assertTrue(AmadeLoginGreeter.describeTimeZone("America/Argentina/Buenos_Aires")
                .startsWith("America/Argentina/Buenos Aires  (UTC-03:00)"));
        assertEquals("Mars/Olympus Mons", AmadeLoginGreeter.describeTimeZone("Mars/Olympus_Mons"),
                "a zone the JDK does not know is shown without an offset");
    }
}
