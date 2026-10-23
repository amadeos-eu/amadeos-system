package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleStore;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.SwingWorker;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Language: the language of the desktop (the user's {@code locale.conf}, which the Front Panel's language
 * button also sets) and the computer's time zone (a system setting, changed through {@code timedatectl}).
 */
final class LanguagePage extends SettingsPage {
    private final LocaleStore locales;
    private final TimeZones timeZones;
    private final Map<Language, JRadioButton> languages = new EnumMap<>(Language.class);
    private final JComboBox<String> zone = new JComboBox<>();
    private String zoneNow;

    LanguagePage(Consumer<String> status, LocaleStore locales, TimeZones timeZones) {
        super(SettingsCategory.LANGUAGE, status);
        this.locales = locales;
        this.timeZones = timeZones;
        ButtonGroup group = new ButtonGroup();
        Language current = locales.language().orElse(Language.ENGLISH);
        JRadioButton[] buttons = new JRadioButton[Language.values().length];
        for (Language language : Language.values()) {
            JRadioButton button = new JRadioButton(language.nativeName());
            button.setSelected(language == current);
            group.add(button);
            languages.put(language, button);
            buttons[language.ordinal()] = button;
        }
        for (String id : TimeZones.choices()) zone.addItem(id);
        zoneNow = timeZones.current();
        if (!TimeZones.choices().contains(zoneNow)) zone.insertItemAt(zoneNow, 0);
        zone.setSelectedItem(zoneNow);
        zone.setMaximumRowCount(20);
        zone.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : TimeZones.label(value.toString()),
                        index, selected, focus);
            }
        });

        layoutSections(
                section(SettingsTexts.get("language.title"), line(buttons), note(SettingsTexts.get("language.hint"))),
                section(SettingsTexts.get("language.zone.title"),
                        line(new JLabel(SettingsTexts.get("language.zone")), zone),
                        note(SettingsTexts.get("language.zone.hint"))),
                note(SettingsTexts.get("language.formats.planned")));
    }

    Optional<Language> selectedLanguage() {
        return languages.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst();
    }

    JComboBox<String> zoneBox() {
        return zone;
    }

    @Override
    protected String apply() {
        StringBuilder message = new StringBuilder();
        Optional<Language> language = selectedLanguage();
        if (language.isPresent() && !language.equals(locales.language())) {
            try {
                locales.write(language.get());
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            message.append(SettingsTexts.format("language.status", language.get().nativeName()));
        }
        String chosen = (String) zone.getSelectedItem();
        if (chosen != null && !chosen.equals(zoneNow)) {
            setZone(chosen);
            if (!message.isEmpty()) message.append(' ');
            message.append(SettingsTexts.get("language.zone.status.saving"));
        }
        return message.isEmpty() ? SettingsTexts.get("language.status.unchanged") : message.toString();
    }

    private void setZone(String chosen) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                timeZones.set(chosen);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    zoneNow = chosen;
                    status(SettingsTexts.format("language.zone.status", TimeZones.label(chosen)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    status(SettingsTexts.format("language.zone.status.failed", e.getCause().getMessage()));
                }
            }
        }.execute();
    }
}
