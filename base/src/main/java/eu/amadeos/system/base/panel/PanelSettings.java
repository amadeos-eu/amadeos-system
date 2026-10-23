package eu.amadeos.system.base.panel;

/**
 * What the Front Panel shows: the clock as a dial or in digits, and which cells of the status slot appear.
 *
 * @param clock        how the clock in the first slot is drawn
 * @param networkCell  whether the status slot shows the network
 * @param updatesCell  whether the status slot shows waiting software updates
 */
public record PanelSettings(Clock clock, boolean networkCell, boolean updatesCell) {
    /** The panel as it was before the settings existed: an analog clock and both cells. */
    public static final PanelSettings DEFAULTS = new PanelSettings(Clock.ANALOG, true, true);

    public enum Clock {
        ANALOG, DIGITAL
    }

    public PanelSettings {
        if (clock == null) clock = DEFAULTS.clock();
    }

    public PanelSettings withClock(Clock newClock) {
        return new PanelSettings(newClock, networkCell, updatesCell);
    }

    public PanelSettings withNetworkCell(boolean show) {
        return new PanelSettings(clock, show, updatesCell);
    }

    public PanelSettings withUpdatesCell(boolean show) {
        return new PanelSettings(clock, networkCell, show);
    }
}
