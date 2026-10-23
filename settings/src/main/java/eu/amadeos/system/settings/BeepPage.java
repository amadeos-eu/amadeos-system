package eu.amadeos.system.settings;

import eu.amadeos.system.input.InputSettings;
import eu.amadeos.system.input.XCommands;
import eu.amadeos.system.settings.InputControls.LabeledSlider;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.util.function.Consumer;

/**
 * Beep: volume, tone and duration of the X bell. "Test" rings the bell with the values shown; Cancel
 * restores the saved ones.
 */
final class BeepPage extends SettingsPage {
    private final InputControls controls;
    private final LabeledSlider volume;
    private final LabeledSlider pitch;
    private final LabeledSlider duration;
    private boolean previewed;

    BeepPage(Consumer<String> status, InputControls controls) {
        super(SettingsCategory.BEEP, status);
        this.controls = controls;
        InputSettings.Beep b = controls.load().beep();
        volume = new LabeledSlider(0, 100, b.volume(), v -> v == 0 ? SettingsTexts.get("value.off") : v + " %");
        pitch = new LabeledSlider(55, 4000, b.pitchHz(), v -> v + " Hz");
        duration = new LabeledSlider(10, 2000, b.durationMs(), v -> v + " ms");

        JButton test = new JButton(SettingsTexts.get("button.test"));
        test.addActionListener(e -> {
            controls.preview(XCommands.beep(settings()));
            previewed = true;
            Toolkit.getDefaultToolkit().beep();
        });
        JButton defaults = new JButton(SettingsTexts.get("button.defaults"));
        defaults.addActionListener(e -> {
            InputSettings.Beep d = InputSettings.Beep.DEFAULTS;
            volume.setValue(d.volume());
            pitch.setValue(d.pitchHz());
            duration.setValue(d.durationMs());
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(test);
        buttons.add(defaults);
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.add(InputControls.form(SettingsTexts.get("beep.volume"), volume, SettingsTexts.get("beep.tone"), pitch,
                SettingsTexts.get("beep.duration"), duration), BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        layoutPage(content);
    }

    InputSettings.Beep settings() {
        return new InputSettings.Beep(volume.value(), pitch.value(), duration.value());
    }

    @Override
    protected String apply() {
        InputSettings.Beep b = settings();
        previewed = false;
        return controls.saveAndApply(controls.load().withBeep(b), XCommands.beep(b), SettingsCategory.BEEP.label());
    }

    @Override
    protected void cancelled() {
        if (previewed) controls.preview(XCommands.beep(controls.load().beep()));
    }
}
