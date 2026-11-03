package eu.amadeos.system.settings;

import eu.amadeos.system.base.shortcuts.CustomShortcut;
import eu.amadeos.system.base.shortcuts.KeyCombo;
import eu.amadeos.system.base.shortcuts.ShortcutAction;
import eu.amadeos.system.base.shortcuts.ShortcutStore;
import eu.amadeos.system.base.shortcuts.Shortcuts;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Keyboard Shortcuts: the keys of the desktop's own actions (lock the screen, open a terminal, switch workspaces,
 * ...) and the user's own commands, as a list grouped by kind. A shortcut gets new keys by pressing them in a
 * {@link KeyCaptureDialog}; keys another shortcut has are taken from it after asking. Apply writes
 * {@code shortcuts.conf}, which the window manager follows at once.
 */
final class ShortcutsPage extends SettingsPage {
    private final ShortcutStore store;
    private final KeyCapture capture;
    private Shortcuts shortcuts;
    private final RowModel model = new RowModel();
    private final JTable table = new JTable(model);
    private final JButton change = new JButton(SettingsTexts.get("shortcuts.change"));
    private final JButton off = new JButton(SettingsTexts.get("shortcuts.off"));
    private final JButton reset = new JButton(SettingsTexts.get("shortcuts.default"));
    private final JButton add = new JButton(SettingsTexts.get("shortcuts.add"));
    private final JButton edit = new JButton(SettingsTexts.get("shortcuts.edit"));
    private final JButton remove = new JButton(SettingsTexts.get("shortcuts.remove"));

    ShortcutsPage(Consumer<String> status, ShortcutStore store) {
        this(status, store, KeyCapture.x11());
    }

