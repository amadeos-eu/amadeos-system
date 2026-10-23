package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;

import java.util.Locale;
import java.util.Optional;

/**
 * The texts of the Settings, {@code messages.properties} and its translations, in the desktop language: the
 * Settings have no Guice, so they resolve the language itself, from {@code locale.conf} as the applications do
 * at start-up, then from the environment.
 */
final class SettingsTexts {
    static final String BUNDLE = "eu.amadeos.system.settings.messages";

    private static Messages texts;

    private SettingsTexts() {
    }

    // --8<-- [start:texts]
    static synchronized Messages texts() {
        if (texts == null) {
            Locale locale = LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                    .language().locale();
            texts = Messages.load(SettingsTexts.class, BUNDLE, locale);
        }
        return texts;
    }
    // --8<-- [end:texts]

    /** For tests: the texts of the given locale, whatever the desktop language. */
    static synchronized void use(Locale locale) {
        texts = Messages.load(SettingsTexts.class, BUNDLE, locale);
    }

    static String get(String key) {
        return texts().get(key);
    }

    static String format(String key, Object... arguments) {
        return texts().format(key, arguments);
    }
}
