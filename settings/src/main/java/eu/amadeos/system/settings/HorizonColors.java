package eu.amadeos.system.settings;

import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.theme.HorizonThemes;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.theme.WallpaperManager;
import eu.amadeos.system.base.x11.X11EwmhHelper;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Horizon's colours, dark or light in one of the scheme's palettes with an accent colour, and whether Horizon's picture follows the scheme. The
 * Desktop look page shows them under its look cards, the Color page instead of the classic palettes while the
 * desktop has the Horizon look.
 */
final class HorizonColors {
    private static final Logger LOGGER = Logger.getLogger(HorizonColors.class.getName());
    /** Horizon's pictures, one per scheme (package amadeos-settings). */
    static final String HORIZON_BACKDROPS = "/usr/share/backgrounds/amadeos/horizon-";

    private final JRadioButton dark = new JRadioButton(SettingsTexts.get("look.scheme.dark"));
    private final JRadioButton light = new JRadioButton(SettingsTexts.get("look.scheme.light"));
    private final Map<HorizonThemes.Accent, AccentButton> accents = new EnumMap<>(HorizonThemes.Accent.class);
    private final Map<HorizonThemes.Palette, PaletteButton> palettes = new EnumMap<>(HorizonThemes.Palette.class);
    private final JPanel paletteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private final JCheckBox backdrop = new JCheckBox(SettingsTexts.get("look.backdrop"), true);
    private final JPanel box = new JPanel();
    private HorizonThemes.Accent accent;
    private HorizonThemes.Palette palette = HorizonThemes.Palette.standard(HorizonThemes.DEFAULT_SCHEME);
    private final Runnable changed;

