package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.clipboard.ClipboardCommand;
import eu.amadeos.system.clipboard.ClipboardSettings;
import eu.amadeos.system.clipboard.ClipboardSettingsStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Clipboard: the history of the clipboard manager (on/off, how many entries, clear it now), keeping a copy
 * after the program that made it has closed, and recording selected text. The clipboard manager picks up the
 * saved settings by itself; the history itself is never saved.
 */
final class ClipboardPage extends SettingsPage {
    private final ClipboardSettingsStore store;
    private final Supplier<Boolean> clearHistory;
    private final JCheckBox history = new JCheckBox(SettingsTexts.get("clipboard.history"));
    private final JSpinner size = new JSpinner(new SpinnerNumberModel(ClipboardSettings.DEFAULTS.size(),
            ClipboardSettings.MIN_SIZE, ClipboardSettings.MAX_SIZE, 5));
    private final JCheckBox keep = new JCheckBox(SettingsTexts.get("clipboard.keep"));
    private final JCheckBox primary = new JCheckBox(SettingsTexts.get("clipboard.primary"));
    private final JButton clear = new JButton(SettingsTexts.get("clipboard.clear"));

    ClipboardPage(Consumer<String> status, ClipboardSettingsStore store) {
        this(status, store, ClipboardCommand.CLEAR::send);
    }

    ClipboardPage(Consumer<String> status, ClipboardSettingsStore store, Supplier<Boolean> clearHistory) {
        super(SettingsCategory.CLIPBOARD, status);
        this.store = store;
        this.clearHistory = clearHistory;
        ClipboardSettings settings = store.load();
        history.setSelected(settings.history());
        size.setValue(settings.size());
        size.setEditor(new JSpinner.NumberEditor(size, "#"));
        keep.setSelected(settings.keep());
        primary.setSelected(settings.primary());
        history.addActionListener(e -> updateEnabled());
        clear.setToolTipText(SettingsTexts.get("clipboard.clear.tooltip"));
        clear.addActionListener(e -> status.accept(SettingsTexts.get(
                clearHistory.get() ? "clipboard.status.cleared" : "clipboard.status.not.running")));

        JPanel historyBox = new JPanel(new GridLayout(0, 1, 0, 4));
        historyBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("clipboard.history.title")));
        historyBox.add(row(history));
        historyBox.add(row(new JLabel(SettingsTexts.get("clipboard.size")), size,
                new JLabel(SettingsTexts.get("clipboard.entries")), clear));
        historyBox.add(hint(SettingsTexts.get("clipboard.history.hint")));

        JPanel copyBox = new JPanel(new GridLayout(0, 1, 0, 4));
        copyBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("clipboard.copies.title")));
        copyBox.add(row(keep));
        copyBox.add(row(primary));
        copyBox.add(hint(SettingsTexts.get("clipboard.secret.hint")));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(historyBox, BorderLayout.CENTER);
        content.add(copyBox, BorderLayout.SOUTH);
        updateEnabled();
        layoutPage(content);
    }

    private static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (Component c : components) row.add(c);
        return row;
    }

    private static JPanel hint(String text) {
        JLabel label = new JLabel(text);
        AmadeUI.style(label, (l, t) -> l.setFont(javax.swing.UIManager.getFont("Label.font").deriveFont(AmadeUI.scaled(11f))));
        return row(label);
    }

    private void updateEnabled() {
        size.setEnabled(history.isSelected());
        keep.setEnabled(history.isSelected());
        primary.setEnabled(history.isSelected());
    }

    ClipboardSettings settings() {
        return new ClipboardSettings(history.isSelected(), (Integer) size.getValue(), keep.isSelected(),
                primary.isSelected());
    }

    @Override
    protected String apply() {
        ClipboardSettings s = settings();
        store.save(s);
        if (!s.history()) return SettingsTexts.get("clipboard.status.off");
        return SettingsTexts.format("clipboard.status", s.size(),
                SettingsTexts.get(s.keep() ? "clipboard.status.keep" : "clipboard.status.keep.off"));
    }
}
