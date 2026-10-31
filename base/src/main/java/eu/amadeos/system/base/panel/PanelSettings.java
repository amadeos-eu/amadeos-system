package eu.amadeos.system.base.panel;

/**
 * What the Front Panel shows: the clock as a dial or in digits, which cells of the status slot appear, and
 * whether the busy light above Exit shows disk and network activity.
 *
 * @param clock         how the clock in the first slot is drawn
 * @param networkCell   whether the status slot shows the network
 * @param soundCell     whether the status slot shows the volume and the sound devices
 * @param batteryCell   whether the status slot shows the battery (on computers that have one)
 * @param bluetoothCell whether the status slot shows Bluetooth (on computers with an adapter)
 * @param updatesCell   whether the status slot shows waiting software updates
 * @param notificationsCell whether the status slot shows the bell of the notifications
 * @param activityMeter whether the busy light shows disk and network activity besides starting applications
 */
public record PanelSettings(Clock clock, boolean networkCell, boolean soundCell, boolean batteryCell,
                            boolean bluetoothCell, boolean updatesCell, boolean activityMeter,
                            boolean notificationsCell) {
    /** An analog clock, all cells and the disk and network activity in the busy light. */
    public static final PanelSettings DEFAULTS = new PanelSettings(Clock.ANALOG, true, true, true, true, true, true,
            true);

    public enum Clock {
        ANALOG, DIGITAL
    }

    public PanelSettings {
        if (clock == null) clock = DEFAULTS.clock();
    }

    public PanelSettings withClock(Clock newClock) {
        return new PanelSettings(newClock, networkCell, soundCell, batteryCell, bluetoothCell, updatesCell, activityMeter, notificationsCell);
    }

    public PanelSettings withNetworkCell(boolean show) {
        return new PanelSettings(clock, show, soundCell, batteryCell, bluetoothCell, updatesCell, activityMeter, notificationsCell);
    }

    public PanelSettings withSoundCell(boolean show) {
        return new PanelSettings(clock, networkCell, show, batteryCell, bluetoothCell, updatesCell, activityMeter, notificationsCell);
    }

    public PanelSettings withBatteryCell(boolean show) {
        return new PanelSettings(clock, networkCell, soundCell, show, bluetoothCell, updatesCell, activityMeter, notificationsCell);
    }

    public PanelSettings withBluetoothCell(boolean show) {
        return new PanelSettings(clock, networkCell, soundCell, batteryCell, show, updatesCell, activityMeter, notificationsCell);
    }

    public PanelSettings withUpdatesCell(boolean show) {
        return new PanelSettings(clock, networkCell, soundCell, batteryCell, bluetoothCell, show, activityMeter, notificationsCell);
    }

    public PanelSettings withNotificationsCell(boolean show) {
        return new PanelSettings(clock, networkCell, soundCell, batteryCell, bluetoothCell, updatesCell, activityMeter,
                show);
    }

    public PanelSettings withActivityMeter(boolean show) {
        return new PanelSettings(clock, networkCell, soundCell, batteryCell, bluetoothCell, updatesCell, show, notificationsCell);
    }
}
