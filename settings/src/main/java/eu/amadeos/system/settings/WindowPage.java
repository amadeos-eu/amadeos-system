package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.base.window.WindowSettings;
import eu.amadeos.system.base.window.WindowSettingsStore;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.function.Consumer;

/**
 * Window: CDE's Window control. How a window is made active (click or pointer), whether it then comes to the
 * front, and whether it shows its contents while it is moved. Writes {@code window.conf}, which the window
 * manager follows at once.
 */
final class WindowPage extends SettingsPage {
    private final WindowSettingsStore store;
    private final JRadioButton click = new JRadioButton(SettingsTexts.get("window.focus.click"));
    private final JRadioButton pointer = new JRadioButton(SettingsTexts.get("window.focus.pointer"));
    private final JCheckBox raise = new JCheckBox(SettingsTexts.get("window.raise"));
    private final JCheckBox contents = new JCheckBox(SettingsTexts.get("window.move.contents"));

    WindowPage(Consumer<String> status, WindowSettingsStore store) {
        super(SettingsCategory.WINDOW, status);
        this.store = store;
        WindowSettings settings = store.load();
        ButtonGroup focus = new ButtonGroup();
        focus.add(click);
        focus.add(pointer);
        (settings.focus() == WindowSettings.Focus.POINTER ? pointer : click).setSelected(true);
        raise.setSelected(settings.raiseWhenActive());
        contents.setSelected(settings.showContentsDuringMove());

        JPanel focusBox = box(SettingsTexts.get("window.focus.title"), click, pointer, raise);
        JPanel moveBox = box(SettingsTexts.get("window.move.title"), contents);
        JLabel hint = new JLabel("<html>" + SettingsTexts.get("window.hint") + "</html>");
        AmadeUI.style(hint, (l, t) -> l.setFont(javax.swing.UIManager.getFont("Label.font").deriveFont(AmadeUI.scaled(11f))));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (JPanel part : new JPanel[]{focusBox, moveBox}) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(part);
            content.add(javax.swing.Box.createVerticalStrut(10));
        }
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(hint);
        JPanel top = new JPanel(new BorderLayout());
        top.add(content, BorderLayout.NORTH);
        layoutPage(top);
    }

    private static JPanel box(String title, Component... rows) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createTitledBorder(title));
        for (Component row : rows) box.add(row);
        return box;
    }

    WindowSettings selected() {
        return new WindowSettings(pointer.isSelected() ? WindowSettings.Focus.POINTER : WindowSettings.Focus.CLICK,
                raise.isSelected(), contents.isSelected());
    }

    @Override
    protected String apply() {
        store.save(selected());
        return SettingsTexts.format("status.applied", SettingsCategory.WINDOW.label());
    }
}
