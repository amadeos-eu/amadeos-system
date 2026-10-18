package eu.amadeos.sysinfo.bootstrap;

import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(SysinfoModule.class, SysinfoModule.MESSAGES));
    }
}
