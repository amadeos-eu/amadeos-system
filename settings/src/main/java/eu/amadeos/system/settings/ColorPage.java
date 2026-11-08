package eu.amadeos.system.settings;

import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.x11.X11EwmhHelper;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Color: the colours of the desktop and the icon theme. With the classic look these are the palettes (themes)
 * with a preview of their colours; with the Horizon look Horizon's scheme and accent colour, the same controls as
 * on the Desktop look page, so that changing the colours never changes the look. Which of the two the page shows
 * follows the look whenever the page is shown.
 */
final class ColorPage extends SettingsPage {
    private static final Logger LOGGER = Logger.getLogger(ColorPage.class.getName());

    private final JList<DesktopTheme> themes = new JList<>(ThemeManager.availableThemes().toArray(DesktopTheme[]::new));
    private final JPanel palette = new JPanel(new GridLayout(2, 7, 6, 6));
    private final JComboBox<IconManager.IconThemeInfo> iconThemes;
    private final JPanel iconPreview = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
    private final HorizonColors horizonColors = new HorizonColors(() -> { });
    private final CardLayout colorCards = new CardLayout();
    private final JPanel colorBox = new JPanel(colorCards);
    private DesktopLook shownLook;

    ColorPage(Consumer<String> status) {
        super(SettingsCategory.COLOR, status);

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
        paletteBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("color.palette")));
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
        iconBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("color.icons")));
        JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        iconRow.add(new JLabel(SettingsTexts.get("color.icon.theme")));
        iconRow.add(iconThemes);
        iconBox.add(iconRow, BorderLayout.NORTH);
        iconBox.add(iconPreview, BorderLayout.CENTER);
        updateIconPreview();

        JPanel horizonCard = new JPanel(new BorderLayout(0, 10));
        horizonCard.add(note(SettingsTexts.get("color.horizon.note")), BorderLayout.NORTH);
        // At the top, as tall as its lines: the card is as tall as the palettes of the classic look
        JPanel horizonTop = new JPanel(new BorderLayout());
        horizonTop.add(horizonColors.component(), BorderLayout.NORTH);
        horizonCard.add(horizonTop, BorderLayout.CENTER);
        colorBox.add(paletteBox, DesktopLook.CLASSIC.id());
        colorBox.add(horizonCard, DesktopLook.HORIZON.id());

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(colorBox, BorderLayout.CENTER);
        content.add(iconBox, BorderLayout.SOUTH);
        layoutPage(content);
        showLook();
        // The look can change while Settings is open (Desktop look page, a session switch): follow it.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) showLook();
        });
    }

    @Override
    protected void cancelled() {
        shownLook = null;
        showLook();
    }

    /** Shows the palettes or Horizon's colours, whichever belong to the current look. */
    private void showLook() {
        DesktopLook look = ThemeManager.currentLook();
        if (look == shownLook) return;
        shownLook = look;
        horizonColors.reset();
        themes.setSelectedValue(ThemeManager.currentTheme(), true);
        colorCards.show(colorBox, look.id());
    }

    private void updatePalette() {
        DesktopTheme t = themes.getSelectedValue() != null ? themes.getSelectedValue() : ThemeManager.currentTheme();
        palette.removeAll();
        Color[] colors = {t.base(), t.activeTitleBg(), t.inactiveTitleBg(), t.highlight(), t.softHighlight(),
                t.sunkenBg(), t.shadow(), t.darkShadow(), t.textActive(), t.textInactive(),
                t.workspaceColor(0), t.workspaceColor(1), t.workspaceColor(2), t.workspaceColor(3)};
        String[] names = {"base", "active.title", "inactive.title", "highlight", "soft.highlight",
                "sunken", "shadow", "dark.shadow", "text", "inactive.text",
                "workspace.1", "workspace.2", "workspace.3", "workspace.4"};
        for (int i = 0; i < colors.length; i++) palette.add(chip(colors[i], SettingsTexts.get("color.chip." + names[i])));
        palette.revalidate();
        palette.repaint();
    }

    private static JComponent chip(Color color, String name) {
        JPanel chip = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(color);
                g.fillRect(0, 0, getWidth(), getHeight());
                eu.amadeos.commons.swing.AmadeUI.paintBevel(g, 0, 0, getWidth(), getHeight(), true, 2);
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

    // --8<-- [start:apply]
    @Override
    protected String apply() {
        StringBuilder message = new StringBuilder();
        if (shownLook == DesktopLook.HORIZON) {
            // Horizon's colours: the look stays Horizon
            DesktopTheme before = ThemeManager.currentTheme();
            ThemeManager.applyHorizonColors(horizonColors.scheme(), horizonColors.accent());
            DesktopTheme after = ThemeManager.currentTheme();
            if (!after.id().equals(before.id())) {
                horizonColors.followTheme(before, after);
                message.append(horizonColors.status());
            }
        }
        DesktopTheme theme = themes.getSelectedValue();
        if (shownLook == DesktopLook.CLASSIC && theme != null && !theme.id().equals(ThemeManager.currentTheme().id())) {
            ThemeManager.applyTheme(theme.id());
            try (X11EwmhHelper helper = X11EwmhHelper.open(null)) {
                helper.setDesktopTheme(theme.id());
            } catch (Exception e) {
                LOGGER.fine("No X11 theme broadcast: " + e.getMessage());
            }
            message.append(SettingsTexts.format("color.status.palette", theme.name()));
        }
        IconManager.IconThemeInfo icons = (IconManager.IconThemeInfo) iconThemes.getSelectedItem();
        if (icons != null && !icons.id().equalsIgnoreCase(ThemeManager.currentTheme().iconTheme())) {
            ThemeManager.applyIconTheme(icons.id());
            if (!message.isEmpty()) message.append(", ");
            message.append(SettingsTexts.format("color.status.icons", icons.name()));
        }
        return message.isEmpty() ? SettingsTexts.get("color.unchanged") : message.toString();
    }
    // --8<-- [end:apply]

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
