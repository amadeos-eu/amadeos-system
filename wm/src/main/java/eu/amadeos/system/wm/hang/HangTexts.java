package eu.amadeos.system.wm.hang;

import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.Messages;

import java.util.Optional;

/** The window manager's texts for windows that do not respond, in the desktop's language. */
public final class HangTexts {
    public static final String BASE_NAME = "eu.amadeos.system.wm.hang.hang";

    private HangTexts() {
    }

    public static Messages load() {
        return Messages.load(HangTexts.class, BASE_NAME,
                LocaleResolution.resolve(Optional.empty(), LocaleStore.system().current(), System.getenv())
                        .language().locale());
    }
}
