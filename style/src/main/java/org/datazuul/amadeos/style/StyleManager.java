package org.datazuul.amadeos.style;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.input.InputApplier;
import org.datazuul.amadeos.input.InputSettingsStore;
import org.datazuul.amadeos.screen.ScreenSettingsStore;
import org.datazuul.amadeos.base.theme.ThemeManager;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * The Style Manager, amadeOS's dtstyle: a small window with one button per control (Color, Font,
 * Backdrop, Keyboard, Mouse, Beep, Screen, Window, Startup). Each button opens the dialog of that
 * control; controls amadeOS does not offer yet are shown disabled.
 */
public final class StyleManager extends JFrame {
    private final Map<StyleCategory, JDialog> open = new EnumMap<>(StyleCategory.class);
    private final Map<StyleCategory, AbstractButton> buttons = new EnumMap<>(StyleCategory.class);
    private final JLabel status = new JLabel(" ");
    private final ScreenSettingsStore screenSettings;
    private final InputControls input;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(StyleManager::open);
    }

    /** Opens a Style Manager window for the current user; call on the EDT. */
    public static StyleManager open() {
        StyleManager manager = new StyleManager(ScreenSettingsStore.user(), InputSettingsStore.user(), new InputApplier());
        manager.setVisible(true);
        return manager;
    }

    public StyleManager(ScreenSettingsStore screenSettings, InputSettingsStore inputSettings, InputApplier applier) {
        super("Style Manager");
        this.screenSettings = screenSettings;
        this.input = new InputControls(inputSettings, applier);
        ThemeManager.currentTheme(); // installs the desktop look
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(menuBar());

        JPanel row = new JPanel(new GridLayout(1, 0, 4, 0));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (StyleCategory category : StyleCategory.values()) {
            JButton button = new JButton(category.label(), StyleIcons.of(category));
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setFont(button.getFont().deriveFont(11f));
            button.setEnabled(category.available());
            button.setToolTipText(category.available() ? category.description()
                    : category.description() + " (not available yet)");
            button.addActionListener(e -> openControl(category));
            buttons.put(category, button);
            row.add(button);
        }

        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        status.setFont(status.getFont().deriveFont(11f));
        statusBar.add(status);

        JPanel content = new JPanel(new BorderLayout());
        content.add(row, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        setContentPane(content);

        // The Style Manager changes the theme itself; restyle its windows like any application.
        ThemeManager.addListener(theme -> SwingUtilities.invokeLater(() -> AmadeUI.apply(theme)));
        pack();
        setResizable(false);
        setLocationByPlatform(true);
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem exit = new JMenuItem("Exit");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exit.addActionListener(e -> dispose());
        file.add(exit);
        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        JMenuItem about = new JMenuItem("About Style Manager");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Style Manager\nColors, backdrop, keyboard, mouse, bell and screen saver of the amadeOS desktop.",
                "About Style Manager", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(file);
        bar.add(help);
        return bar;
    }

    /** Opens the dialog of a control, or brings it to the front if it is already open. */
    public void openControl(StyleCategory category) {
        JDialog existing = open.get(category);
        if (existing != null && existing.isDisplayable()) {
            existing.toFront();
            return;
        }
        JDialog dialog = switch (category) {
            case COLOR -> new ColorDialog(this, this::setStatus);
            case BACKDROP -> new BackdropDialog(this, this::setStatus);
            case KEYBOARD -> new KeyboardDialog(this, this::setStatus, input);
            case MOUSE -> new MouseDialog(this, this::setStatus, input);
            case BEEP -> new BeepDialog(this, this::setStatus, input);
            case SCREEN -> new ScreenDialog(this, this::setStatus, screenSettings);
            default -> null;
        };
        if (dialog == null) return;
        open.put(category, dialog);
        dialog.setVisible(true);
    }

    void setStatus(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    AbstractButton button(StyleCategory category) {
        return buttons.get(category);
    }
}
