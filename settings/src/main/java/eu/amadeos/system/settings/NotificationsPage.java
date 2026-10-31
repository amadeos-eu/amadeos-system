package eu.amadeos.system.settings;

import eu.amadeos.system.notify.NotificationSettings;
import eu.amadeos.system.notify.NotificationSettingsStore;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.Dimension;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Notifications: where the pop-ups appear and how long they stay, the beep, Do not disturb (by hand, every day
 * from ... to ..., while a window is full screen), and for each program that has sent something whether its
 * notifications pop up, only go into the history or are dropped. Writes {@code notifications.conf}, which the
 * notification daemon follows at once.
 */
final class NotificationsPage extends SettingsPage {
    private final NotificationSettingsStore store;
    private final Consumer<List<String>> launcher;
    private final NotificationSettings loaded;
    private final JComboBox<String> corner = new JComboBox<>();
    private final JSpinner seconds = new JSpinner(new SpinnerNumberModel(NotificationSettings.DEFAULTS.seconds(),
            NotificationSettings.MIN_SECONDS, NotificationSettings.MAX_SECONDS, 1));
    private final JCheckBox beep = new JCheckBox(SettingsTexts.get("notifications.beep"));
    private final JCheckBox dnd = new JCheckBox(SettingsTexts.get("notifications.dnd"));
    private final JCheckBox schedule = new JCheckBox(SettingsTexts.get("notifications.schedule"));
    private final JComboBox<LocalTime> from = new JComboBox<>(halfHours());
    private final JComboBox<LocalTime> to = new JComboBox<>(halfHours());
    private final JCheckBox fullscreen = new JCheckBox(SettingsTexts.get("notifications.fullscreen"));
    private final ProgramsModel programs;
    private final JTable table;

