package eu.amadeos.system.settings;

import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.input.AccessibilitySettings;
import eu.amadeos.system.input.AccessibilitySettings.Contrast;
import eu.amadeos.system.input.AccessibilitySettings.Pointer;
import eu.amadeos.system.input.AccessibilityStore;
import eu.amadeos.system.input.XCommands;
import eu.amadeos.system.settings.InputControls.LabeledSlider;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Accessibility: seeing (high contrast palette, text size, a larger or darker mouse pointer), typing (XKB's sticky,
 * slow and bounce keys) and the pointer without a mouse (mouse keys). The keyboard aids and the pointer are
 * applied with {@code xkbset}, {@code xrdb} and {@code xsetroot} and again at every login; high contrast is a
 * palette like the others, so it stays until another palette is chosen.
 */
final class AccessibilityPage extends SettingsPage {
    /** The palettes, so tests need no desktop: the current one and a way to switch. */
    record Themes(Supplier<String> current, Consumer<String> apply) {
        static Themes desktop() {
            return new Themes(() -> ThemeManager.currentTheme().id(), ThemeManager::applyTheme);
        }
    }

    private final AccessibilityStore store;
    private final InputControls controls;
    private final Themes themes;
    private final AccessibilitySettings saved;

    private final JComboBox<Contrast> contrast = new JComboBox<>(Contrast.values());
    private final JComboBox<Pointer> pointer = new JComboBox<>(Pointer.values());
    private final JComboBox<Integer> pointerSize = new JComboBox<>(
            Arrays.stream(AccessibilitySettings.POINTER_SIZES).boxed().toArray(Integer[]::new));
    private final JCheckBox sticky = new JCheckBox(SettingsTexts.get("accessibility.sticky"));
    private final JCheckBox stickyLock = new JCheckBox(SettingsTexts.get("accessibility.sticky.lock"));
    private final JCheckBox stickyTwoKeys = new JCheckBox(SettingsTexts.get("accessibility.sticky.twokeys"));
    private final JCheckBox slow = new JCheckBox(SettingsTexts.get("accessibility.slow"));
    private final LabeledSlider slowMs;
    private final JCheckBox bounce = new JCheckBox(SettingsTexts.get("accessibility.bounce"));
    private final LabeledSlider bounceMs;
    private final JCheckBox gestures = new JCheckBox(SettingsTexts.get("accessibility.gestures"));
    private final JCheckBox feedback = new JCheckBox(SettingsTexts.get("accessibility.feedback"));
    private final JCheckBox mouseKeys = new JCheckBox(SettingsTexts.get("accessibility.mousekeys"));

    AccessibilityPage(Consumer<String> status, AccessibilityStore store, InputControls controls, Runnable openFont) {
        this(status, store, controls, openFont, Themes.desktop());
    }

    AccessibilityPage(Consumer<String> status, AccessibilityStore store, InputControls controls, Runnable openFont,
                      Themes themes) {
        super(SettingsCategory.ACCESSIBILITY, status);
        this.store = store;
        this.controls = controls;
        this.themes = themes;
        this.saved = store.load();
        slowMs = new LabeledSlider(50, 1500, saved.slowKeysMs(), v -> v + " ms");
        bounceMs = new LabeledSlider(50, 1000, saved.bounceKeysMs(), v -> v + " ms");

        contrast.setRenderer(AccessibilityPage.<Contrast>renderer(c -> SettingsTexts.get("accessibility.contrast." + c.id())));
        pointer.setRenderer(AccessibilityPage.<Pointer>renderer(p -> SettingsTexts.get("accessibility.pointer." + p.id())));
        pointerSize.setRenderer(AccessibilityPage.<Integer>renderer(size -> SettingsTexts.format("accessibility.pointer.size." + size, size)));
        pointer.addActionListener(e -> updateEnabled());
        for (JCheckBox box : new JCheckBox[]{sticky, slow, bounce}) box.addActionListener(e -> updateEnabled());
        JButton textSize = new JButton(SettingsTexts.get("accessibility.textsize.button"));
        textSize.addActionListener(e -> openFont.run());
        show(saved);

        JPanel seeing = section(SettingsTexts.get("accessibility.seeing"),
                InputControls.form(
                        SettingsTexts.get("accessibility.contrast"), line0(contrast),
                        SettingsTexts.get("accessibility.textsize"), line0(textSize),
                        SettingsTexts.get("accessibility.pointer"), line0(pointer, new JLabel(SettingsTexts.get("accessibility.pointer.size")), pointerSize)),
                note(SettingsTexts.get("accessibility.pointer.note")));
        JPanel typing = section(SettingsTexts.get("accessibility.typing"),
                line(sticky), indented(stickyLock), indented(stickyTwoKeys),
                line(slow), indented(new JLabel(SettingsTexts.get("accessibility.slow.delay")), slowMs),
                line(bounce), indented(new JLabel(SettingsTexts.get("accessibility.bounce.delay")), bounceMs),
                line(gestures), line(feedback));
        JPanel pointing = section(SettingsTexts.get("accessibility.pointing"), line(mouseKeys),
                note(SettingsTexts.get("accessibility.mousekeys.note")));
        layoutSections(seeing, typing, pointing, note(SettingsTexts.get("accessibility.note")));
        updateEnabled();
    }

