package org.datazuul.amadeos.style;

import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.base.x11.X11EwmhHelper;
import org.datazuul.amadeos.base.theme.IconManager;
import org.datazuul.amadeos.base.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Color: the desktop palette (theme) with a preview of its colours, and the icon theme.
 */
final class ColorDialog extends StyleDialog {
    private static final Logger LOGGER = Logger.getLogger(ColorDialog.class.getName());

    private final JList<DesktopTheme> themes = new JList<>(ThemeManager.availableThemes().toArray(DesktopTheme[]::new));
    private final JPanel palette = new JPanel(new GridLayout(2, 5, 6, 6));
    private final JComboBox<IconManager.IconThemeInfo> iconThemes;
    private final JPanel iconPreview = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));

    ColorDialog(JFrame owner, Consumer<String> status) {
        super(owner, StyleCategory.COLOR, status);

        themes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        themes.setVisibleRowCount(6);
        themes.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof DesktopTheme t) {
                    setText(t.name());
                    setIcon(swatch(t));
                }
                return this;
            }
        });
        themes.setSelectedValue(ThemeManager.currentTheme(), true);
        themes.addListSelectionListener(e -> updatePalette());

        JPanel paletteBox = new JPanel(new BorderLayout(10, 0));
        paletteBox.setBorder(BorderFactory.createTitledBorder("Palette"));
        JScrollPane list = new JScrollPane(themes);
        list.setPreferredSize(new Dimension(230, 150));
        paletteBox.add(list, BorderLayout.WEST);
        paletteBox.add(palette, BorderLayout.CENTER);
        updatePalette();

        List<IconManager.IconThemeInfo> available = IconManager.availableIconThemes();
        iconThemes = new JComboBox<>(available.toArray(IconManager.IconThemeInfo[]::new));
        iconThemes.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof IconManager.IconThemeInfo info) setText(info.name());
                return this;
            }
        });
        String activeIcons = ThemeManager.currentTheme().iconTheme();
        for (IconManager.IconThemeInfo info : available) {
            if (info.id().equalsIgnoreCase(activeIcons)) iconThemes.setSelectedItem(info);
        }
        iconThemes.addActionListener(e -> updateIconPreview());
        JPanel iconBox = new JPanel(new BorderLayout(0, 6));
        iconBox.setBorder(BorderFactory.createTitledBorder("Icons"));
        JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        iconRow.add(new JLabel("Icon theme:"));
        iconRow.add(iconThemes);
        iconBox.add(iconRow, BorderLayout.NORTH);
        iconBox.add(iconPreview, BorderLayout.CENTER);
        updateIconPreview();

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(paletteBox, BorderLayout.CENTER);
        content.add(iconBox, BorderLayout.SOUTH);
        layoutDialog(content);
    }

    private void updatePalette() {
        DesktopTheme t = themes.getSelectedValue() != null ? themes.getSelectedValue() : ThemeManager.currentTheme();
        palette.removeAll();
        Color[] colors = {t.base(), t.activeTitleBg(), t.inactiveTitleBg(), t.highlight(), t.softHighlight(),
                t.sunkenBg(), t.shadow(), t.darkShadow(), t.textActive(), t.textInactive()};
        String[] names = {"Base", "Active title", "Inactive title", "Highlight", "Soft highlight",
                "Sunken", "Shadow", "Dark shadow", "Text", "Inactive text"};
        for (int i = 0; i < colors.length; i++) palette.add(chip(colors[i], names[i]));
        palette.revalidate();
        palette.repaint();
    }

    private static JComponent chip(Color color, String name) {
        JPanel chip = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(color);
                g.fillRect(0, 0, getWidth(), getHeight());
                org.datazuul.amadeos.commons.swing.AmadeUI.paintBevel(g, 0, 0, getWidth(), getHeight(), true, 2);
            }
        };
        chip.setPreferredSize(new Dimension(44, 28));
        chip.setToolTipText(String.format("%s  #%02X%02X%02X", name, color.getRed(), color.getGreen(), color.getBlue()));
        return chip;
    }

    private void updateIconPreview() {
        iconPreview.removeAll();
        IconManager.IconThemeInfo selected = (IconManager.IconThemeInfo) iconThemes.getSelectedItem();
        String id = selected != null ? selected.id() : ThemeManager.currentTheme().iconTheme();
        for (String key : new String[]{"file-manager", "text-editor", "terminal", "lock", "power", "trash"}) {
            Icon icon = IconManager.getIcon(id, key, 32);
            JLabel label = new JLabel(icon);
            label.setToolTipText(key);
            iconPreview.add(label);
        }
        iconPreview.revalidate();
        iconPreview.repaint();
    }

    @Override
    protected String apply() {
        StringBuilder message = new StringBuilder();
        DesktopTheme theme = themes.getSelectedValue();
        if (theme != null && !theme.id().equals(ThemeManager.currentTheme().id())) {
            ThemeManager.applyTheme(theme.id());
            try (X11EwmhHelper helper = X11EwmhHelper.open(null)) {
                helper.setDesktopTheme(theme.id());
            } catch (Exception e) {
                LOGGER.fine("No X11 theme broadcast: " + e.getMessage());
            }
            message.append("Palette: ").append(theme.name());
        }
        IconManager.IconThemeInfo icons = (IconManager.IconThemeInfo) iconThemes.getSelectedItem();
        if (icons != null && !icons.id().equalsIgnoreCase(ThemeManager.currentTheme().iconTheme())) {
            ThemeManager.applyIconTheme(icons.id());
            if (!message.isEmpty()) message.append(", ");
            message.append("icons: ").append(icons.name());
        }
        return message.isEmpty() ? "Color unchanged" : message.toString();
    }

    private static Icon swatch(DesktopTheme t) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(t.base());
                g.fillRect(x, y, 10, 14);
                g.setColor(t.activeTitleBg());
                g.fillRect(x + 10, y, 10, 14);
                g.setColor(t.darkShadow());
                g.drawRect(x, y, 19, 13);
            }

            @Override
            public int getIconWidth() {
                return 20;
            }

            @Override
            public int getIconHeight() {
                return 14;
            }
        };
    }
}
