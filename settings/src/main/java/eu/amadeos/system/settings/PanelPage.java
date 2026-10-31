package eu.amadeos.system.settings;

import eu.amadeos.system.base.panel.PanelSettings;
import eu.amadeos.system.base.panel.PanelSettingsStore;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import java.util.function.Consumer;

/**
 * Front Panel: the clock as a dial or in digits, which cells the status slot shows, and whether the busy light
 * above Exit shows disk and network activity. Writes
 * {@code panel.conf}, which the panel follows at once.
 */
final class PanelPage extends SettingsPage {
    private final PanelSettingsStore store;
    private final JRadioButton analog = new JRadioButton(SettingsTexts.get("panel.clock.analog"));
    private final JRadioButton digital = new JRadioButton(SettingsTexts.get("panel.clock.digital"));
    private final JCheckBox network = new JCheckBox(SettingsTexts.get("panel.cell.network"));
    private final JCheckBox sound = new JCheckBox(SettingsTexts.get("panel.cell.sound"));
    private final JCheckBox battery = new JCheckBox(SettingsTexts.get("panel.cell.battery"));
    private final JCheckBox bluetooth = new JCheckBox(SettingsTexts.get("panel.cell.bluetooth"));
    private final JCheckBox notifications = new JCheckBox(SettingsTexts.get("panel.cell.notifications"));
    private final JCheckBox updates = new JCheckBox(SettingsTexts.get("panel.cell.updates"));
    private final JCheckBox activity = new JCheckBox(SettingsTexts.get("panel.activity.meter"));

    PanelPage(Consumer<String> status, PanelSettingsStore store) {
        super(SettingsCategory.PANEL, status);
        this.store = store;
        PanelSettings settings = store.load();
        ButtonGroup clock = new ButtonGroup();
        clock.add(analog);
        clock.add(digital);
        (settings.clock() == PanelSettings.Clock.DIGITAL ? digital : analog).setSelected(true);
        network.setSelected(settings.networkCell());
        sound.setSelected(settings.soundCell());
        battery.setSelected(settings.batteryCell());
        bluetooth.setSelected(settings.bluetoothCell());
        notifications.setSelected(settings.notificationsCell());
        updates.setSelected(settings.updatesCell());
        activity.setSelected(settings.activityMeter());

        layoutSections(
                section(SettingsTexts.get("panel.clock.title"), analog, digital),
                section(SettingsTexts.get("panel.cells.title"), network, sound, battery, bluetooth, notifications,
                        updates,
                        note(SettingsTexts.get("panel.cells.hint"))),
                section(SettingsTexts.get("panel.activity.title"), activity,
                        note(SettingsTexts.get("panel.activity.hint"))),
                note(SettingsTexts.get("panel.hint")));
    }

    PanelSettings selected() {
        return new PanelSettings(digital.isSelected() ? PanelSettings.Clock.DIGITAL : PanelSettings.Clock.ANALOG,
                network.isSelected(), sound.isSelected(), battery.isSelected(), bluetooth.isSelected(),
                updates.isSelected(), activity.isSelected(), notifications.isSelected());
    }

    @Override
    protected String apply() {
        store.save(selected());
        return SettingsTexts.format("status.applied", SettingsCategory.PANEL.label());
    }
}
