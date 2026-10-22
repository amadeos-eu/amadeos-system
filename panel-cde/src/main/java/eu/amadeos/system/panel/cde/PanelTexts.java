package eu.amadeos.system.panel.cde;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;

import java.util.Locale;
import java.util.Optional;

/**
 * The texts of the front panel, {@code desktop.properties} and its translations, in the language of the session.
 * The panel changes its language at the next login, like the terminals, so it resolves it once from the
 * environment, which the session sets from {@code locale.conf}.
 */
public final class PanelTexts {
    public static final String BUNDLE = "eu.amadeos.system.i18n.desktop";

    private static Messages texts;

    private PanelTexts() {
    }

    public static synchronized Messages texts() {
        if (texts == null) {
            Locale locale = LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv())
                    .language().locale();
            texts = Messages.load(PanelTexts.class, BUNDLE, locale);
        }
        return texts;
    }

    /** For tests: the texts of the given locale, whatever the language of the session. */
    public static synchronized void use(Locale locale) {
        texts = Messages.load(PanelTexts.class, BUNDLE, locale);
    }

    public static String get(String key) {
        return texts().get(key);
    }

    public static String format(String key, Object... arguments) {
        return texts().format(key, arguments);
    }
}