    ShortcutsPage(Consumer<String> status, ShortcutStore store, KeyCapture capture) {
        super(SettingsCategory.SHORTCUTS, status);
        this.store = store;
        this.capture = capture;
        this.shortcuts = store.load();
        model.rebuild();

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 6);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.setDefaultRenderer(Object.class, new RowRenderer());
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) changeKeys();
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 360));

        change.addActionListener(e -> changeKeys());
        off.addActionListener(e -> switchOff());
        reset.addActionListener(e -> backToDefault());
        add.addActionListener(e -> addCustom());
        edit.addActionListener(e -> editCustom());
        remove.addActionListener(e -> removeCustom());
        JButton allDefaults = new JButton(SettingsTexts.get("button.defaults"));
        allDefaults.setToolTipText(SettingsTexts.get("shortcuts.defaults.tooltip"));
        allDefaults.addActionListener(e -> {
            shortcuts = Shortcuts.DEFAULTS.withCustom(shortcuts.custom());
            // Custom keys that a default now needs lose them.
            for (ShortcutAction a : ShortcutAction.values()) {
                List<CustomShortcut> kept = new ArrayList<>(shortcuts.custom());
                kept.removeIf(c -> c.keys().equals(a.defaultKeys()));
                shortcuts = shortcuts.withCustom(kept);
            }
            model.rebuild();
        });

        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
        for (JButton b : List.of(change, off, reset)) buttons.add(b);
        buttons.add(new JLabel());
        for (JButton b : List.of(add, edit, remove)) buttons.add(b);
        buttons.add(new JLabel());
        buttons.add(allDefaults);
        JPanel side = new JPanel(new BorderLayout());
        side.add(buttons, BorderLayout.NORTH);

        JPanel list = new JPanel(new BorderLayout(10, 0));
        list.add(scroll, BorderLayout.CENTER);
        list.add(side, BorderLayout.EAST);
        layoutSections(list, note(SettingsTexts.get("shortcuts.note")));
        updateButtons();
    }

    // ------------------------------------------------------------------------------------- the rows

    private sealed interface Row {
        record Heading(ShortcutAction.Group group) implements Row {
        }

        record Action(ShortcutAction action) implements Row {
        }

        record Custom(int index) implements Row {
        }

        record Fixed(String label, KeyCombo keys) implements Row {
        }
    }

    private final class RowModel extends AbstractTableModel {
        private final List<Row> rows = new ArrayList<>();

        void rebuild() {
            Row selected = selectedRow();
            rows.clear();
            for (ShortcutAction.Group group : ShortcutAction.Group.values()) {
                if (group == ShortcutAction.Group.CUSTOM) continue;
                rows.add(new Row.Heading(group));
                for (ShortcutAction a : ShortcutAction.values()) {
                    if (a.group() == group) rows.add(new Row.Action(a));
                }
                if (group == ShortcutAction.Group.WINDOWS) {
                    rows.add(new Row.Fixed(SettingsTexts.get("shortcuts.fixed.switcher"), ShortcutAction.FIXED.get(0)));
                    rows.add(new Row.Fixed(SettingsTexts.get("shortcuts.fixed.switcher.back"), ShortcutAction.FIXED.get(1)));
                }
                if (group == ShortcutAction.Group.PROGRAMS) {
                    rows.add(new Row.Fixed(SettingsTexts.get("shortcuts.fixed.taskmanager"), ShortcutAction.FIXED.get(2)));
                }
            }
            rows.add(new Row.Heading(ShortcutAction.Group.CUSTOM));
            for (int i = 0; i < shortcuts.custom().size(); i++) rows.add(new Row.Custom(i));
            fireTableDataChanged();
            if (selected != null) {
                int index = rows.indexOf(selected);
                if (index >= 0) table.setRowSelectionInterval(index, index);
            }
        }

        Row row(int index) {
            return index < 0 || index >= rows.size() ? null : rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return SettingsTexts.get(column == 0 ? "shortcuts.column.action" : "shortcuts.column.keys");
        }

        @Override
        public Object getValueAt(int index, int column) {
            Row row = rows.get(index);
            if (column == 0) {
                return switch (row) {
                    case Row.Heading h -> SettingsTexts.get("shortcuts.group." + h.group().id());
                    case Row.Action a -> label(a.action());
                    case Row.Custom c -> shortcuts.custom().get(c.index()).name();
                    case Row.Fixed f -> f.label();
                };
            }
            return switch (row) {
                case Row.Heading h -> "";
                case Row.Action a -> shortcuts.keys(a.action()).map(ShortcutsPage::display)
                        .orElse(SettingsTexts.get("shortcuts.none"));
                case Row.Custom c -> display(shortcuts.custom().get(c.index()).keys());
                case Row.Fixed f -> display(f.keys());
            };
        }
    }

    private final class RowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            Row r = model.row(row);
            boolean heading = r instanceof Row.Heading;
            super.getTableCellRendererComponent(t, value, selected && !heading, false, row, column);
            Font font = t.getFont();
            setFont(heading ? font.deriveFont(Font.BOLD) : font);
            setBorder(BorderFactory.createEmptyBorder(0, column == 0 && !heading ? 18 : 6, 0, 6));
            boolean dim = r instanceof Row.Fixed
                    || (r instanceof Row.Action a && column == 1 && shortcuts.keys(a.action()).isEmpty());
            if (!selected || heading) {
                setForeground(dim ? javax.swing.UIManager.getColor("Label.disabledForeground") : t.getForeground());
            }
            if (r instanceof Row.Fixed && column == 1) setToolTipText(SettingsTexts.get("shortcuts.fixed.tooltip"));
            else setToolTipText(null);
            return this;
        }
    }

    private Row selectedRow() {
        return model.row(table.getSelectedRow());
    }

    private void updateButtons() {
        Row row = selectedRow();
        boolean action = row instanceof Row.Action;
        boolean custom = row instanceof Row.Custom;
        change.setEnabled(action || custom);
        off.setEnabled(action && shortcuts.keys(((Row.Action) row).action()).isPresent());
        reset.setEnabled(action && !shortcuts.keys(((Row.Action) row).action())
                .equals(Optional.of(((Row.Action) row).action().defaultKeys())));
        edit.setEnabled(custom);
        remove.setEnabled(custom);
    }

    static String label(ShortcutAction action) {
        return SettingsTexts.get("shortcut." + action.id());
    }

    /** {@code Ctrl+Alt+T} in the words of the language: Strg+Alt+T in German, with names for keys such as Space. */
    static String display(KeyCombo keys) {
        StringBuilder text = new StringBuilder();
        int m = keys.modifiers();
        if ((m & KeyCombo.CTRL) != 0) text.append(SettingsTexts.get("keys.ctrl")).append('+');
        if ((m & KeyCombo.ALT) != 0) text.append(SettingsTexts.get("keys.alt")).append('+');
        if ((m & KeyCombo.SHIFT) != 0) text.append(SettingsTexts.get("keys.shift")).append('+');
        if ((m & KeyCombo.SUPER) != 0) text.append(SettingsTexts.get("keys.super")).append('+');
        String key = keys.key();
        String named = switch (key) {
            case "space", "Print", "Escape", "Return", "Delete", "Up", "Down", "Left", "Right", "Tab", "BackSpace" ->
                    SettingsTexts.get("keys.name." + key);
            default -> key.length() == 1 ? key.toUpperCase(java.util.Locale.ROOT) : key;
        };
        return text.append(named).toString();
    }

    // ------------------------------------------------------------------------------------- editing

    private void changeKeys() {
        Row row = selectedRow();
        String what = switch (row) {
            case Row.Action a -> label(a.action());
            case Row.Custom c -> shortcuts.custom().get(c.index()).name();
            case null, default -> null;
        };
        if (what == null) return;
        KeyCapture.Result result = KeyCaptureDialog.ask(this, what, capture);
        switch (result) {
            case KeyCapture.Result.Keys k -> {
                if (row instanceof Row.Action a) {
                    assign(k.keys(), a.action().name(), s -> s.with(a.action(), Optional.of(k.keys())), what);
                } else if (row instanceof Row.Custom c) {
                    CustomShortcut old = shortcuts.custom().get(c.index());
                    if (old.keys().equals(k.keys())) return;
                    assign(k.keys(), "custom" + c.index(), s -> {
                        List<CustomShortcut> list = new ArrayList<>(s.custom());
                        int at = list.indexOf(old);
                        list.set(at, new CustomShortcut(old.name(), old.command(), k.keys()));
                        return s.withCustom(list);
                    }, what);
                }
            }
            case KeyCapture.Result.Off o -> {
                if (row instanceof Row.Action a) {
                    shortcuts = shortcuts.with(a.action(), Optional.empty());
                    model.rebuild();
                }
            }
            default -> {
            }
        }
        updateButtons();
    }

    /**
     * Gives the keys to a shortcut after the checks: not a plain letter, not one of the window manager's own keys,
     * and, after asking, taken from the shortcut that has them now.
     */
    private boolean assign(KeyCombo keys, String self, java.util.function.UnaryOperator<Shortcuts> change, String what) {
        if (!keys.standsAlone()) {
            JOptionPane.showMessageDialog(this, wrap(SettingsTexts.format("shortcuts.needs.modifier", display(keys))),
                    SettingsCategory.SHORTCUTS.label(), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Optional<Shortcuts.Owner> owner = shortcuts.owner(keys);
        Shortcuts base = shortcuts;
        if (owner.isPresent()) {
            String ownerName = switch (owner.get()) {
                case Shortcuts.Owner.Fixed f -> null;
                case Shortcuts.Owner.Action a -> a.action().name().equals(self) ? "" : label(a.action());
                case Shortcuts.Owner.Custom c -> ("custom" + c.index()).equals(self) ? ""
                        : shortcuts.custom().get(c.index()).name();
            };
            if (ownerName == null) {
                JOptionPane.showMessageDialog(this, wrap(SettingsTexts.format("shortcuts.fixed.taken", display(keys))),
                        SettingsCategory.SHORTCUTS.label(), JOptionPane.WARNING_MESSAGE);
                return false;
            }
            if (!ownerName.isEmpty()) {
                int answer = JOptionPane.showConfirmDialog(this,
                        wrap(SettingsTexts.format("shortcuts.taken", display(keys), ownerName, what)),
                        SettingsCategory.SHORTCUTS.label(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (answer != JOptionPane.OK_OPTION) return false;
                if (owner.get() instanceof Shortcuts.Owner.Action a) {
                    base = shortcuts.with(a.action(), Optional.empty());
                } else {
                    // A custom shortcut without keys does nothing: it goes, unless it is the one being changed.
                    base = shortcuts.release(keys);
                }
            }
        }
        shortcuts = change.apply(base);
        model.rebuild();
        return true;
    }

    private void switchOff() {
        if (selectedRow() instanceof Row.Action a) {
            shortcuts = shortcuts.with(a.action(), Optional.empty());
            model.rebuild();
            updateButtons();
        }
    }

    private void backToDefault() {
        if (selectedRow() instanceof Row.Action a) {
            assign(a.action().defaultKeys(), a.action().name(),
                    s -> s.with(a.action(), Optional.of(a.action().defaultKeys())), label(a.action()));
            updateButtons();
        }
    }

    private void addCustom() {
        Optional<String[]> nameAndCommand = askNameAndCommand("", "", SettingsTexts.get("shortcuts.add.title"));
        if (nameAndCommand.isEmpty()) return;
        String name = nameAndCommand.get()[0].isBlank() ? nameAndCommand.get()[1] : nameAndCommand.get()[0];
        KeyCapture.Result result = KeyCaptureDialog.ask(this, name, capture);
        if (!(result instanceof KeyCapture.Result.Keys k)) return;
        CustomShortcut added = new CustomShortcut(name, nameAndCommand.get()[1], k.keys());
        if (assign(k.keys(), "new", s -> {
            List<CustomShortcut> list = new ArrayList<>(s.custom());
            list.add(added);
            return s.withCustom(list);
        }, name)) {
            int index = model.rows.indexOf(new Row.Custom(shortcuts.custom().size() - 1));
            if (index >= 0) table.setRowSelectionInterval(index, index);
        }
        updateButtons();
    }

    private void editCustom() {
        if (!(selectedRow() instanceof Row.Custom c)) return;
        CustomShortcut old = shortcuts.custom().get(c.index());
        Optional<String[]> nameAndCommand = askNameAndCommand(old.name(), old.command(),
                SettingsTexts.get("shortcuts.edit.title"));
        if (nameAndCommand.isEmpty()) return;
        List<CustomShortcut> list = new ArrayList<>(shortcuts.custom());
        list.set(c.index(), new CustomShortcut(nameAndCommand.get()[0], nameAndCommand.get()[1], old.keys()));
        shortcuts = shortcuts.withCustom(list);
        model.rebuild();
    }

    private void removeCustom() {
        if (!(selectedRow() instanceof Row.Custom c)) return;
        List<CustomShortcut> list = new ArrayList<>(shortcuts.custom());
        list.remove(c.index());
        shortcuts = shortcuts.withCustom(list);
        model.rebuild();
        updateButtons();
    }

    /** Name and command of a custom shortcut; empty if cancelled or the command is empty. */
    private Optional<String[]> askNameAndCommand(String name, String command, String title) {
        JTextField nameField = new JTextField(name, 26);
        JTextField commandField = new JTextField(command, 26);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 8);
        c.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel(SettingsTexts.get("shortcuts.custom.name")), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        form.add(nameField, c);
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel(SettingsTexts.get("shortcuts.custom.command")), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        form.add(commandField, c);
        c.gridx = 1;
        c.gridy = 2;
        form.add(new JLabel(SettingsTexts.get("shortcuts.custom.hint")), c);
        int answer = JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION || commandField.getText().isBlank()) return Optional.empty();
        return Optional.of(new String[]{nameField.getText().strip(), commandField.getText().strip()});
    }

    /** A message for a dialog, wrapped to a readable width. */
    private static String wrap(String text) {
        return "<html><body style='width: 340px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>";
    }

    /** The shortcuts as shown, for tests. */
    Shortcuts shortcuts() {
        return shortcuts;
    }

    @Override
    protected String apply() {
        store.save(shortcuts);
        return SettingsTexts.format("status.applied", SettingsCategory.SHORTCUTS.label());
    }
}
