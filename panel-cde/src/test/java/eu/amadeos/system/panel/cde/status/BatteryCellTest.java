package eu.amadeos.system.panel.cde.status;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.base.power.BatteryStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryCellTest {
    private static final Messages EN = Messages.load(BatteryCell.class, NetworkCell.DESKTOP_TEXTS, Locale.ENGLISH);
    private static final Messages DE = Messages.load(BatteryCell.class, NetworkCell.DESKTOP_TEXTS, Locale.GERMAN);

    @Test
    void tooltipGivesPercentageAndTime() {
        BatteryCell cell = new BatteryCell(Optional::empty, EN);
        assertEquals("Battery 73 %, about 2 h 05 min left",
                cell.describe(new BatteryStatus(73, BatteryStatus.State.DISCHARGING, 125)));
        assertEquals("Battery 12 %", cell.describe(new BatteryStatus(12, BatteryStatus.State.DISCHARGING, -1)));
        assertEquals("Battery 40 %, charging, full in about 45 min",
                cell.describe(new BatteryStatus(40, BatteryStatus.State.CHARGING, 45)));
        assertEquals("Battery 100 %, plugged in", cell.describe(new BatteryStatus(100, BatteryStatus.State.FULL, -1)));
        assertEquals("Akku 80 %, am Netz, lädt nicht", new BatteryCell(Optional::empty, DE)
                .describe(new BatteryStatus(80, BatteryStatus.State.NOT_CHARGING, -1)));
    }

    @Test
    void shownOnlyWithABattery() {
        AtomicInteger relayouts = new AtomicInteger();
        BatteryCell cell = new BatteryCell(Optional::empty, EN);
        cell.onAvailabilityChanged(relayouts::incrementAndGet);
        assertFalse(cell.available());
        assertNull(cell.component().getToolTipText());

        cell.show(Optional.of(new BatteryStatus(60, BatteryStatus.State.DISCHARGING, -1)));
        assertTrue(cell.available());
        assertEquals("Battery 60 %", cell.component().getToolTipText());
        cell.show(Optional.of(new BatteryStatus(59, BatteryStatus.State.DISCHARGING, -1)));
        assertEquals(1, relayouts.get(), "a new reading alone does not lay out the slot again");

        cell.show(Optional.empty());
        assertFalse(cell.available());
        assertEquals(2, relayouts.get());
    }

    @Test
    void fillTurnsYellowAndRed() {
        assertEquals(BatteryIcons.GREEN, BatteryIcons.fillColor(51));
        assertEquals(BatteryIcons.YELLOW, BatteryIcons.fillColor(50));
        assertEquals(BatteryIcons.YELLOW, BatteryIcons.fillColor(21));
        assertEquals(BatteryIcons.RED, BatteryIcons.fillColor(20));
        assertEquals(BatteryIcons.RED, BatteryIcons.fillColor(0));
    }
}
