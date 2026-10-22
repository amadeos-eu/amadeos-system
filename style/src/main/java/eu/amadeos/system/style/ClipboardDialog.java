package eu.amadeos.system.style;

import eu.amadeos.system.clipboard.ClipboardCommand;
import eu.amadeos.system.clipboard.ClipboardSettings;
import eu.amadeos.system.clipboard.ClipboardSettingsStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
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
final class ClipboardDialog extends StyleDialog {
    private final ClipboardSettingsStore store;
    private final Supplier<Boolean> clearHistory;
    private final JCheckBox history = new JCheckBox(StyleTexts.get("clipboard.history"));
    private final JSpinner size = new JSpinner(new SpinnerNumberModel(ClipboardSettings.DEFAULTS.size(),
            ClipboardSettings.MIN_SIZE, ClipboardSettings.MAX_SIZE, 5));
    private final JCheckBox keep = new JCheckBox(StyleTexts.get("clipboard.keep"));
    private final JCheckBox primary = new JCheckBox(StyleTexts.get("clipboard.primary"));
    private final JButton clear = new JButton(StyleTexts.get("clipboard.clear"));

    ClipboardDialog(JFrame owner, Consumer<String> status, ClipboardSettingsStore store) {
        this(owner, status, store, ClipboardCommand.CLEAR::send);
    }

    ClipboardDialog(JFrame owner, Consumer<String> status, ClipboardSettingsStore store, Supplier<Boolean> clearHistory) {
        super(owner, StyleCategory.CLIPBOARD, status);
        this.store = store;
        this.clearHistory = clearHistory;
        ClipboardSettings settings = store.load();
        history.setSelected(settings.history());
        size.setValue(settings.size());
        size.setEditor(new JSpinner.NumberEditor(size, "#"));
        keep.setSelected(settings.keep());
        primary.setSelected(settings.primary());
        history.addActionListener(e -> updateEnabled());
        clear.setToolTipText(StyleTexts.get("clipboard.clear.tooltip"));
        clear.addActionListener(e -> status.accept(StyleTexts.get(
                clearHistory.get() ? "clipboard.status.cleared" : "clipboard.status.not.running")));

        JPanel historyBox = new JPanel(new GridLayout(0, 1, 0, 4));
        historyBox.setBorder(BorderFactory.createTitledBorder(StyleTexts.get("clipboard.history.title")));
        historyBox.add(row(history));
        historyBox.add(row(new JLabel(StyleTexts.get("clipboard.size")), size,
                new JLabel(StyleTexts.get("clipboard.entries")), clear));
        historyBox.add(hint(StyleTexts.get("clipboard.history.hint")));

        JPanel copyBox = new JPanel(new GridLayout(0, 1, 0, 4));
        copyBox.setBorder(BorderFactory.createTitledBorder(StyleTexts.get("clipboard.copies.title")));
        copyBox.add(row(keep));
        copyBox.add(row(primary));
        copyBox.add(hint(StyleTexts.get("clipboard.secret.hint")));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(historyBox, BorderLayout.CENTER);
        content.add(copyBox, BorderLayout.SOUTH);
        updateEnabled();
        layoutDialog(content);
    }

    private static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (Component c : components) row.add(c);
        return row;
    }

    private static JPanel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(11f));
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
        if (!s.history()) return StyleTexts.get("clipboard.status.off");
        return StyleTexts.format("clipboard.status", s.size(),
                StyleTexts.get(s.keep() ? "clipboard.status.keep" : "clipboard.status.keep.off"));
    }
}
