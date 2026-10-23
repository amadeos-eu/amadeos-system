package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.screen.SaverCanvas;
import eu.amadeos.system.screen.SaverWindow;
import eu.amadeos.system.screen.ScreenSettings;
import eu.amadeos.system.screen.ScreenSettingsStore;
import eu.amadeos.system.screen.modules.SaverModules;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.awt.event.HierarchyEvent;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Screen: screen saver (on/off, minutes, module with live preview, test) and screen lock (on/off, minutes),
 * as in CDE's Style Manager. The screen saver daemon picks up the saved settings by itself.
 */
final class ScreenPage extends SettingsPage {
    private static final String RANDOM_LABEL = SettingsTexts.get("screen.random");

    private final ScreenSettingsStore store;
    private final JCheckBox saverOn = new JCheckBox(SettingsTexts.get("screen.saver"));
    private final JSpinner saverMinutes = minutes();
    private final JCheckBox lockOn = new JCheckBox(SettingsTexts.get("screen.lock"));
    private final JSpinner lockMinutes = minutes();
    private final DefaultListModel<Object> modules = new DefaultListModel<>();
    private final JList<Object> moduleList = new JList<>(modules);
    private final SaverCanvas preview;
    private final Random random = new Random();

    ScreenPage(Consumer<String> status, ScreenSettingsStore store) {
        super(SettingsCategory.SCREEN, status);
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

        JButton test = new JButton(SettingsTexts.get("button.test"));
        test.setToolTipText(SettingsTexts.get("screen.test.tooltip"));
        test.addActionListener(e -> {
            SaverWindow window = new SaverWindow(SaverModules.create(selectedId(), random));
            window.dismissOnInput(() -> {
            });
            window.showSaver();
        });

        JPanel saverBox = new JPanel(new BorderLayout(10, 6));
        saverBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("screen.saver.title")));
        saverBox.add(row(saverOn, new JLabel(SettingsTexts.get("screen.saver.after")), saverMinutes,
                new JLabel(SettingsTexts.get("screen.minutes"))), BorderLayout.NORTH);
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
        lockBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("screen.lock.title")));
        lockBox.add(row(lockOn, new JLabel(SettingsTexts.get("screen.lock.after")), lockMinutes,
                new JLabel(SettingsTexts.get("screen.minutes.idle"))));
        JLabel hint = new JLabel(SettingsTexts.get("screen.hint"));
        AmadeUI.style(hint, (l, t) -> l.setFont(javax.swing.UIManager.getFont("Label.font").deriveFont(AmadeUI.scaled(11f))));
        lockBox.add(hint);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(saverBox, BorderLayout.CENTER);
        content.add(lockBox, BorderLayout.SOUTH);
        updateEnabled();
        layoutPage(content);

        // The preview runs only while the page is on screen: in the sidebar view the page stays when another is shown.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (isShowing()) preview.start();
            else preview.stop();
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
        String saver = settings.saverEnabled()
                ? SettingsTexts.format("screen.status.saver", settings.saverMinutes()) : SettingsTexts.get("screen.status.saver.off");
        String lock = settings.lockEnabled()
                ? SettingsTexts.format("screen.status.lock", settings.lockMinutes()) : SettingsTexts.get("screen.status.lock.off");
        return SettingsTexts.format("screen.status", saver, lock);
    }
}
