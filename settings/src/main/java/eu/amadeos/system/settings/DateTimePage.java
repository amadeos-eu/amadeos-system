package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.RegionalFormats;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Date and Time: the computer's clock (network time on or off, setting it by hand), its time zone, and the
 * user's formats of dates, times and numbers when they should differ from the language. The clock and the zone
 * are settings of the whole computer ({@link SystemClock}, polkit asks for the administrator's password); the
 * formats are the user's {@code formats.conf} ({@link RegionalFormats}).
 */
final class DateTimePage extends SettingsPage {
    /** The item of the region list that means "the formats of the language". */
    static final Locale FOLLOW_LANGUAGE = Locale.ROOT;
    /** Regions that measure in feet and pounds; glibc's LC_MEASUREMENT of en_US. */
    private static final Set<String> IMPERIAL = Set.of("US");
    /** Regions that print on US Letter; glibc's LC_PAPER. */
    private static final Set<String> LETTER = Set.of("US", "CA", "MX");

    private final SystemClock clock;
    private final RegionalFormats formats;
    private final Locale languageLocale;
    private final JLabel now = new JLabel();
    private final JCheckBox ntp = new JCheckBox(SettingsTexts.get("datetime.ntp"));
    private final JLabel ntpState = new JLabel();
    private final JSpinner date = new JSpinner(new SpinnerDateModel());
    private final JSpinner time = new JSpinner(new SpinnerDateModel());
    private final JComboBox<String> zone = new JComboBox<>();
    private final JComboBox<Locale> region = new JComboBox<>();
    private final JPanel preview = new JPanel(new GridBagLayout());
    private final Timer ticker;
    private SystemClock.Status status;
    private boolean timeEdited;
    private boolean filling;

    DateTimePage(Consumer<String> status, SystemClock clock, RegionalFormats formats, LocaleStore locales,
                 List<Locale> regions) {
        super(SettingsCategory.DATETIME, status);
        this.clock = clock;
        this.formats = formats;
        this.languageLocale = locales.language().map(Language::locale).orElse(Language.ENGLISH.locale());
        this.status = clock.status();

        now.setFont(now.getFont().deriveFont(Font.BOLD, now.getFont().getSize2D() * 1.3f));
        ntp.setSelected(this.status.ntp());
        ntp.setEnabled(this.status.canNtp());
        ntp.addActionListener(e -> {
            if (!ntp.isSelected()) fillManualTime();
            updateEnabled();
        });
        date.setEditor(new JSpinner.DateEditor(date, datePattern()));
        time.setEditor(new JSpinner.DateEditor(time, "HH:mm:ss"));
        date.addChangeListener(e -> timeEdited |= !filling);
        time.addChangeListener(e -> timeEdited |= !filling);

        for (String id : SystemClock.zones()) zone.addItem(id);
        if (!SystemClock.zones().contains(this.status.zone())) zone.insertItemAt(this.status.zone(), 0);
        zone.setSelectedItem(this.status.zone());
        zone.setMaximumRowCount(20);
        zone.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : SystemClock.label(value.toString()),
                        index, selected, focus);
            }
        });
        zone.addActionListener(e -> tick());
        fillManualTime();

        region.addItem(FOLLOW_LANGUAGE);
        Optional<Locale> chosen = formats.region();
        List<Locale> offered = new ArrayList<>(regions);
        chosen.filter(r -> !offered.contains(r)).ifPresent(r -> offered.addFirst(r));
        for (Locale r : offered) region.addItem(r);
        region.setSelectedItem(chosen.orElse(FOLLOW_LANGUAGE));
        region.setMaximumRowCount(20);
        region.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(regionLabel((Locale) value));
                return this;
            }
        });
        region.addActionListener(e -> {
            updatePreview();
            tick();
        });
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        updatePreview();

        // --8<-- [start:layout]
        layoutSections(
                section(SettingsTexts.get("datetime.clock.title"), line(now), line(ntp), line(ntpState),
                        line(new JLabel(SettingsTexts.get("datetime.date")), date,
                                new JLabel(SettingsTexts.get("datetime.time")), time),
                        note(SettingsTexts.get("datetime.clock.hint"))),
                section(SettingsTexts.get("datetime.zone.title"),
                        line(new JLabel(SettingsTexts.get("datetime.zone")), zone),
                        note(SettingsTexts.get("datetime.zone.hint"))),
                section(SettingsTexts.get("datetime.formats.title"),
                        line(new JLabel(SettingsTexts.get("datetime.region")), region), preview,
                        note(SettingsTexts.get("datetime.formats.hint"))));
        // --8<-- [end:layout]
        updateEnabled();
        tick();
        ticker = new Timer(1000, e -> tick());
        ticker.setInitialDelay(1000 - (int) (System.currentTimeMillis() % 1000));
        ticker.start();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        ticker.stop();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (ticker != null && !ticker.isRunning()) ticker.start();
    }

    /** The region the formats follow now: the chosen one, or the language's. */
    Locale formatLocale() {
        Locale r = (Locale) region.getSelectedItem();
        return r == null || r.equals(FOLLOW_LANGUAGE) ? languageLocale : r;
    }

    private String regionLabel(Locale r) {
        if (r == null) return "";
        if (r.equals(FOLLOW_LANGUAGE)) {
            return SettingsTexts.format("datetime.region.language", RegionalFormats.nativeName(languageLocale));
        }
        return RegionalFormats.nativeName(r);
    }

    private ZoneId selectedZone() {
        try {
            return ZoneId.of((String) zone.getSelectedItem());
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /** The clock line, every second, in the chosen zone and formats. */
    private void tick() {
        ZonedDateTime t = ZonedDateTime.now(selectedZone());
        Locale l = formatLocale();
        now.setText(text(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(l).format(t) + "   "
                + DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(l).format(t)));
        if (status.canNtp()) {
            ntpState.setText(SettingsTexts.get(!status.ntp() ? "datetime.ntp.off"
                    : status.synchronized_() ? "datetime.ntp.synchronized" : "datetime.ntp.waiting"));
        } else {
            ntpState.setText(SettingsTexts.get("datetime.ntp.missing"));
        }
    }

    /** The fields for setting the clock by hand, filled with the time of now. */
    private void fillManualTime() {
        filling = true;
        Date d = Date.from(ZonedDateTime.now(selectedZone()).toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant());
        date.setValue(d);
        time.setValue(d);
        filling = false;
        timeEdited = false;
    }

    private void updateEnabled() {
        boolean manual = !ntp.isSelected();
        date.setEnabled(manual);
        time.setEnabled(manual);
    }

    /** The short date pattern of the formats, with a four-digit year, for the date field. */
    private String datePattern() {
        String pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT,
                null, java.time.chrono.IsoChronology.INSTANCE, formatLocale());
        return pattern.contains("yyyy") ? pattern : pattern.replaceAll("y+", "yyyy");
    }

    private void updatePreview() {
        preview.removeAll();
        Locale l = formatLocale();
        ZonedDateTime t = ZonedDateTime.now(selectedZone());
        Locale ui = SettingsTexts.locale();
        int row = 0;
        row = previewRow(row, "datetime.preview.date", DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(l).format(t)
                + "   " + DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(l).format(t));
        row = previewRow(row, "datetime.preview.time", DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(l).format(t));
        row = previewRow(row, "datetime.preview.number", NumberFormat.getNumberInstance(l).format(1234567.89));
        row = previewRow(row, "datetime.preview.currency", NumberFormat.getCurrencyInstance(l).format(1234.5));
        row = previewRow(row, "datetime.preview.week",
                WeekFields.of(l).getFirstDayOfWeek().getDisplayName(TextStyle.FULL, ui));
        row = previewRow(row, "datetime.preview.measurement",
                SettingsTexts.get(IMPERIAL.contains(l.getCountry()) ? "datetime.imperial" : "datetime.metric"));
        previewRow(row, "datetime.preview.paper", LETTER.contains(l.getCountry()) ? "Letter" : "A4");
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 2;
        filler.weightx = 1;
        preview.add(javax.swing.Box.createHorizontalGlue(), filler);
        date.setEditor(new JSpinner.DateEditor(date, datePattern()));
        preview.revalidate();
        preview.repaint();
    }

    private int previewRow(int row, String key, String value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 8, 1, 8);
        c.gridx = 0;
        preview.add(new JLabel(SettingsTexts.get(key)), c);
        c.gridx = 1;
        JLabel v = new JLabel(text(value));
        v.setFont(v.getFont().deriveFont(Font.PLAIN));
        preview.add(v, c);
        return row + 1;
    }

    /**
     * The narrow no-break space that the CLDR formats put before "PM" and into French numbers, as a plain no-break
     * space, which every font of the desktop has.
     */
    static String text(String formatted) {
        return formatted.replace('\u202F', '\u00A0');
    }

    JComboBox<String> zoneBox() {
        return zone;
    }

    JComboBox<Locale> regionBox() {
        return region;
    }

    JCheckBox ntpBox() {
        return ntp;
    }

    /** Pretends the clock fields were changed by hand to the time, for tests and pictures. */
    void enterTime(LocalDateTime t) {
        Date d = Date.from(t.atZone(ZoneId.systemDefault()).toInstant());
        date.setValue(d);
        time.setValue(d);
    }

    LocalDateTime manualTime() {
        LocalDateTime d = LocalDateTime.ofInstant(((Date) date.getValue()).toInstant(), ZoneId.systemDefault());
        LocalDateTime t = LocalDateTime.ofInstant(((Date) time.getValue()).toInstant(), ZoneId.systemDefault());
        return d.toLocalDate().atTime(t.toLocalTime().withNano(0));
    }

    @Override
    protected String apply() {
        List<String> message = new ArrayList<>();
        Locale r = (Locale) region.getSelectedItem();
        Optional<Locale> newRegion = r == null || r.equals(FOLLOW_LANGUAGE) ? Optional.empty() : Optional.of(r);
        if (!newRegion.equals(formats.region())) {
            try {
                formats.write(newRegion);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            message.add(SettingsTexts.format("datetime.status.formats", regionLabel(r)));
        }
        String newZone = (String) zone.getSelectedItem();
        boolean zoneChanged = newZone != null && !newZone.equals(status.zone());
        boolean ntpChanged = status.canNtp() && ntp.isSelected() != status.ntp();
        boolean setTime = !ntp.isSelected() && timeEdited;
        if (zoneChanged || ntpChanged || setTime) {
            changeClock(zoneChanged ? newZone : null, ntpChanged ? ntp.isSelected() : null,
                    setTime ? manualTime() : null);
            message.add(SettingsTexts.get("datetime.status.saving"));
        }
        return message.isEmpty() ? SettingsTexts.get("datetime.status.unchanged") : String.join(" ", message);
    }

    // --8<-- [start:clock]
    /** Changes the computer's clock in the background: zone, then network time, then the time itself. */
    private void changeClock(String newZone, Boolean newNtp, LocalDateTime newTime) {
        new SwingWorker<SystemClock.Status, Void>() {
            @Override
            protected SystemClock.Status doInBackground() throws IOException {
                if (newZone != null) clock.setZone(newZone);
                if (newNtp != null) clock.setNtp(newNtp);
                if (newTime != null) clock.setTime(newTime);
                return clock.status();
            }

            @Override
            protected void done() {
                try {
                    status = get();
                    timeEdited = false;
                    status(SettingsTexts.format("datetime.status.done", SystemClock.label(status.zone()),
                            SettingsTexts.get(status.ntp() ? "datetime.status.ntp" : "datetime.status.manual")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    status = clock.status();
                    status(SettingsTexts.format("datetime.status.failed", e.getCause().getMessage()));
                }
                tick();
            }
        }.execute();
    }
    // --8<-- [end:clock]

    @Override
    protected void cancelled() {
        status = clock.status();
        ntp.setSelected(status.ntp());
        zone.setSelectedItem(status.zone());
        region.setSelectedItem(formats.region().orElse(FOLLOW_LANGUAGE));
        fillManualTime();
        updateEnabled();
    }
}
