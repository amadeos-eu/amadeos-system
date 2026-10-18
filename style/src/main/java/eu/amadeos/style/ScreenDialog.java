package eu.amadeos.style;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.screen.SaverCanvas;
import eu.amadeos.screen.SaverWindow;
import eu.amadeos.screen.ScreenSettings;
import eu.amadeos.screen.ScreenSettingsStore;
import eu.amadeos.screen.modules.SaverModules;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Screen: screen saver (on/off, minutes, module with live preview, test) and screen lock (on/off, minutes),
 * as in CDE's Style Manager. The screen saver daemon picks up the saved settings by itself.
 */
final class ScreenDialog extends StyleDialog {
    private static final String RANDOM_LABEL = "Random";

    private final ScreenSettingsStore store;
    private final JCheckBox saverOn = new JCheckBox("Screen saver");
    private final JSpinner saverMinutes = minutes();
    private final JCheckBox lockOn = new JCheckBox("Screen lock");
    private final JSpinner lockMinutes = minutes();
    private final DefaultListModel<Object> modules = new DefaultListModel<>();
    private final JList<Object> moduleList = new JList<>(modules);
    private final SaverCanvas preview;
    private final Random random = new Random();

    ScreenDialog(JFrame owner, Consumer<String> status, ScreenSettingsStore store) {
        super(owner, StyleCategory.SCREEN, status);
        this.store = store;
        ScreenSettings settings = store.load();

        modules.addElement(RANDOM_LABEL);
        SaverModules.ALL.forEach(modules::addElement);
        moduleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        moduleList.setVisibleRowCount(6);
        select(settings.module());

        preview = new SaverCanvas(SaverModules.create(selectedId(), random));
        preview.setPreferredSize(new Dimension(240, 150));
        moduleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) preview.setModule(SaverModules.create(selectedId(), random));
        });

        saverOn.setSelected(settings.saverEnabled());
        saverMinutes.setValue(settings.saverMinutes());
        lockOn.setSelected(settings.lockEnabled());
        lockMinutes.setValue(settings.lockMinutes());
        saverOn.addActionListener(e -> updateEnabled());
        lockOn.addActionListener(e -> updateEnabled());

        JButton test = new JButton("Test");
        test.setToolTipText("Show the selected screen saver full screen; any key or mouse click ends it");
        test.addActionListener(e -> {
            SaverWindow window = new SaverWindow(SaverModules.create(selectedId(), random));
            window.dismissOnInput(() -> {
            });
            window.showSaver();
        });

        JPanel saverBox = new JPanel(new BorderLayout(10, 6));
        saverBox.setBorder(BorderFactory.createTitledBorder("Screen Saver"));
        saverBox.add(row(saverOn, new JLabel("Start after"), saverMinutes, new JLabel("minutes")), BorderLayout.NORTH);
        JScrollPane list = new JScrollPane(moduleList);
        list.setPreferredSize(new Dimension(160, 150));
        saverBox.add(list, BorderLayout.WEST);
        JPanel previewBox = new JPanel(new BorderLayout(0, 6));
        JPanel framed = new JPanel(new BorderLayout());
        framed.setBorder(AmadeUI.sunken());
        framed.add(preview);
        previewBox.add(framed, BorderLayout.CENTER);
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        testRow.add(test);
        previewBox.add(testRow, BorderLayout.SOUTH);
        saverBox.add(previewBox, BorderLayout.CENTER);

        JPanel lockBox = new JPanel(new GridLayout(0, 1, 0, 4));
        lockBox.setBorder(BorderFactory.createTitledBorder("Screen Lock"));
        lockBox.add(row(lockOn, new JLabel("Lock after"), lockMinutes, new JLabel("minutes without input")));
        JLabel hint = new JLabel("Unlocking needs your login password. Lock at once with the front panel's lock button.");
        hint.setFont(hint.getFont().deriveFont(11f));
        lockBox.add(hint);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(saverBox, BorderLayout.CENTER);
        content.add(lockBox, BorderLayout.SOUTH);
        updateEnabled();
        layoutDialog(content);

        preview.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                preview.stop();
            }
        });
    }

    private static JSpinner minutes() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(10, 1, ScreenSettings.MAX_MINUTES, 1));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        return spinner;
    }

    private static JPanel row(java.awt.Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (java.awt.Component c : components) row.add(c);
        return row;
    }

    private void updateEnabled() {
        saverMinutes.setEnabled(saverOn.isSelected());
        moduleList.setEnabled(saverOn.isSelected());
        lockMinutes.setEnabled(lockOn.isSelected());
    }

    private void select(String id) {
        if (ScreenSettings.RANDOM.equals(id)) {
            moduleList.setSelectedIndex(0);
            return;
        }
        for (int i = 1; i < modules.size(); i++) {
            if (((SaverModules.Entry) modules.get(i)).id().equals(id)) {
                moduleList.setSelectedIndex(i);
                return;
            }
        }
        moduleList.setSelectedIndex(1);
    }

    String selectedId() {
        Object value = moduleList.getSelectedValue();
        return value instanceof SaverModules.Entry entry ? entry.id() : ScreenSettings.RANDOM;
    }

    ScreenSettings settings() {
        return new ScreenSettings(saverOn.isSelected(), (Integer) saverMinutes.getValue(), lockOn.isSelected(),
                (Integer) lockMinutes.getValue(), selectedId());
    }

    @Override
    protected String apply() {
        ScreenSettings settings = settings();
        store.save(settings);
        String saver = settings.saverEnabled() ? "saver after " + settings.saverMinutes() + " min" : "saver off";
        String lock = settings.lockEnabled() ? "lock after " + settings.lockMinutes() + " min" : "lock off";
        return "Screen: " + saver + ", " + lock;
    }
}
