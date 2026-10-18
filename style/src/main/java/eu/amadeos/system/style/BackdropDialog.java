package eu.amadeos.system.style;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.WallpaperManager;
import eu.amadeos.system.base.theme.WallpaperManager.ScaleMode;
import eu.amadeos.system.base.theme.WallpaperManager.WallpaperItem;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Backdrop: the original CDE pattern backdrops or a picture, scaling and the backdrop colour, with a
 * miniature preview of the desktop. Patterns take their shades from the colour, as in dtstyle.
 */
final class BackdropDialog extends StyleDialog {
    private final DefaultListModel<WallpaperItem> model = new DefaultListModel<>();
    private final JList<WallpaperItem> wallpapers = new JList<>(model);
    private final JTextField path = new JTextField(24);
    private final JComboBox<ScaleMode> scale = new JComboBox<>(ScaleMode.values());
    private final JPanel colorChip = new JPanel();
    private final JPanel preview;
    private Color color = WallpaperManager.currentBgColor();
    private String cachedKey;
    private BufferedImage cachedImage;

    BackdropDialog(JFrame owner, Consumer<String> status) {
        super(owner, StyleCategory.BACKDROP, status);
        for (WallpaperItem item : WallpaperManager.availableWallpapers()) model.addElement(item);
        wallpapers.setVisibleRowCount(8);
        String current = WallpaperManager.currentWallpaperPath();
        path.setText(current != null ? current : "");
        // Preselect the current backdrop before the listener exists: it would reset the scaling.
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).path().equals(current)) {
                wallpapers.setSelectedIndex(i);
                wallpapers.ensureIndexIsVisible(i);
                break;
            }
        }
        wallpapers.addListSelectionListener(e -> {
            WallpaperItem item = wallpapers.getSelectedValue();
            if (!e.getValueIsAdjusting() && item != null) {
                path.setText(item.path());
                // CDE tiles its patterns; pictures keep the chosen scaling.
                if (item.isPattern()) scale.setSelectedItem(ScaleMode.TILE);
                refresh();
            }
        });
        path.addActionListener(e -> refresh());
        scale.setSelectedItem(WallpaperManager.currentScaleMode());
        scale.addActionListener(e -> refresh());

        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Images and CDE backdrops (*.png, *.jpg, *.pm, *.bm)",
                    "png", "jpg", "jpeg", "pm", "xpm", "bm", "xbm"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                path.setText(chooser.getSelectedFile().getAbsolutePath());
                refresh();
            }
        });
        colorChip.setPreferredSize(new Dimension(40, 20));
        colorChip.setBackground(color);
        colorChip.setBorder(AmadeUI.sunken());
        JButton pickColor = new JButton("Color…");
        pickColor.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Backdrop Color", color);
            if (chosen != null) {
                color = chosen;
                colorChip.setBackground(chosen);
                refresh();
            }
        });

        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 6);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        JScrollPane list = new JScrollPane(wallpapers);
        list.setPreferredSize(new Dimension(280, 150));
        controls.add(list, c);
        c.gridwidth = 1;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 1;
        c.weightx = 0;
        controls.add(new JLabel("Image:"), c);
        c.gridx = 1;
        c.weightx = 1;
        controls.add(path, c);
        c.gridx = 2;
        c.weightx = 0;
        controls.add(browse, c);
        c.gridy = 2;
        c.gridx = 0;
        controls.add(new JLabel("Scaling:"), c);
        c.gridx = 1;
        controls.add(scale, c);
        c.gridy = 3;
        c.gridx = 0;
        controls.add(new JLabel("Color:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        controls.add(colorChip, c);
        c.gridx = 2;
        controls.add(pickColor, c);

        preview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintPreview((Graphics2D) g, getWidth(), getHeight());
            }
        };
        preview.setPreferredSize(new Dimension(260, 180));
        preview.setBorder(BorderFactory.createTitledBorder("Preview"));

        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.add(controls, BorderLayout.CENTER);
        content.add(preview, BorderLayout.EAST);
        layoutDialog(content);
    }

    private void refresh() {
        if (preview != null) preview.repaint();
    }

    private void paintPreview(Graphics2D g, int width, int height) {
        int x = 14;
        int y = 22;
        int w = width - 28;
        int h = height - 36;
        if (w <= 0 || h <= 0) return;
        BufferedImage source = image(path.getText().strip());
        BufferedImage rendered = WallpaperManager.renderToTarget(source, w, h, (ScaleMode) scale.getSelectedItem(), color);
        g.drawImage(rendered, x, y, null);
        AmadeUI.paintBevel(g, x - 2, y - 2, w + 4, h + 4, false, 2);
        // Miniature front panel at the bottom, in the current theme.
        DesktopTheme t = AmadeUI.theme();
        int dockW = (int) (w * 0.7);
        int dockH = Math.max(8, h / 12);
        int dockX = x + (w - dockW) / 2;
        int dockY = y + h - dockH - 1;
        g.setColor(t.base());
        g.fillRect(dockX, dockY, dockW, dockH);
        AmadeUI.paintBevel(g, dockX, dockY, dockW, dockH, true, 1);
    }

    /** The selected picture, decoded once per path and colour rather than on every repaint. */
    private BufferedImage image(String file) {
        String key = file + '|' + color.getRGB();
        if (!key.equals(cachedKey)) {
            cachedKey = key;
            cachedImage = WallpaperManager.loadImage(file, color);
        }
        return cachedImage;
    }

    @Override
    protected String apply() {
        String image = path.getText().strip();
        WallpaperManager.applyWallpaper(image, (ScaleMode) scale.getSelectedItem(), color);
        boolean plain = image.isEmpty() || WallpaperManager.NO_BACKDROP.equalsIgnoreCase(image);
        return "Backdrop: " + (plain ? "solid color" : image);
    }
}
