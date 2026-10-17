package org.datazuul.amadeos.print.bootstrap;

import org.datazuul.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(PrintModule.class, PrintModule.MESSAGES));
    }
}
