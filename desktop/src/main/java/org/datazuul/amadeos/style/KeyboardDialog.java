package org.datazuul.amadeos.style;

import org.datazuul.amadeos.input.InputSettings;
import org.datazuul.amadeos.input.XCommands;
import org.datazuul.amadeos.style.InputControls.LabeledSlider;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * Keyboard: auto repeat (with delay and rate) and key click volume, plus a field to try them out.
 */
final class KeyboardDialog extends StyleDialog {
    private final InputControls controls;
    private final JCheckBox autoRepeat = new JCheckBox("Auto Repeat");
    private final LabeledSlider delay;
    private final LabeledSlider rate;
    private final LabeledSlider click;

    KeyboardDialog(JFrame owner, Consumer<String> status, InputControls controls) {
        super(owner, StyleCategory.KEYBOARD, status);
        this.controls = controls;
        InputSettings.Keyboard k = controls.load().keyboard();
        delay = new LabeledSlider(100, 2000, k.repeatDelayMs(), v -> v + " ms");
        rate = new LabeledSlider(1, 100, k.repeatRate(), v -> v + " /s");
        click = new LabeledSlider(0, 100, k.clickVolume(), v -> v == 0 ? "Off" : v + " %");
        autoRepeat.setSelected(k.autoRepeat());
        autoRepeat.addActionListener(e -> updateEnabled());

        JTextField test = new JTextField(24);
        test.setToolTipText("Hold a key down here after Apply to try the repeat");
        JButton defaults = new JButton("Defaults");
        defaults.addActionListener(e -> show(InputSettings.Keyboard.DEFAULTS));

        JPanel form = InputControls.form(
                "", autoRepeat,
                "Delay before repeat:", delay,
                "Repeat rate:", rate,
                "Click volume:", click,
                "Test:", test);
        form.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        JPanel defaultsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        defaultsRow.add(defaults);
        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(defaultsRow, BorderLayout.SOUTH);
        updateEnabled();
        layoutDialog(content);
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
        return controls.saveAndApply(controls.load().withKeyboard(k), XCommands.keyboard(k), "Keyboard");
    }
}
