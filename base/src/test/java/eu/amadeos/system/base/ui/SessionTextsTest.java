package eu.amadeos.system.base.ui;

import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionTextsTest {
    @Test
    void everyTranslationIsComplete() {
        assertEquals(Map.of(), TranslationCheck.problems(SessionDialogHelper.class, SessionDialogHelper.TEXTS));
    }
}
