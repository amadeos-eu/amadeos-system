package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsTextsTest {

    @AfterEach
    void english() {
        SettingsTexts.use(Locale.ENGLISH);
    }

    @Test
    void everyTranslationIsComplete() {
        assertEquals(Map.of(), TranslationCheck.problems(SettingsTexts.class, SettingsTexts.BUNDLE));
    }

    @Test
    void controlsAndStatusLinesFollowTheLanguage() {
        SettingsTexts.use(Locale.GERMAN);
        assertEquals("Tastatur", SettingsCategory.KEYBOARD.label());
        assertEquals("Bildschirm: Schoner nach 10 Min., Sperre aus", SettingsTexts.format("screen.status",
                SettingsTexts.format("screen.status.saver", 10), SettingsTexts.get("screen.status.lock.off")));
        SettingsTexts.use(Locale.FRENCH);
        assertEquals("Impossible d'appliquer : xset", SettingsTexts.format("status.error", "xset"));
    }
}
