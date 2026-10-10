package org.datazuul.dazeos.wm.settings;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.datazuul.dazeos.wm.panel.X11EwmhHelper;
import org.datazuul.dazeos.wm.theme.DesktopTheme;
import org.datazuul.dazeos.wm.theme.IconManager;
import org.datazuul.dazeos.wm.theme.ThemeManager;
import org.datazuul.dazeos.wm.theme.WallpaperManager;
import org.datazuul.dazeos.wm.theme.WallpaperManager.ScaleMode;
import org.datazuul.dazeos.wm.theme.WallpaperManager.WallpaperItem;

/**
 * Dedicated DazeOS Settings Application for Appearance, Themes,
 * Wallpaper management, and Desktop behavior.
 */
public final class DazeSettingsApplication extends JFrame {

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JLabel statusLabel = new JLabel("DazeOS Settings ready.", JLabel.LEFT);

    // Color theme tab components
    private JComboBox<DesktopTheme> themeCombo;
    private JPanel themePalettePreview;

    // Icon theme tab components
    private JComboBox<IconManager.IconThemeInfo> iconThemeCombo;
    private JPanel iconPreviewStrip;

    // Wallpaper tab components
    private DefaultListModel<WallpaperItem> wallpaperListModel;
    private JList<WallpaperItem> wallpaperList;
    private JTextField customWallpaperField;
    private JComboBox<ScaleMode> scaleModeCombo;
    private JPanel colorSwatchButton;
    private Color selectedBgColor = new Color(0x4A, 0x69, 0x84);
    private JPanel wallpaperPreviewPanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DazeSettingsApplication app = new DazeSettingsApplication();
            app.setVisible(true);
        });
    }

    public DazeSettingsApplication() {
        super("DazeOS Desktop Settings");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // 1. Header panel
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
        JLabel titleLabel = new JLabel("DazeOS Desktop Settings", JLabel.LEFT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16.0f));
        JLabel subLabel = new JLabel("Customize appearance, color schemes, Freedesktop icon packs, and desktop wallpapers.");
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subLabel, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Tabs
        tabbedPane.addTab("🎨 Color Themes", createThemeTab());
        tabbedPane.addTab("📁 Icon Themes", createIconThemeTab());
        tabbedPane.addTab("🖼 Wallpaper", createWallpaperTab());
        tabbedPane.addTab("🖥 Window Manager", createWmTab());
        add(tabbedPane, BorderLayout.CENTER);

        // 3. Footer with status and close
        JPanel footerPanel = new JPanel(new BorderLayout(8, 0));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(6, 14, 10, 14));
        footerPanel.add(statusLabel, BorderLayout.WEST);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.add(closeBtn);
        footerPanel.add(btnPanel, BorderLayout.EAST);
        add(footerPanel, BorderLayout.SOUTH);

        // Dynamic theme synchronization for settings dialog itself
        ThemeManager.addListener(t -> SwingUtilities.invokeLater(this::applyCurrentThemeColors));
        applyCurrentThemeColors();

        setPreferredSize(new Dimension(740, 520));
        setMinimumSize(new Dimension(640, 440));
        pack();
        setLocationByPlatform(true);
    }

    private JPanel createThemeTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel("Active Color Theme:");
        themeCombo = new JComboBox<>(ThemeManager.availableThemes().toArray(new DesktopTheme[0]));
        themeCombo.setSelectedItem(ThemeManager.currentTheme());
        themeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DesktopTheme dt) {
                    l.setText(dt.name() + " (" + dt.id() + ")");
                    BufferedImage swatch = new BufferedImage(14, 14, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = swatch.createGraphics();
                    g2.setColor(dt.base());
                    g2.fillRect(0, 0, 14, 14);
                    g2.setColor(dt.darkShadow());
                    g2.drawRect(0, 0, 13, 13);
                    g2.dispose();
                    l.setIcon(new ImageIcon(swatch));
                }
                return l;
            }
        });
        themeCombo.addActionListener(e -> updateThemePalettePreview());

        JButton applyBtn = new JButton("Apply Theme Globally");
        applyBtn.addActionListener(e -> {
            DesktopTheme selected = (DesktopTheme) themeCombo.getSelectedItem();
            if (selected != null) {
                ThemeManager.applyTheme(selected.id());
                try (X11EwmhHelper helper = X11EwmhHelper.open(null)) {
                    helper.setDesktopTheme(selected.id());
                } catch (Exception ignored) {}
                statusLabel.setText("Applied desktop color theme: " + selected.name());
            }
        });

        topRow.add(label, BorderLayout.WEST);
        topRow.add(themeCombo, BorderLayout.CENTER);
        topRow.add(applyBtn, BorderLayout.EAST);
        panel.add(topRow, BorderLayout.NORTH);

        // Theme palette swatches preview
        themePalettePreview = new JPanel(new GridLayout(2, 5, 8, 8));
        themePalettePreview.setBorder(BorderFactory.createTitledBorder("Theme Color Palette Preview"));
        updateThemePalettePreview();
        panel.add(themePalettePreview, BorderLayout.CENTER);

        return panel;
    }

    private void updateThemePalettePreview() {
        if (themePalettePreview == null || themeCombo == null) return;
        themePalettePreview.removeAll();

        DesktopTheme dt = (DesktopTheme) themeCombo.getSelectedItem();
        if (dt == null) dt = ThemeManager.currentTheme();

        addSwatchBox(themePalettePreview, "Base", dt.base());
        addSwatchBox(themePalettePreview, "Active Title", dt.activeTitleBg());
        addSwatchBox(themePalettePreview, "Inactive Title", dt.inactiveTitleBg());
        addSwatchBox(themePalettePreview, "Highlight", dt.highlight());
        addSwatchBox(themePalettePreview, "Soft Highlight", dt.softHighlight());
        addSwatchBox(themePalettePreview, "Sunken Bg", dt.sunkenBg());
        addSwatchBox(themePalettePreview, "Shadow", dt.shadow());
        addSwatchBox(themePalettePreview, "Dark Shadow", dt.darkShadow());
        addSwatchBox(themePalettePreview, "Active Text", dt.textActive());
        addSwatchBox(themePalettePreview, "Inactive Text", dt.textInactive());

        themePalettePreview.revalidate();
        themePalettePreview.repaint();
    }

    private void addSwatchBox(JPanel container, String title, Color color) {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JPanel swatch = new JPanel();
        swatch.setBackground(color);
        swatch.setPreferredSize(new Dimension(40, 36));

        String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        JLabel lbl = new JLabel("<html><center><small><b>" + title + "</b><br>" + hex + "</small></center></html>", JLabel.CENTER);

        box.add(swatch, BorderLayout.CENTER);
        box.add(lbl, BorderLayout.SOUTH);
        container.add(box);
    }

    private JPanel createIconThemeTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        List<IconManager.IconThemeInfo> iconThemes = IconManager.availableIconThemes();
        iconThemeCombo = new JComboBox<>(iconThemes.toArray(new IconManager.IconThemeInfo[0]));
        iconThemeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof IconManager.IconThemeInfo info) {
                    l.setText(info.name() + " (" + info.id() + ")");
                }
                return l;
            }
        });
        iconThemeCombo.addActionListener(e -> updateIconPreviewStrip());

        JButton applyIconBtn = new JButton("Apply Icon Theme");
        applyIconBtn.addActionListener(e -> {
            IconManager.IconThemeInfo sel = (IconManager.IconThemeInfo) iconThemeCombo.getSelectedItem();
            if (sel != null) {
                IconManager.clearCache();
                statusLabel.setText("Icon cache refreshed for theme: " + sel.name());
                JOptionPane.showMessageDialog(this, "Icon theme changed to '" + sel.name() + "'. Icons refreshed.", "Icon Theme", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(new JLabel("Freedesktop Icon Theme:"), BorderLayout.WEST);
        top.add(iconThemeCombo, BorderLayout.CENTER);
        top.add(applyIconBtn, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        iconPreviewStrip = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 14));
        iconPreviewStrip.setBorder(BorderFactory.createTitledBorder("Standard Freedesktop Icons Preview"));
        updateIconPreviewStrip();
        panel.add(iconPreviewStrip, BorderLayout.CENTER);

        return panel;
    }

    private void updateIconPreviewStrip() {
        if (iconPreviewStrip == null) return;
        iconPreviewStrip.removeAll();

        String[] sampleKeys = {"file-manager", "text-editor", "terminal", "lock", "power", "trash", "diagnostics", "window"};
        for (String k : sampleKeys) {
            JPanel card = new JPanel(new BorderLayout(0, 4));
            Icon icon = IconManager.getIcon(k, 32);
            JLabel iconLbl = new JLabel(icon, JLabel.CENTER);
            JLabel textLbl = new JLabel(k, JLabel.CENTER);
            card.add(iconLbl, BorderLayout.CENTER);
            card.add(textLbl, BorderLayout.SOUTH);
            iconPreviewStrip.add(card);
        }
        iconPreviewStrip.revalidate();
        iconPreviewStrip.repaint();
    }

    private JPanel createWallpaperTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Left control column
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setPreferredSize(new Dimension(340, 360));

        // Available wallpapers list
        wallpaperListModel = new DefaultListModel<>();
        for (WallpaperItem item : WallpaperManager.availableWallpapers()) {
            wallpaperListModel.addElement(item);
        }
        wallpaperList = new JList<>(wallpaperListModel);
        wallpaperList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                WallpaperItem sel = wallpaperList.getSelectedValue();
                if (sel != null) {
                    customWallpaperField.setText(sel.path());
                    updateWallpaperPreview();
                }
            }
        });

        JPanel listBox = new JPanel(new BorderLayout(0, 4));
        listBox.setBorder(BorderFactory.createTitledBorder("Available Wallpapers"));
        listBox.add(new JScrollPane(wallpaperList), BorderLayout.CENTER);
        listBox.setMaximumSize(new Dimension(Short.MAX_VALUE, 160));
        leftCol.add(listBox);

        leftCol.add(Box.createVerticalStrut(8));

        // File browser row
        JPanel fileBox = new JPanel(new BorderLayout(6, 0));
        customWallpaperField = new JTextField(WallpaperManager.currentWallpaperPath() != null ? WallpaperManager.currentWallpaperPath() : "");
        customWallpaperField.addActionListener(e -> updateWallpaperPreview());
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Wallpaper Images (*.png, *.jpg)", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File chosen = chooser.getSelectedFile();
                customWallpaperField.setText(chosen.getAbsolutePath());
                updateWallpaperPreview();
            }
        });
        fileBox.add(new JLabel("Image Path:"), BorderLayout.WEST);
        fileBox.add(customWallpaperField, BorderLayout.CENTER);
        fileBox.add(browseBtn, BorderLayout.EAST);
        leftCol.add(fileBox);

        leftCol.add(Box.createVerticalStrut(8));

        // Scaling mode and solid color
        JPanel optsBox = new JPanel(new GridLayout(2, 2, 6, 6));
        optsBox.setBorder(BorderFactory.createTitledBorder("Display Options"));
        scaleModeCombo = new JComboBox<>(ScaleMode.values());
        scaleModeCombo.setSelectedItem(WallpaperManager.currentScaleMode());
        scaleModeCombo.addActionListener(e -> updateWallpaperPreview());

        colorSwatchButton = new JPanel();
        colorSwatchButton.setBackground(selectedBgColor);
        colorSwatchButton.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        JButton pickColorBtn = new JButton("Color...");
        pickColorBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Select Backdrop Color", selectedBgColor);
            if (chosen != null) {
                selectedBgColor = chosen;
                colorSwatchButton.setBackground(chosen);
                updateWallpaperPreview();
            }
        });

        optsBox.add(new JLabel("Scale Mode:"));
        optsBox.add(scaleModeCombo);
        optsBox.add(pickColorBtn);
        optsBox.add(colorSwatchButton);
        leftCol.add(optsBox);

        leftCol.add(Box.createVerticalStrut(10));

        // Apply wallpaper button
        JButton applyWpBtn = new JButton("Apply Wallpaper Now");
        applyWpBtn.setFont(applyWpBtn.getFont().deriveFont(Font.BOLD));
        applyWpBtn.addActionListener(e -> {
            String path = customWallpaperField.getText().trim();
            ScaleMode mode = (ScaleMode) scaleModeCombo.getSelectedItem();
            WallpaperManager.applyWallpaper(path, mode, selectedBgColor);
            statusLabel.setText("Wallpaper applied: " + (path.isEmpty() ? "Solid Color" : path));
        });
        leftCol.add(applyWpBtn);

        // Right preview column: interactive mini workstation screen preview
        wallpaperPreviewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth() - 20;
                int h = getHeight() - 20;
                int x = 10;
                int y = 10;

                // Bezel monitor border
                g2.setColor(new Color(0x30, 0x30, 0x30));
                g2.fillRect(x - 6, y - 6, w + 12, h + 12);
                g2.setColor(new Color(0x60, 0x60, 0x60));
                g2.drawRect(x - 6, y - 6, w + 12, h + 12);

                // Render current wallpaper in preview
                String path = customWallpaperField != null ? customWallpaperField.getText().trim() : null;
                ScaleMode mode = scaleModeCombo != null ? (ScaleMode) scaleModeCombo.getSelectedItem() : ScaleMode.FILL;
                BufferedImage src = WallpaperManager.loadImage(path);
                BufferedImage rendered = WallpaperManager.renderToTarget(src, w, h, mode, selectedBgColor);
                g2.drawImage(rendered, x, y, null);

                // Mini CDE front panel dock at bottom of preview
                DesktopTheme t = ThemeManager.currentTheme();
                int dockW = (int) (w * 0.72);
                int dockH = Math.max(12, (int) (h * 0.08));
                int dockX = x + (w - dockW) / 2;
                int dockY = y + h - dockH - 2;

                g2.setColor(t.base());
                g2.fillRect(dockX, dockY, dockW, dockH);
                g2.setColor(t.highlight());
                g2.drawRect(dockX, dockY, dockW, dockH);
            }
        };
        wallpaperPreviewPanel.setBorder(BorderFactory.createTitledBorder("Live Desktop Preview"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCol, wallpaperPreviewPanel);
        split.setDividerLocation(360);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private void updateWallpaperPreview() {
        if (wallpaperPreviewPanel != null) {
            wallpaperPreviewPanel.repaint();
        }
    }

    private JPanel createWmTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JCheckBox focusPolicyCheck = new JCheckBox("Click to Focus (Motif/CDE default)", true);
        JCheckBox tilesCheck = new JCheckBox("Park minimized windows as Desktop Icon Tiles (Top-left vertical stack)", true);
        JCheckBox dockCheck = new JCheckBox("Show CDE Front Panel dock centered at screen bottom", true);
        JCheckBox soundCheck = new JCheckBox("System Bell & Alert Audio", true);

        panel.add(focusPolicyCheck);
        panel.add(Box.createVerticalStrut(10));
        panel.add(tilesCheck);
        panel.add(Box.createVerticalStrut(10));
        panel.add(dockCheck);
        panel.add(Box.createVerticalStrut(10));
        panel.add(soundCheck);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private void applyCurrentThemeColors() {
        DesktopTheme t = ThemeManager.currentTheme();
        getContentPane().setBackground(t.base());
        tabbedPane.setBackground(t.base());
        repaint();
    }
}