    private static JPanel line0(Component... components) {
        JPanel row = line(components);
        ((java.awt.FlowLayout) row.getLayout()).setHgap(6);
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, -6, 0, 0));
        return row;
    }

    private static JPanel indented(Component... components) {
        JPanel row = line(components);
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 24, 0, 0));
        return row;
    }

    private static <T> DefaultListCellRenderer renderer(Function<T, String> text) {
        return new DefaultListCellRenderer() {
            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value != null) setText(text.apply((T) value));
                return this;
            }
        };
    }

    private void show(AccessibilitySettings a) {
        contrast.setSelectedItem(contrastOf(themes.current().get()));
        pointer.setSelectedItem(a.pointer());
        pointerSize.setSelectedItem(a.pointerSize());
        sticky.setSelected(a.stickyKeys());
        stickyLock.setSelected(a.stickyLock());
        stickyTwoKeys.setSelected(a.stickyTwoKeys());
        slow.setSelected(a.slowKeys());
        slowMs.setValue(a.slowKeysMs());
        bounce.setSelected(a.bounceKeys());
        bounceMs.setValue(a.bounceKeysMs());
        gestures.setSelected(a.gestures());
        feedback.setSelected(a.feedback());
        mouseKeys.setSelected(a.mouseKeys());
    }

    /** Chooses a contrast as if picked in the list, for tests. */
    void showContrast(Contrast value) {
        contrast.setSelectedItem(value);
    }

    /** High contrast is on when one of its palettes is the current one, whoever chose it. */
    static Contrast contrastOf(String themeId) {
        for (Contrast c : Contrast.values()) {
            if (c != Contrast.OFF && c.themeId().equals(themeId)) return c;
        }
        return Contrast.OFF;
    }

    private void updateEnabled() {
        pointerSize.setEnabled(pointer.getSelectedItem() != Pointer.CLASSIC);
        stickyLock.setEnabled(sticky.isSelected());
        stickyTwoKeys.setEnabled(sticky.isSelected());
        slowMs.setEnabled(slow.isSelected());
        bounceMs.setEnabled(bounce.isSelected());
    }

    /** The settings as shown; the palette to go back to is taken on Apply. */
    AccessibilitySettings settings() {
        return saved.withPointer((Pointer) pointer.getSelectedItem(), (Integer) pointerSize.getSelectedItem())
                .withKeys(sticky.isSelected(), stickyLock.isSelected(), stickyTwoKeys.isSelected(), slow.isSelected(),
                        slowMs.value(), bounce.isSelected(), bounceMs.value(), mouseKeys.isSelected(),
                        gestures.isSelected(), feedback.isSelected())
                .withContrast((Contrast) contrast.getSelectedItem(), saved.previousTheme());
    }

    @Override
    protected String apply() {
        AccessibilitySettings a = settings();
        String current = themes.current().get();
        Contrast before = contrastOf(current);
        if (a.contrast() != before) {
            if (a.contrast() == Contrast.OFF) {
                String back = a.previousTheme().isEmpty() ? DesktopTheme.WARM_MOTIF.id() : a.previousTheme();
                themes.apply().accept(back);
            } else {
                // Remember the palette before high contrast, not the other high contrast palette.
                String previous = before == Contrast.OFF ? current : a.previousTheme();
                a = a.withContrast(a.contrast(), previous);
                themes.apply().accept(a.contrast().themeId());
                // Without the palette files in /etc/amadeos/themes nothing changed: say so, save nothing.
                if (!themes.current().get().equals(a.contrast().themeId())) {
                    throw new IllegalStateException(a.contrast().themeId());
                }
            }
        }
        store.save(a);
        return controls.apply(XCommands.accessibility(a), SettingsCategory.ACCESSIBILITY.label());
    }
}
