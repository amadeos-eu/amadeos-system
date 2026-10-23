package eu.amadeos.system.settings;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.function.Consumer;

/**
 * A page of the Settings, one control such as Color or Screen. The sidebar view shows it on the right with Apply
 * and Reset; the compact view puts it into a {@link ControlDialog} with CDE's OK, Apply and Cancel.
 */
abstract class SettingsPage extends JPanel {
    private final SettingsCategory category;
    private final Consumer<String> status;

    SettingsPage(SettingsCategory category, Consumer<String> status) {
        super(new BorderLayout());
        this.category = category;
        this.status = status;
    }

    SettingsCategory category() {
        return category;
    }

    // --8<-- [start:layout]
    /** Puts the page's controls in place; call at the end of the subclass constructor. */
    protected final void layoutPage(JComponent content) {
        add(content, BorderLayout.CENTER);
    }
    // --8<-- [end:layout]

    /** Applies the page and puts its message on the status line; false if that failed. */
    final boolean applyChanges() {
        try {
            String message = apply();
            if (message != null) status.accept(message);
            return true;
        } catch (RuntimeException e) {
            status.accept(SettingsTexts.format("status.error", e.getMessage()));
            return false;
        }
    }

    /** Called on Reset, Cancel and when the window closes; undoes previews that were not applied. */
    protected void cancelled() {
    }

    /** Applies the page's settings; returns a message for the status line. */
    protected abstract String apply();
}
