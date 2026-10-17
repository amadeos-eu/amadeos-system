package org.datazuul.amadeos.style;

import org.datazuul.amadeos.input.InputApplier;
import org.datazuul.amadeos.input.InputSettings;
import org.datazuul.amadeos.input.InputSettingsStore;
import org.datazuul.amadeos.input.XCommands;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Shared parts of the Keyboard, Mouse and Beep dialogs: where the settings are stored, how they are
 * applied, and a labelled slider that shows its value.
 */
final class InputControls {
    private final InputSettingsStore store;
    private final InputApplier applier;

    InputControls(InputSettingsStore store, InputApplier applier) {
        this.store = store;
        this.applier = applier;
    }

    InputSettings load() {
        return store.load();
    }

    /** Saves the settings and applies the given commands; returns a status message. */
    String saveAndApply(InputSettings settings, List<XCommands.Command> commands, String what) {
        store.save(settings);
        List<XCommands.Command> failed = applier.apply(commands);
        return failed.isEmpty() ? what + " applied" : what + " saved; could not run: " + failed.getFirst().args().getFirst();
    }

    /** Applies commands without saving, e.g. to try out the bell. */
    void preview(List<XCommands.Command> commands) {
        applier.apply(commands);
    }

    int pointerButtons() {
        return applier.pointerButtons();
    }

    /** A slider with its current value as text on the right. */
    static final class LabeledSlider extends JPanel {
        final JSlider slider;

        LabeledSlider(int min, int max, int value, IntFunction<String> format) {
            super(new BorderLayout(8, 0));
            slider = new JSlider(min, max, Math.max(min, Math.min(max, value)));
            JLabel label = new JLabel(format.apply(slider.getValue()));
            label.setPreferredSize(new Dimension(70, label.getPreferredSize().height));
            slider.addChangeListener(e -> label.setText(format.apply(slider.getValue())));
            add(slider, BorderLayout.CENTER);
            add(label, BorderLayout.EAST);
        }

        int value() {
            return slider.getValue();
        }

        void setValue(int value) {
            slider.setValue(value);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            slider.setEnabled(enabled);
        }
    }

    /** Two-column form: label, control. */
    static JPanel form(Object... labelsAndControls) {
        JPanel form = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(4, 0, 4, 8);
        for (int i = 0; i < labelsAndControls.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.weightx = 0;
            c.anchor = java.awt.GridBagConstraints.LINE_END;
            c.fill = java.awt.GridBagConstraints.NONE;
            Object label = labelsAndControls[i];
            form.add(label instanceof java.awt.Component comp ? comp : new JLabel((String) label), c);
            c.gridx = 1;
            c.weightx = 1;
            c.anchor = java.awt.GridBagConstraints.LINE_START;
            c.fill = java.awt.GridBagConstraints.HORIZONTAL;
            form.add((java.awt.Component) labelsAndControls[i + 1], c);
        }
        return form;
    }
}
