package eu.amadeos.system.settings;

import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.base.power.PowerAction;
import eu.amadeos.system.base.power.PowerControl;
import eu.amadeos.system.base.power.PowerSettings;
import eu.amadeos.system.base.power.PowerSettingsStore;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Power: when the screen turns off and the computer suspends while nobody uses it, separately on battery and
 * plugged in; what closing the lid and the power button do; the warning and the action for a nearly empty battery;
 * and whether the computer wakes up locked. The session's screen and power daemon and the window manager (power
 * button) pick up the saved {@code power.conf} by themselves. On a computer without a battery only the
 * "plugged in" column and the power button are shown.
 */
final class PowerPage extends SettingsPage {
    private final PowerSettingsStore store;
    private final boolean laptop;
    private final boolean canHibernate;
    private final JComboBox<Integer> batteryScreen = minutes();
    private final JComboBox<Integer> mainsScreen = minutes();
    private final JComboBox<Integer> batterySuspend = minutes();
    private final JComboBox<Integer> mainsSuspend = minutes();
    private final JComboBox<PowerAction> batteryLid;
    private final JComboBox<PowerAction> mainsLid;
    private final JCheckBox ignoreDocked = new JCheckBox(SettingsTexts.get("power.lid.docked"));
    private final JComboBox<PowerAction> powerKey;
    private final JCheckBox warn = new JCheckBox(SettingsTexts.get("power.low.warn"));
    private final JSpinner warnPercent = percent(10);
    private final JSpinner lowPercent = percent(5);
    private final JComboBox<PowerAction> lowAction;
    private final JCheckBox lockOnWake = new JCheckBox(SettingsTexts.get("power.lock"));

    PowerPage(Consumer<String> status, PowerSettingsStore store, PowerControl power,
              Supplier<Optional<BatteryStatus>> battery) {
        super(SettingsCategory.POWER, status);
        this.store = store;
        Optional<BatteryStatus> now = battery.get();
        this.laptop = now.isPresent();
        this.canHibernate = power.canHibernate();
        PowerSettings s = store.load();

        batteryLid = actions(PowerAction.LID, s.battery().lid());
        mainsLid = actions(PowerAction.LID, s.mains().lid());
        powerKey = actions(PowerAction.POWER_KEY, s.powerKey());
        lowAction = actions(PowerAction.LOW_BATTERY, s.lowAction());
        batteryScreen.setSelectedItem(s.battery().screenOffMinutes());
        mainsScreen.setSelectedItem(s.mains().screenOffMinutes());
        batterySuspend.setSelectedItem(s.battery().suspendMinutes());
        mainsSuspend.setSelectedItem(s.mains().suspendMinutes());
        ignoreDocked.setSelected(s.lidIgnoreDocked());
        warn.setSelected(s.warnPercent() > 0);
        warnPercent.setValue(s.warnPercent() > 0 ? s.warnPercent() : 10);
        lowPercent.setValue(s.lowPercent());
        lockOnWake.setSelected(s.lockOnWake());
        warn.addActionListener(e -> warnPercent.setEnabled(warn.isSelected()));
        warnPercent.setEnabled(warn.isSelected());

        List<JComponent> parts = new ArrayList<>();
        now.ifPresent(b -> parts.add(note(batteryLine(b))));

        // --8<-- [start:idle]
        JPanel idle = table();
        int row = 0;
        if (laptop) header(idle, row++);
        cells(idle, row++, SettingsTexts.get("power.screen"), batteryScreen, mainsScreen);
        cells(idle, row, SettingsTexts.get("power.suspend"), batterySuspend, mainsSuspend);
        parts.add(section(SettingsTexts.get("power.idle.title"), idle,
                note(SettingsTexts.get("power.idle.hint"))));
        // --8<-- [end:idle]

        JPanel buttons = table();
        row = 0;
        if (laptop) {
            header(buttons, row++);
            cells(buttons, row++, SettingsTexts.get("power.lid"), batteryLid, mainsLid);
        }
        cells(buttons, row, SettingsTexts.get("power.key"), powerKey, null);
        sameLabelWidth(idle, buttons);
        List<Component> buttonLines = new ArrayList<>(List.of(buttons));
        if (laptop) buttonLines.add(line(ignoreDocked));
        parts.add(section(SettingsTexts.get(laptop ? "power.buttons.title" : "power.button.title"), buttonLines.toArray(Component[]::new)));

        if (laptop) {
            List<Component> low = new ArrayList<>(List.of(
                    line(warn, warnPercent, new JLabel("%")),
                    line(new JLabel(SettingsTexts.get("power.low.at")), lowPercent, new JLabel("%"), lowAction)));
            if (!canHibernate) low.add(note(SettingsTexts.get("power.hibernate.missing")));
            parts.add(section(SettingsTexts.get("power.low.title"), low.toArray(Component[]::new)));
        }

        parts.add(section(SettingsTexts.get("power.wake.title"), line(lockOnWake),
                note(SettingsTexts.get("power.wake.hint"))));
        layoutSections(parts.toArray(JComponent[]::new));
    }

