package eu.amadeos.system.style;

import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StyleTextsTest {

    @AfterEach
    void english() {
        StyleTexts.use(Locale.ENGLISH);
    }

    @Test
    void everyTranslationIsComplete() {
        assertEquals(Map.of(), TranslationCheck.problems(StyleTexts.class, StyleTexts.BUNDLE));
    }

    @Test
    void controlsAndStatusLinesFollowTheLanguage() {
        StyleTexts.use(Locale.GERMAN);
        assertEquals("Tastatur", StyleCategory.KEYBOARD.label());
        assertEquals("Bildschirm: Schoner nach 10 Min., Sperre aus", StyleTexts.format("screen.status",
                StyleTexts.format("screen.status.saver", 10), StyleTexts.get("screen.status.lock.off")));
        StyleTexts.use(Locale.FRENCH);
        assertEquals("Impossible d'appliquer : xset", StyleTexts.format("status.error", "xset"));
    }
}
