package eu.amadeos.system.disks.ui;

import eu.amadeos.system.disks.model.Layout;
import eu.amadeos.system.disks.model.Sizes;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * A size between a minimum and a maximum: a slider and a number in GB (with one decimal) or, for less than 10 GB,
 * in MB. Both move together. The value is aligned to whole MiB, as partitions are.
 */
final class SizeField extends JPanel {
    private static final long serialVersionUID = 1L;
    private final long min;
    private final long max;
    private final long unit;
    private final JSlider slider = new JSlider(0, 1000);
    private final JSpinner number;
    private boolean syncing;

    SizeField(Context c, long min, long max, long value) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        this.min = min;
        this.max = Math.max(min, max);
        this.unit = max >= 10 * Sizes.GB ? Sizes.GB : Sizes.MB;
        double step = unit == Sizes.GB ? 0.1 : 1;
        number = new JSpinner(new SpinnerNumberModel(round(value), round(min), round(this.max), step));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(number, unit == Sizes.GB ? "0.0" : "0");
        number.setEditor(editor);
        editor.getTextField().setColumns(6);
        slider.setPreferredSize(new Dimension(200, slider.getPreferredSize().height));
        add(slider);
        add(number);
        add(new JLabel(unit == Sizes.GB ? "GB" : "MB"));
        setSlider(value);
        slider.addChangeListener(e -> {
            if (syncing) return;
            syncing = true;
            long v = min + Math.round((this.max - min) * slider.getValue() / 1000.0);
            number.setValue(round(v));
            syncing = false;
        });
        number.addChangeListener(e -> {
            if (syncing) return;
            syncing = true;
            setSlider(value());
            syncing = false;
        });
    }

    private double round(long bytes) {
        double v = (double) bytes / unit;
        return unit == Sizes.GB ? Math.floor(v * 10) / 10 : Math.floor(v);
    }

    private void setSlider(long v) {
        slider.setValue(max == min ? 1000 : (int) Math.round(1000.0 * (v - min) / (max - min)));
    }

    /** The chosen size in bytes, aligned to MiB and kept between minimum and maximum. */
    long value() {
        double shown = ((Number) number.getValue()).doubleValue();
        long bytes = (long) (shown * unit);
        if (slider.getValue() == 1000) bytes = max;
        bytes = Math.max(min, Math.min(max, bytes));
        return bytes == max ? max : Math.max(min, Layout.alignDown(bytes));
    }
}
