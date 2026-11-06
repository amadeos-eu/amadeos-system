package eu.amadeos.system.settings;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class FontPageTest {
    @Test
    void manageFontsStartsTheFontViewer() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        List<List<String>> launched = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            FontPage page = new FontPage(s -> { }, launched::add);
            ((JButton) find(page, "font.manage")).doClick();
        });
        assertEquals(List.of(List.of("amade-fontviewer")), launched);
    }

    private static Component find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container k) {
                Component found = find(k, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
