package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleStore;

import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Language: the language of the desktop (the user's {@code locale.conf}, which the Front Panel's language
 * button also sets). The time zone and the formats of dates and numbers are on the Date and Time page.
 */
final class LanguagePage extends SettingsPage {
    private final LocaleStore locales;
    private final Map<Language, JRadioButton> languages = new EnumMap<>(Language.class);

    LanguagePage(Consumer<String> status, LocaleStore locales) {
        super(SettingsCategory.LANGUAGE, status);
        this.locales = locales;
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
        layoutSections(
                section(SettingsTexts.get("language.title"), line(buttons), note(SettingsTexts.get("language.hint"))),
                note(SettingsTexts.get("language.formats.moved")));
    }

    Optional<Language> selectedLanguage() {
        return languages.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst();
    }

    @Override
    protected String apply() {
        Optional<Language> language = selectedLanguage();
        if (language.isEmpty() || language.equals(locales.language())) {
            return SettingsTexts.get("language.status.unchanged");
        }
        try {
            locales.write(language.get());
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return SettingsTexts.format("language.status", language.get().nativeName());
    }
}
