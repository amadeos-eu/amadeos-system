package eu.amadeos.system.widgets;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;

import java.util.Locale;
import java.util.Optional;

/** The texts of the widget host and its own widgets, {@code widgets.properties}, in the session's language. */
public final class WidgetTexts {
    public static final String BUNDLE = "eu.amadeos.system.i18n.widgets";

    private static Messages texts;
    private static Locale locale;

    private WidgetTexts() {
    }

    public static synchronized Messages texts() {
        if (texts == null) {
            locale = LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale();
            texts = Messages.load(WidgetTexts.class, BUNDLE, locale);
        }
        return texts;
    }

    public static synchronized Locale locale() {
        texts();
        return locale;
    }

    /** For tests: the texts of the given locale. */
    public static synchronized void use(Locale l) {
        locale = l;
        texts = Messages.load(WidgetTexts.class, BUNDLE, l);
    }

    public static String get(String key) {
        return texts().get(key);
    }

    public static String format(String key, Object... arguments) {
        return texts().format(key, arguments);
    }
}
