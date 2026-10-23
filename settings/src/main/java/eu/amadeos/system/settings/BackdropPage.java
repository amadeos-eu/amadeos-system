package eu.amadeos.system.settings;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.WallpaperManager;
import eu.amadeos.system.base.theme.WallpaperManager.Backdrop;
import eu.amadeos.system.base.theme.WallpaperManager.ScaleMode;
import eu.amadeos.system.base.theme.WallpaperManager.WallpaperItem;
import eu.amadeos.system.base.workspace.WorkspaceNames;
import eu.amadeos.system.base.x11.X11EwmhHelper;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Backdrop: the original CDE pattern backdrops or a picture, scaling and the backdrop colour, with a
 * miniature preview of the desktop. Patterns take their shades from the colour, as in dtstyle. As in CDE,
 * each workspace can have its own backdrop: the dialog opens on the current workspace, and "All workspaces"
 * gives every workspace the same one.
 */
final class BackdropPage extends SettingsPage {
    private final DefaultListModel<WallpaperItem> model = new DefaultListModel<>();
    private final JList<WallpaperItem> wallpapers = new JList<>(model);
    private final JTextField path = new JTextField(24);
    private final JComboBox<ScaleMode> scale = new JComboBox<>(ScaleMode.values());
    private final JPanel colorChip = new JPanel();
    private final JPanel preview;
    private final JComboBox<WorkspaceChoice> workspace = new JComboBox<>();
    private Color color = WallpaperManager.currentBgColor();
    private String cachedKey;
    private BufferedImage cachedImage;

    BackdropPage(Consumer<String> status) {
        super(SettingsCategory.BACKDROP, status);
        scale.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                Object text = value instanceof ScaleMode mode
                        ? SettingsTexts.get("backdrop.scale." + mode.name().toLowerCase(Locale.ROOT)) : value;
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        for (WallpaperItem item : WallpaperManager.availableWallpapers()) model.addElement(item);
        wallpapers.setVisibleRowCount(8);
        int currentWorkspace = fillWorkspaces();
        Backdrop backdrop = WallpaperManager.backdropOf(currentWorkspace);
        // Show the current backdrop before the listener exists: it would reset the scaling.
        loadBackdrop(backdrop);
        wallpapers.addListSelectionListener(e -> {
            WallpaperItem item = wallpapers.getSelectedValue();
            if (!e.getValueIsAdjusting() && item != null && !loading) {
                path.setText(item.path());
                // CDE tiles its patterns; pictures keep the chosen scaling.
                if (item.isPattern()) scale.setSelectedItem(ScaleMode.TILE);
                refresh();
            }
        });
        path.addActionListener(e -> refresh());
        scale.addActionListener(e -> refresh());
        workspace.addActionListener(e -> {
            WorkspaceChoice choice = (WorkspaceChoice) workspace.getSelectedItem();
            if (choice != null) {
                loadBackdrop(WallpaperManager.backdropOf(choice.index()));
                refresh();
            }
        });

        JButton browse = new JButton(SettingsTexts.get("backdrop.browse"));
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter(SettingsTexts.get("backdrop.filter"),
                    "png", "jpg", "jpeg", "pm", "xpm", "bm", "xbm"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                path.setText(chooser.getSelectedFile().getAbsolutePath());
                refresh();
            }
        });
        colorChip.setPreferredSize(new Dimension(40, 20));
        colorChip.setBackground(color);
        colorChip.setBorder(AmadeUI.sunken());
        JButton pickColor = new JButton(SettingsTexts.get("backdrop.pick.color"));
        pickColor.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, SettingsTexts.get("backdrop.color.title"), color);
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
        controls.add(new JLabel(SettingsTexts.get("backdrop.image")), c);
        c.gridx = 1;
        c.weightx = 1;
        controls.add(path, c);
        c.gridx = 2;
        c.weightx = 0;
        controls.add(browse, c);
        c.gridy = 2;
        c.gridx = 0;
        controls.add(new JLabel(SettingsTexts.get("backdrop.scaling")), c);
        c.gridx = 1;
        controls.add(scale, c);
        c.gridy = 3;
        c.gridx = 0;
        controls.add(new JLabel(SettingsTexts.get("backdrop.color")), c);
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
        preview.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("backdrop.preview")));

        JPanel workspaceRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        workspaceRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JLabel workspaceLabel = new JLabel(SettingsTexts.get("backdrop.workspace"));
        workspaceLabel.setLabelFor(workspace);
        workspaceRow.add(workspaceLabel);
        workspaceRow.add(workspace);

        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.add(workspaceRow, BorderLayout.NORTH);
        content.add(controls, BorderLayout.CENTER);
        content.add(preview, BorderLayout.EAST);
        layoutPage(content);
    }

    /** An entry of the workspace list: a workspace (0 to 3) under its name, or -1 for all of them. */
    record WorkspaceChoice(int index, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private boolean loading;

    /**
     * Lists "All workspaces" and the four workspaces under the names the Front Panel published, and selects
     * the current one, as CDE's Style Manager changes the backdrop of the workspace you are in.
     *
     * @return the selected workspace, -1 without a desktop to ask
     */
    private int fillWorkspaces() {
        List<String> published = new ArrayList<>();
        int current = -1;
        try (X11EwmhHelper helper = X11EwmhHelper.open(null)) {
            published = helper.getDesktopNames();
            current = helper.getCurrentDesktop();
        } catch (RuntimeException | LinkageError e) {
            // No X display (tests, a terminal): offer all workspaces under their stored names.
        }
        workspace.addItem(new WorkspaceChoice(-1, SettingsTexts.get("backdrop.workspace.all")));
        WorkspaceNames stored = WorkspaceNames.user();
        for (int i = 0; i < WorkspaceNames.COUNT; i++) {
            String name = i < published.size() && !published.get(i).isBlank() ? published.get(i)
                    : stored.name(i, SettingsTexts.format("backdrop.workspace.number", i + 1));
            workspace.addItem(new WorkspaceChoice(i, name));
        }
        if (current < 0 || current >= WorkspaceNames.COUNT) current = -1;
        workspace.setSelectedIndex(current + 1);
        return current;
    }

    /** Fills the list, the image field, the scaling and the colour from {@code backdrop}. */
    private void loadBackdrop(Backdrop backdrop) {
        loading = true;
        try {
            wallpapers.clearSelection();
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).path().equals(backdrop.path())) {
                    wallpapers.setSelectedIndex(i);
                    wallpapers.ensureIndexIsVisible(i);
                    break;
                }
            }
            path.setText(backdrop.path());
            scale.setSelectedItem(backdrop.mode());
            color = backdrop.color();
            colorChip.setBackground(color);
        } finally {
            loading = false;
        }
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
        WorkspaceChoice choice = (WorkspaceChoice) workspace.getSelectedItem();
        int index = choice != null ? choice.index() : -1;
        WallpaperManager.applyWallpaper(index, image, (ScaleMode) scale.getSelectedItem(), color);
        boolean plain = image.isEmpty() || WallpaperManager.NO_BACKDROP.equalsIgnoreCase(image);
        String shown = plain ? SettingsTexts.get("backdrop.solid") : image;
        return index < 0 ? SettingsTexts.format("backdrop.status", shown)
                : SettingsTexts.format("backdrop.status.workspace", shown, choice.name());
    }
}