    NotificationsPage(Consumer<String> status, NotificationSettingsStore store, Consumer<List<String>> launcher) {
        super(SettingsCategory.NOTIFICATIONS, status);
        this.store = store;
        this.launcher = launcher;
        this.loaded = store.load();
        for (NotificationSettings.Corner c : NotificationSettings.Corner.values()) {
            corner.addItem(SettingsTexts.get("notifications.corner." + c.name().toLowerCase(Locale.ROOT)));
        }
        corner.setName("notifications.corner");
        corner.setSelectedIndex(loaded.corner().ordinal());
        seconds.setValue(loaded.seconds());
        seconds.setEditor(new JSpinner.NumberEditor(seconds, "#"));
        seconds.setName("notifications.seconds");
        beep.setSelected(loaded.beep());
        boolean manual = loaded.manualDnd(Instant.now());
        dnd.setSelected(manual);
        dnd.setName("notifications.dnd");
        schedule.setSelected(loaded.schedule());
        schedule.setName("notifications.schedule");
        from.setSelectedItem(snap(loaded.from()));
        to.setSelectedItem(snap(loaded.to()));
        javax.swing.ListCellRenderer<? super LocalTime> times = new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof LocalTime t ? time(t) : value, index,
                        selected, focus);
            }
        };
        from.setRenderer(times);
        to.setRenderer(times);
        schedule.addActionListener(e -> updateEnabled());
        fullscreen.setSelected(loaded.fullscreen());

        programs = new ProgramsModel(loaded);
        table = new JTable(programs);
        table.setName("notifications.programs");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(table.getRowHeight(), new JComboBox<>().getPreferredSize().height));
        JComboBox<String> modes = new JComboBox<>(modeLabels());
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(modes));
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(420, table.getRowHeight() * 6 + 24));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, table.getRowHeight() * 6 + 24));

        JButton history = new JButton(SettingsTexts.get("notifications.history"));
        history.setName("notifications.history");
        history.addActionListener(e -> launcher.accept(List.of("amadeos-notifications")));

        String until = loaded.dndUntil() > 0 && manual
                ? SettingsTexts.format("notifications.dnd.until", time(LocalTime.ofInstant(
                        Instant.ofEpochSecond(loaded.dndUntil()), ZoneId.systemDefault())))
                : SettingsTexts.get("notifications.dnd.hint");
        updateEnabled();
        layoutSections(
                section(SettingsTexts.get("notifications.popups.title"),
                        line(new JLabel(SettingsTexts.get("notifications.corner")), corner),
                        line(new JLabel(SettingsTexts.get("notifications.seconds")), seconds,
                                new JLabel(SettingsTexts.get("notifications.seconds.unit"))),
                        note(SettingsTexts.get("notifications.seconds.hint")),
                        line(beep)),
                section(SettingsTexts.get("notifications.dnd.title"),
                        line(dnd), note(until),
                        line(schedule, from, new JLabel(SettingsTexts.get("notifications.schedule.to")), to),
                        line(fullscreen)),
                section(SettingsTexts.get("notifications.programs.title"),
                        programs.getRowCount() == 0 ? note(SettingsTexts.get("notifications.programs.none")) : scroll,
                        note(SettingsTexts.get("notifications.programs.hint"))),
                line(history),
                note(SettingsTexts.get("notifications.hint")));
    }

    private void updateEnabled() {
        from.setEnabled(schedule.isSelected());
        to.setEnabled(schedule.isSelected());
    }

    NotificationSettings selected() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        boolean wasOn = loaded.manualDnd(Instant.now());
        long dndUntil = dnd.isSelected() == wasOn ? loaded.dndUntil()
                : dnd.isSelected() ? NotificationSettings.DND_ON : NotificationSettings.DND_OFF;
        NotificationSettings s = new NotificationSettings(
                NotificationSettings.Corner.values()[Math.max(0, corner.getSelectedIndex())],
                (Integer) seconds.getValue(), beep.isSelected(), dndUntil, schedule.isSelected(),
                (LocalTime) from.getSelectedItem(), (LocalTime) to.getSelectedItem(), fullscreen.isSelected(),
                Map.of(), loaded.names());
        for (ProgramsModel.Row r : programs.rows) {
            if (r.mode != NotificationSettings.Mode.POPUP || loaded.programs().containsKey(r.key)) {
                s = s.withProgram(r.key, r.mode, null);
            }
        }
        return s;
    }

    @Override
    protected String apply() {
        // The daemon may have written new programs or Do not disturb since the page was opened: keep those.
        NotificationSettings now = store.load();
        NotificationSettings s = selected();
        Map<String, String> names = new TreeMap<>(now.names());
        names.putAll(s.names());
        Map<String, NotificationSettings.Mode> modes = new TreeMap<>(now.programs());
        modes.putAll(s.programs());
        long dndUntil = dnd.isSelected() == loaded.manualDnd(Instant.now()) ? now.dndUntil() : s.dndUntil();
        store.save(new NotificationSettings(s.corner(), s.seconds(), s.beep(), dndUntil, s.schedule(), s.from(),
                s.to(), s.fullscreen(), modes, names));
        return SettingsTexts.format("status.applied", SettingsCategory.NOTIFICATIONS.label());
    }

    /** 00:00, 00:30, ... 23:30. */
    private static LocalTime[] halfHours() {
        LocalTime[] times = new LocalTime[48];
        for (int i = 0; i < 48; i++) times[i] = LocalTime.of(i / 2, i % 2 * 30);
        return times;
    }

    private static LocalTime snap(LocalTime t) {
        return LocalTime.of(t.getHour(), t.getMinute() < 30 ? 0 : 30);
    }

    private static String time(LocalTime t) {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(t);
    }

    private static String[] modeLabels() {
        NotificationSettings.Mode[] modes = NotificationSettings.Mode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = modeLabel(modes[i]);
        return labels;
    }

    static String modeLabel(NotificationSettings.Mode m) {
        return SettingsTexts.get("notifications.mode." + m.name().toLowerCase(Locale.ROOT));
    }

    /** The programs that have sent notifications, by name, with what their notifications do. */
    static final class ProgramsModel extends AbstractTableModel {
        static final class Row {
            final String key;
            final String name;
            NotificationSettings.Mode mode;

            Row(String key, String name, NotificationSettings.Mode mode) {
                this.key = key;
                this.name = name;
                this.mode = mode;
            }
        }

        final List<Row> rows = new ArrayList<>();

        ProgramsModel(NotificationSettings s) {
            TreeMap<String, String> keys = new TreeMap<>();
            s.names().forEach((k, v) -> keys.put(k, v));
            s.programs().keySet().forEach(k -> keys.putIfAbsent(k, k));
            keys.forEach((k, v) -> rows.add(new Row(k, v, s.mode(k))));
            rows.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
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
            return SettingsTexts.get(column == 0 ? "notifications.column.program" : "notifications.column.show");
        }

        @Override
        public Object getValueAt(int row, int column) {
            Row r = rows.get(row);
            return column == 0 ? r.name : modeLabel(r.mode);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            for (NotificationSettings.Mode m : NotificationSettings.Mode.values()) {
                if (modeLabel(m).equals(value)) rows.get(row).mode = m;
            }
            fireTableCellUpdated(row, column);
        }
    }
}