    private static String batteryLine(BatteryStatus b) {
        String state = SettingsTexts.get("power.battery." + b.state().name().toLowerCase(Locale.ROOT));
        if (b.minutesLeft() < 0 || b.state() == BatteryStatus.State.FULL) {
            return SettingsTexts.format("power.battery", b.percent(), state);
        }
        return SettingsTexts.format("power.battery.time", b.percent(), state, b.minutesLeft() / 60, b.minutesLeft() % 60);
    }

    /** The first columns of both tables as wide as the wider one, so that their choices line up. */
    private static void sameLabelWidth(JPanel... tables) {
        int width = 0;
        for (JPanel t : tables) {
            for (Component c : t.getComponents()) {
                if (((GridBagLayout) t.getLayout()).getConstraints(c).gridx == 0) width = Math.max(width, c.getPreferredSize().width);
            }
        }
        for (JPanel t : tables) {
            for (Component c : t.getComponents()) {
                if (((GridBagLayout) t.getLayout()).getConstraints(c).gridx == 0 && c instanceof JLabel l) {
                    l.setPreferredSize(new java.awt.Dimension(width, l.getPreferredSize().height));
                }
            }
            // A last, empty column takes the rest of the width, so that the table stays on the left.
            GridBagConstraints filler = new GridBagConstraints();
            filler.gridx = 3;
            filler.gridy = 0;
            filler.weightx = 1;
            t.add(javax.swing.Box.createHorizontalGlue(), filler);
        }
    }

    private static JPanel table() {
        JPanel table = new JPanel(new GridBagLayout());
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        return table;
    }

    private static void header(JPanel table, int row) {
        cell(table, row, 1, bold(SettingsTexts.get("power.on.battery")));
        cell(table, row, 2, bold(SettingsTexts.get("power.on.mains")));
    }

    private static JLabel bold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        return label;
    }

    /** A row of the table: the label, the battery's control (left out on desktops) and the mains' control. */
    private void cells(JPanel table, int row, String label, JComponent battery, JComponent mains) {
        cell(table, row, 0, new JLabel(label));
        if (mains == null) {
            cell(table, row, 1, battery);
            return;
        }
        if (laptop) cell(table, row, 1, battery);
        cell(table, row, laptop ? 2 : 1, mains);
    }

    private static void cell(JPanel table, int row, int column, Component component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.fill = column == 0 ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 8, 2, 8);
        table.add(component, c);
    }

    private static JComboBox<Integer> minutes() {
        JComboBox<Integer> box = new JComboBox<>();
        for (int m : PowerSettings.MINUTES) box.addItem(m);
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(minutesText((Integer) value));
                return this;
            }
        });
        return box;
    }

    static String minutesText(int minutes) {
        if (minutes == PowerSettings.NEVER) return SettingsTexts.get("power.never");
        if (minutes >= 60 && minutes % 60 == 0) return SettingsTexts.format("power.hours", minutes / 60);
        return SettingsTexts.format("power.minutes", minutes);
    }

    /** A choice of actions; Hibernate only where the computer can. */
    private JComboBox<PowerAction> actions(PowerAction[] offered, PowerAction current) {
        JComboBox<PowerAction> box = new JComboBox<>();
        for (PowerAction a : offered) {
            if (a != PowerAction.HIBERNATE || canHibernate) box.addItem(a);
        }
        // Without hibernation the daemon falls back: the lid and the button suspend, a nearly empty battery shuts down.
        if (current == PowerAction.HIBERNATE && !canHibernate) {
            current = offered == PowerAction.LOW_BATTERY ? PowerAction.SHUTDOWN : PowerAction.SUSPEND;
        }
        box.setSelectedItem(current);
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof PowerAction a) setText(SettingsTexts.get("power.action." + a.key()));
                return this;
            }
        });
        return box;
    }

    private static JSpinner percent(int value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 1, 50, 1));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        return spinner;
    }

    PowerSettings settings() {
        PowerSettings old = store.load();
        PowerSettings.Profile battery = laptop
                ? new PowerSettings.Profile((Integer) batteryScreen.getSelectedItem(),
                (Integer) batterySuspend.getSelectedItem(), (PowerAction) batteryLid.getSelectedItem())
                : old.battery();
        PowerSettings.Profile mains = new PowerSettings.Profile((Integer) mainsScreen.getSelectedItem(),
                (Integer) mainsSuspend.getSelectedItem(),
                laptop ? (PowerAction) mainsLid.getSelectedItem() : old.mains().lid());
        int low = (Integer) lowPercent.getValue();
        int warnAt = warn.isSelected() ? Math.max((Integer) warnPercent.getValue(), low + 1) : 0;
        return new PowerSettings(battery, mains, (PowerAction) powerKey.getSelectedItem(), ignoreDocked.isSelected(),
                lockOnWake.isSelected(), warnAt, low, (PowerAction) lowAction.getSelectedItem());
    }

    @Override
    protected String apply() {
        PowerSettings s = settings();
        store.save(s);
        PowerSettings.Profile p = laptop ? s.battery() : s.mains();
        return SettingsTexts.format("power.status", minutesText(p.screenOffMinutes()), minutesText(p.suspendMinutes()));
    }
}
