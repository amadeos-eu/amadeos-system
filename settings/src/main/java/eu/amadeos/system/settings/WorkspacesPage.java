package eu.amadeos.system.settings;

import eu.amadeos.system.base.workspace.WorkspaceNames;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Workspaces: the names of the four workspaces, which the Front Panel's buttons and the root menu show. Writes
 * {@code workspaces.conf}; the panel follows the file and publishes the names to the window manager. The
 * backdrop of each workspace is on the Backdrop page, one button away.
 */
final class WorkspacesPage extends SettingsPage {
    private final WorkspaceNames names;
    private final JTextField[] fields = new JTextField[WorkspaceNames.COUNT];

    WorkspacesPage(Consumer<String> status, WorkspaceNames names, Runnable openBackdrop) {
        super(SettingsCategory.WORKSPACES, status);
        this.names = names;
        JLabel[] labels = new JLabel[WorkspaceNames.COUNT];
        for (int i = 0; i < WorkspaceNames.COUNT; i++) {
            fields[i] = new JTextField(names.custom(i).orElse(defaultName(i)), 18);
            fields[i].setToolTipText(SettingsTexts.format("workspaces.name.tooltip", defaultName(i)));
            labels[i] = new JLabel(SettingsTexts.format("workspaces.name", i + 1));
            labels[i].setLabelFor(fields[i]);
        }
        // One width for the labels, so that the fields line up in a proportional font
        int width = 0;
        for (JLabel label : labels) width = Math.max(width, label.getPreferredSize().width);
        for (JLabel label : labels) label.setPreferredSize(new Dimension(width, label.getPreferredSize().height));
        JButton backdrop = new JButton(SettingsTexts.get("workspaces.backdrop"));
        backdrop.addActionListener(e -> openBackdrop.run());

        layoutSections(
                section(SettingsTexts.get("workspaces.names.title"),
                        line(labels[0], fields[0]), line(labels[1], fields[1]),
                        line(labels[2], fields[2]), line(labels[3], fields[3]),
                        note(SettingsTexts.format("workspaces.names.hint", WorkspaceNames.MAX_LENGTH))),
                section(SettingsTexts.get("workspaces.backdrop.title"), line(backdrop),
                        note(SettingsTexts.get("workspaces.backdrop.hint"))),
                note(SettingsTexts.get("workspaces.count.hint")));
    }

    /** One to Four in the language of the Settings, as the panel names a workspace without a name of its own. */
    static String defaultName(int index) {
        return SettingsTexts.get("workspace." + (index + 1));
    }

    JTextField field(int index) {
        return fields[index];
    }

    @Override
    protected String apply() {
        for (int i = 0; i < WorkspaceNames.COUNT; i++) {
            String name = WorkspaceNames.clean(fields[i].getText());
            // The default name is no name of its own: it follows the language.
            if (name.equals(defaultName(i))) name = "";
            try {
                names.rename(i, name);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            fields[i].setText(names.custom(i).orElse(defaultName(i)));
        }
        return SettingsTexts.format("status.applied", SettingsCategory.WORKSPACES.label());
    }
}
