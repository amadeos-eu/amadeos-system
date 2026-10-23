package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
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

    /** Puts a line on the status bar, e.g. when an operation that runs in the background has ended. */
    protected final void status(String message) {
        status.accept(message);
    }

    /** A line of components from the left. */
    protected static JPanel line(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        for (Component c : components) row.add(c);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    /** An explanation in the small type of the hints, wrapped to the page's width. */
    protected static JComponent note(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        AmadeUI.style(label, (l, t) -> l.setFont(UIManager.getFont("Label.font").deriveFont(AmadeUI.scaled(11f))));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        holder.add(label);
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        return holder;
    }

    /** A titled frame with its lines one below the other, as CDE groups the settings of a dialog. */
    protected static JPanel section(String title, Component... lines) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createTitledBorder(title));
        for (Component line : lines) {
            if (line instanceof JComponent c) c.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(line);
        }
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    /** Sections and hints one below the other at the top of the page. */
    protected final void layoutSections(JComponent... parts) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (int i = 0; i < parts.length; i++) {
            parts[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(parts[i]);
            if (i < parts.length - 1) content.add(Box.createVerticalStrut(10));
        }
        JPanel top = new JPanel(new BorderLayout());
        top.add(content, BorderLayout.NORTH);
        layoutPage(top);
    }

    /** Applies the page's settings; returns a message for the status line. */
    protected abstract String apply();
}
