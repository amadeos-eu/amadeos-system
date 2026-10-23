package eu.amadeos.system.settings;

import eu.amadeos.system.input.InputSettings;
import eu.amadeos.system.input.XCommands;
import eu.amadeos.system.settings.InputControls.LabeledSlider;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * Keyboard: auto repeat (with delay and rate) and key click volume, plus a field to try them out.
 */
final class KeyboardPage extends SettingsPage {
    private final InputControls controls;
    private final JCheckBox autoRepeat = new JCheckBox(SettingsTexts.get("keyboard.repeat"));
    private final LabeledSlider delay;
    private final LabeledSlider rate;
    private final LabeledSlider click;

    KeyboardPage(Consumer<String> status, InputControls controls) {
        super(SettingsCategory.KEYBOARD, status);
        this.controls = controls;
        InputSettings.Keyboard k = controls.load().keyboard();
        delay = new LabeledSlider(100, 2000, k.repeatDelayMs(), v -> v + " ms");
        rate = new LabeledSlider(1, 100, k.repeatRate(), v -> v + " /s");
        click = new LabeledSlider(0, 100, k.clickVolume(), v -> v == 0 ? SettingsTexts.get("value.off") : v + " %");
        autoRepeat.setSelected(k.autoRepeat());
        autoRepeat.addActionListener(e -> updateEnabled());

        JTextField test = new JTextField(24);
        test.setToolTipText(SettingsTexts.get("keyboard.test.tooltip"));
        JButton defaults = new JButton(SettingsTexts.get("button.defaults"));
        defaults.addActionListener(e -> show(InputSettings.Keyboard.DEFAULTS));

        JPanel form = InputControls.form(
                "", autoRepeat,
                SettingsTexts.get("keyboard.delay"), delay,
                SettingsTexts.get("keyboard.rate"), rate,
                SettingsTexts.get("keyboard.click"), click,
                SettingsTexts.get("keyboard.test"), test);
        form.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        JPanel defaultsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        defaultsRow.add(defaults);
        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(defaultsRow, BorderLayout.SOUTH);
        updateEnabled();
        layoutPage(content);
    }

    private void show(InputSettings.Keyboard k) {
        autoRepeat.setSelected(k.autoRepeat());
        delay.setValue(k.repeatDelayMs());
        rate.setValue(k.repeatRate());
        click.setValue(k.clickVolume());
        updateEnabled();
    }

    private void updateEnabled() {
        delay.setEnabled(autoRepeat.isSelected());
        rate.setEnabled(autoRepeat.isSelected());
    }

    InputSettings.Keyboard settings() {
        return new InputSettings.Keyboard(autoRepeat.isSelected(), delay.value(), rate.value(), click.value());
    }

    @Override
    protected String apply() {
        InputSettings.Keyboard k = settings();
        return controls.saveAndApply(controls.load().withKeyboard(k), XCommands.keyboard(k), SettingsCategory.KEYBOARD.label());
    }
}
