package eu.amadeos.i18n;

import java.util.Map;
import eu.amadeos.commons.i18n.TranslationCheck;
import eu.amadeos.panel.cde.widget.WorkspaceSwitcherDockWidget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopTextsTest {
    @Test
    void everyTranslationIsComplete() {
        assertEquals(Map.of(), TranslationCheck.problems(WorkspaceSwitcherDockWidget.class,
                "eu.amadeos.i18n.desktop"));
    }
}
