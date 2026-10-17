package org.datazuul.amadeos.i18n;

import java.util.Map;
import org.datazuul.amadeos.commons.i18n.TranslationCheck;
import org.datazuul.amadeos.wm.panel.widget.WorkspaceSwitcherDockWidget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopTextsTest {
    @Test
    void everyTranslationIsComplete() {
        assertEquals(Map.of(), TranslationCheck.problems(WorkspaceSwitcherDockWidget.class,
                "org.datazuul.amadeos.i18n.desktop"));
    }
}
