package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Font: one of CDE's seven font sizes for the desktop's programs, with a preview. The size scales the standard
 * fonts of every amade application and desktop tool (amade-commons' {@link AmadeUI}); window titles and the
 * Front Panel keep theirs. The fonts themselves (which ones there are, installing new ones) are the Font
 * Viewer's ({@code amade-fontviewer}), which "Manage Fonts..." starts.
 */
final class FontPage extends SettingsPage {
    private final JList<Integer> sizes = new JList<>(IntStream.rangeClosed(DesktopTheme.MIN_FONT_SIZE,
            DesktopTheme.MAX_FONT_SIZE).boxed().toArray(Integer[]::new));
    private final JLabel labels = new JLabel(SettingsTexts.get("font.preview.labels"));
    private final JLabel text = new JLabel(SettingsTexts.get("font.preview.text"));
    private final JLabel sample = new JLabel(SettingsTexts.get("font.preview.sample"));

    static final List<String> FONT_VIEWER = List.of("amade-fontviewer");

    FontPage(Consumer<String> status, Consumer<List<String>> launcher) {
        super(SettingsCategory.FONT, status);
        sizes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sizes.setVisibleRowCount(DesktopTheme.MAX_FONT_SIZE);
        sizes.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(SettingsTexts.format("font.size.item", value, SettingsTexts.get("font.size." + value)));
                return this;
            }
        });
        sizes.setSelectedValue(ThemeManager.currentTheme().fontSize(), true);
        sizes.addListSelectionListener(e -> updatePreview());

        JScrollPane list = new JScrollPane(sizes);

        JPanel preview = new JPanel();
        preview.setLayout(new BoxLayout(preview, BoxLayout.Y_AXIS));
        preview.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        AmadeUI.style(preview, (p, t) -> {
            p.setOpaque(true);
            p.setBackground(t.fieldBackground());
            for (JLabel l : new JLabel[]{labels, text, sample}) l.setForeground(t.fieldText());
        });
        preview.add(labels);
        preview.add(javax.swing.Box.createVerticalStrut(6));
        preview.add(text);
        preview.add(javax.swing.Box.createVerticalStrut(6));
        preview.add(sample);

        JPanel sizeBox = new JPanel(new BorderLayout(10, 0));
        sizeBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("font.size")));
        sizeBox.add(list, BorderLayout.WEST);
        JPanel previewBox = new JPanel(new BorderLayout());
        previewBox.add(preview, BorderLayout.CENTER);
        sizeBox.add(previewBox, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html>" + SettingsTexts.get("font.hint") + "</html>");
        AmadeUI.style(hint, (l, t) -> l.setFont(javax.swing.UIManager.getFont("Label.font").deriveFont(AmadeUI.scaled(11f))));

        JButton manage = new JButton(SettingsTexts.get("font.manage"));
        manage.setName("font.manage");
        manage.addActionListener(e -> launcher.accept(FONT_VIEWER));
        JPanel fonts = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        fonts.add(manage);
        fonts.add(Box.createHorizontalStrut(10));
        fonts.add(new JLabel(SettingsTexts.get("font.manage.hint")));

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.add(hint, BorderLayout.NORTH);
        south.add(fonts, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(sizeBox, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        updatePreview();
        layoutPage(content);
    }

    private int selectedSize() {
        Integer size = sizes.getSelectedValue();
        return size != null ? size : ThemeManager.currentTheme().fontSize();
    }

    /** Shows the selected size with the look and feel's own fonts, as it will be after Apply. */
    private void updatePreview() {
        float scale = AmadeUI.fontScale(selectedSize());
        labels.setFont(scaled(UIManager.getLookAndFeelDefaults().getFont("Label.font"), scale));
        text.setFont(scaled(UIManager.getLookAndFeelDefaults().getFont("TextField.font"), scale));
        sample.setFont(scaled(new Font(Font.MONOSPACED, Font.PLAIN, 12), scale));
    }

    private static Font scaled(Font font, float scale) {
        Font base = font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12);
        return base.deriveFont((float) Math.round(base.getSize2D() * scale));
    }

    @Override
    protected String apply() {
        int size = selectedSize();
        ThemeManager.applyFontSize(size);
        return SettingsTexts.format("font.status", size, SettingsTexts.get("font.size." + size));
    }
}
