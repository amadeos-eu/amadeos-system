package eu.amadeos.system.panel.horizon;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;

import java.util.Locale;
import java.util.Optional;

/** The texts of the top bar, {@code topbar.properties} and its translations, in the language of the session. */
public final class TopBarTexts {
    public static final String BUNDLE = "eu.amadeos.system.i18n.topbar";

    private static Messages texts;

    private TopBarTexts() {
    }

    public static synchronized Messages texts() {
        if (texts == null) {
            Locale locale = LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv())
                    .language().locale();
            texts = Messages.load(TopBarTexts.class, BUNDLE, locale);
        }
        return texts;
    }

    /** For tests: the texts of the given locale. */
    public static synchronized void use(Locale locale) {
        texts = Messages.load(TopBarTexts.class, BUNDLE, locale);
    }

    public static String get(String key) {
        return texts().get(key);
    }

    public static String format(String key, Object... arguments) {
        return texts().format(key, arguments);
    }
}