    /** @param changed called when the scheme or the accent changes, for previews */
    HorizonColors(Runnable changed) {
        this.changed = changed;
        ButtonGroup schemes = new ButtonGroup();
        schemes.add(dark);
        schemes.add(light);
        dark.addActionListener(e -> schemeChosen());
        light.addActionListener(e -> schemeChosen());
        for (HorizonThemes.Palette p : HorizonThemes.Palette.values()) {
            PaletteButton b = new PaletteButton(p);
            b.addActionListener(e -> {
                select(p);
                changed.run();
            });
            palettes.put(p, b);
        }
        JPanel accentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (HorizonThemes.Accent a : HorizonThemes.Accent.values()) {
            AccentButton b = new AccentButton(a);
            b.addActionListener(e -> {
                select(a);
                changed.run();
            });
            accents.put(a, b);
            accentRow.add(b);
        }
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("look.horizon.colors")));
        box.add(SettingsPage.line(new JLabel(SettingsTexts.get("look.scheme")), dark, light));
        box.add(SettingsPage.line(new JLabel(SettingsTexts.get("look.palette")), paletteRow));
        box.add(SettingsPage.line(new JLabel(SettingsTexts.get("look.accent")), accentRow));
        box.add(SettingsPage.line(backdrop));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset();
    }

    JPanel component() {
        return box;
    }

    /** Shows the colours of the desktop, or the Horizon colours last chosen while it has the classic look. */
    void reset() {
        DesktopTheme current = ThemeManager.currentTheme();
        String id = current.look() == DesktopLook.HORIZON ? current.id() : storedHorizonId();
        select(HorizonThemes.accent(id));
        (HorizonThemes.scheme(id) == HorizonThemes.Scheme.LIGHT ? light : dark).setSelected(true);
        showPalettes();
        select(HorizonThemes.palette(id));
    }

    /** Another scheme: its palettes, starting with its default one. */
    private void schemeChosen() {
        if (palette.scheme() != scheme()) {
            showPalettes();
            select(HorizonThemes.Palette.standard(scheme()));
        }
        changed.run();
    }

    /** The palettes of the chosen scheme. */
    private void showPalettes() {
        paletteRow.removeAll();
        for (HorizonThemes.Palette p : HorizonThemes.Palette.of(scheme())) paletteRow.add(palettes.get(p));
        paletteRow.revalidate();
        paletteRow.repaint();
    }

    private void select(HorizonThemes.Palette chosen) {
        palette = chosen.scheme() == scheme() ? chosen : HorizonThemes.Palette.standard(scheme());
        palettes.values().forEach(x -> x.setSelected(x.palette == palette));
    }

    private void select(HorizonThemes.Accent chosen) {
        accent = chosen;
        accents.values().forEach(x -> x.setSelected(x.accent == accent));
        paletteRow.repaint();
    }

    HorizonThemes.Scheme scheme() {
        return light.isSelected() ? HorizonThemes.Scheme.LIGHT : HorizonThemes.Scheme.DARK;
    }

    HorizonThemes.Accent accent() {
        return accent;
    }

    HorizonThemes.Palette palette() {
        return palette;
    }

    /** The theme id of the chosen scheme, palette and accent. */
    String themeId() {
        return HorizonThemes.id(scheme(), accent, palette);
    }

    /** A status line naming the scheme (with its palette, unless the default one) and the accent. */
    String status() {
        String scheme = SettingsTexts.get("look.scheme." + scheme().id());
        if (!palette.isStandard()) scheme += " (" + SettingsTexts.get("look.palette." + palette.id()) + ")";
        return SettingsTexts.format("look.status.horizon", scheme, SettingsTexts.get("look.accent." + accent.id()));
    }

    /** The Horizon colours last chosen (look.conf). */
    static String storedHorizonId() {
        java.util.Properties p = eu.amadeos.commons.theme.ThemeStore.readLook(
                eu.amadeos.system.base.xdg.XdgDirectories.amadeosConfigDir());
        return HorizonThemes.id(p);
    }

    /**
     * After the theme changed from {@code before} to {@code after}: matches the backdrop and tells the window
     * manager and the panel, which follow the theme on the root window.
     */
    void followTheme(DesktopTheme before, DesktopTheme after) {
        matchBackdrop(before, after);
        try (X11EwmhHelper helper = X11EwmhHelper.open(null)) {
            helper.setDesktopTheme(after.id());
        } catch (Exception e) {
            LOGGER.fine("No X11 theme broadcast: " + e.getMessage());
        }
    }

    // --8<-- [start:backdrop]
    /**
     * Horizon brings its picture for the chosen scheme (unless the box is cleared); back to classic, a Horizon
     * picture gives way to amadeOS's own backdrop again. Backdrops the user chose stay, the extra Horizon
     * wallpapers (horizon-dunes.jpg and so on) too: only the two scheme pictures follow the scheme.
     */
    private void matchBackdrop(DesktopTheme before, DesktopTheme after) {
        String current = WallpaperManager.defaultBackdrop().path();
        boolean horizonPicture = isSchemePicture(current);
        if (after.look() == DesktopLook.HORIZON) {
            if (!backdrop.isSelected()) return;
            String picture = HORIZON_BACKDROPS + scheme().id() + ".png";
            boolean schemeChanged = before.look() == DesktopLook.CLASSIC || horizonPicture && !picture.equals(current);
            if (schemeChanged && new java.io.File(picture).isFile()) {
                WallpaperManager.applyWallpaper(picture, WallpaperManager.ScaleMode.FILL, after.base());
            }
        } else if (horizonPicture) {
            WallpaperManager.applyWallpaper(WallpaperManager.initialWallpaper(), WallpaperManager.ScaleMode.FILL, null);
        }
    }
    // --8<-- [end:backdrop]

    /** Whether the path is one of the scheme pictures horizon-dark.png and horizon-light.png. */
    static boolean isSchemePicture(String path) {
        if (path == null || !path.startsWith(HORIZON_BACKDROPS)) return false;
        String rest = path.substring(HORIZON_BACKDROPS.length());
        return rest.equals("dark.png") || rest.equals("light.png");
    }

    /**
     * A small window in the colours of a palette (the top bar, the window and a field with text), with the
     * palette's name under it.
     */
    private final class PaletteButton extends JToggleButton {
        private static final int PREVIEW_HEIGHT = 44;
        private final HorizonThemes.Palette palette;
        private final String name;

        PaletteButton(HorizonThemes.Palette palette) {
            this.palette = palette;
            this.name = SettingsTexts.get("look.palette." + palette.id());
            setToolTipText(name);
            setPreferredSize(new Dimension(70, PREVIEW_HEIGHT + getFontMetrics(getFont()).getHeight() + 2));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            getAccessibleContext().setAccessibleName(name);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = 62, x = (getWidth() - w) / 2 + 4, y = 4, h = PREVIEW_HEIGHT - 8;
            w -= 8;
            java.awt.Shape clip = new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, 8, 8);
            g2.setColor(palette.base());
            g2.fill(clip);
            g2.setClip(clip);
            g2.setColor(palette.bar());
            g2.fillRect(x, y, w, 7);
            g2.setColor(accent != null ? accent.color() : HorizonThemes.DEFAULT_ACCENT.color());
            g2.fillRect(x + 6, y + 12, w - 12, 2);
            g2.setColor(palette.fields());
            g2.fillRoundRect(x + 6, y + 17, w - 12, h - 22, 4, 4);
            g2.setColor(palette.text());
            g2.fillRect(x + 10, y + 21, w / 2 - 6, 2);
            g2.fillRect(x + 10, y + 26, w / 3, 2);
            g2.setClip(null);
            g2.setStroke(new BasicStroke(isSelected() ? 2f : 1f));
            g2.setColor(isSelected() ? getForeground() : new Color(0x80, 0x80, 0x88, 160));
            g2.draw(isSelected() ? new java.awt.geom.RoundRectangle2D.Float(x - 2, y - 2, w + 4, h + 4, 10, 10) : clip);
            g2.setColor(getForeground());
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.drawString(name, (getWidth() - fm.stringWidth(name)) / 2, PREVIEW_HEIGHT + fm.getAscent());
            g2.dispose();
        }
    }

    /** A round swatch of an accent colour. */
    private static final class AccentButton extends JToggleButton {
        private final HorizonThemes.Accent accent;

        AccentButton(HorizonThemes.Accent accent) {
            this.accent = accent;
            setToolTipText(SettingsTexts.get("look.accent." + accent.id()));
            setPreferredSize(new Dimension(28, 28));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            getAccessibleContext().setAccessibleName(accent.id().toUpperCase(Locale.ROOT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = isEnabled() ? accent.color() : Color.GRAY;
            g2.setColor(c);
            g2.fillOval(5, 5, 18, 18);
            if (isSelected()) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(getForeground());
                g2.drawOval(2, 2, 24, 24);
            }
            g2.dispose();
        }
    }
}
