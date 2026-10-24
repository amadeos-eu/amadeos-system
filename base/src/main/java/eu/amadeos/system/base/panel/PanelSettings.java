package eu.amadeos.system.base.panel;

/**
 * What the Front Panel shows: the clock as a dial or in digits, which cells of the status slot appear, and
 * whether the busy light above Exit shows disk and network activity.
 *
 * @param clock         how the clock in the first slot is drawn
 * @param networkCell   whether the status slot shows the network
 * @param soundCell     whether the status slot shows the volume and the sound devices
 * @param updatesCell   whether the status slot shows waiting software updates
 * @param activityMeter whether the busy light shows disk and network activity besides starting applications
 */
public record PanelSettings(Clock clock, boolean networkCell, boolean soundCell, boolean updatesCell, boolean activityMeter) {
    /** An analog clock, all cells and the disk and network activity in the busy light. */
    public static final PanelSettings DEFAULTS = new PanelSettings(Clock.ANALOG, true, true, true, true);

    public enum Clock {
        ANALOG, DIGITAL
    }

    public PanelSettings {
        if (clock == null) clock = DEFAULTS.clock();
    }

    public PanelSettings withClock(Clock newClock) {
        return new PanelSettings(newClock, networkCell, soundCell, updatesCell, activityMeter);
    }

    public PanelSettings withNetworkCell(boolean show) {
        return new PanelSettings(clock, show, soundCell, updatesCell, activityMeter);
    }

    public PanelSettings withSoundCell(boolean show) {
        return new PanelSettings(clock, networkCell, show, updatesCell, activityMeter);
    }

    public PanelSettings withUpdatesCell(boolean show) {
        return new PanelSettings(clock, networkCell, soundCell, show, activityMeter);
    }

    public PanelSettings withActivityMeter(boolean show) {
        return new PanelSettings(clock, networkCell, soundCell, updatesCell, show);
    }
}
