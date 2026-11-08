package eu.amadeos.system.panel.cde.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class UpdateProgressWindowTest {
    private static final String LONG = "Die Aktualisierungen sind installiert. Starten Sie den Computer neu, um sie abzuschließen.";

    @Test
    void aLongMessageGetsTheHeightOfItsLines() {
        JLabel oneLine = new JLabel(UpdateProgressWindow.statusHtml("Kurz.", UpdateProgressWindow.STATUS_WIDTH));
        JLabel wrapped = new JLabel(UpdateProgressWindow.statusHtml(LONG, UpdateProgressWindow.STATUS_WIDTH));
        assertTrue(wrapped.getPreferredSize().height >= 2 * oneLine.getPreferredSize().height - 2,
                "the label must be as tall as the wrapped lines, not one line: " + wrapped.getPreferredSize());
        assertTrue(wrapped.getPreferredSize().width <= UpdateProgressWindow.STATUS_WIDTH + 10,
                "long texts wrap instead of widening the window: " + wrapped.getPreferredSize());
    }

    @Test
    void theMessageIsEscapedAndKeepsItsLineBreaks() {
        assertEquals("<html><div width='420'>a &lt;b&gt; &amp; c<br>d</div></html>",
                UpdateProgressWindow.statusHtml("a <b> & c\nd", 420));
    }
}
