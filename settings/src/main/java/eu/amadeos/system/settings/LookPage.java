package eu.amadeos.system.settings;

import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.theme.HorizonThemes;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.theme.WallpaperManager;
import eu.amadeos.system.base.x11.X11EwmhHelper;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Desktop look: Classic (CDE Front Panel, Motif frames, the palettes of the Color page) or Horizon (top bar,
 * flat frames), and Horizon's colours, dark or light with an accent colour. The window manager, the panel and
 * the applications switch right away; the session swaps the Front Panel for the top bar without logging out.
 */
final class LookPage extends SettingsPage {
    private static final Logger LOGGER = Logger.getLogger(LookPage.class.getName());

    private final LookCard classic = new LookCard(DesktopLook.CLASSIC);
    private final LookCard horizon = new LookCard(DesktopLook.HORIZON);
    private final JRadioButton dark = new JRadioButton(SettingsTexts.get("look.scheme.dark"));
    private final JRadioButton light = new JRadioButton(SettingsTexts.get("look.scheme.light"));
    private final Map<HorizonThemes.Accent, AccentButton> accents = new EnumMap<>(HorizonThemes.Accent.class);
    private final JPanel horizonBox = new JPanel();
    private final JCheckBox backdrop = new JCheckBox(SettingsTexts.get("look.backdrop"), true);
    /** Horizon's pictures, one per scheme (package amadeos-settings). */
    static final String HORIZON_BACKDROPS = "/usr/share/backgrounds/amadeos/horizon-";
    private DesktopLook look;
    private HorizonThemes.Accent accent;

    LookPage(Consumer<String> status) {
        super(SettingsCategory.LOOK, status);
        DesktopTheme current = ThemeManager.currentTheme();
        look = current.look();
        String horizonId = look == DesktopLook.HORIZON ? current.id() : storedHorizonId();
        accent = HorizonThemes.accent(horizonId);

        JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0));
        cards.add(classic);
        cards.add(horizon);
        classic.addActionListener(e -> choose(DesktopLook.CLASSIC));
        horizon.addActionListener(e -> choose(DesktopLook.HORIZON));
        JPanel lookBox = new JPanel(new BorderLayout(0, 6));
        lookBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("look.look")));
        lookBox.add(cards, BorderLayout.CENTER);
        lookBox.add(note(SettingsTexts.get("look.note")), BorderLayout.SOUTH);

        ButtonGroup schemes = new ButtonGroup();
        schemes.add(dark);
        schemes.add(light);
        (HorizonThemes.scheme(horizonId) == HorizonThemes.Scheme.LIGHT ? light : dark).setSelected(true);
        dark.addActionListener(e -> repaintCards());
        light.addActionListener(e -> repaintCards());
        JPanel accentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (HorizonThemes.Accent a : HorizonThemes.Accent.values()) {
            AccentButton b = new AccentButton(a);
            b.addActionListener(e -> {
                accent = a;
                accents.values().forEach(x -> x.setSelected(x.accent == accent));
                repaintCards();
            });
            b.setSelected(a == accent);
            accents.put(a, b);
            accentRow.add(b);
        }
        horizonBox.setLayout(new BoxLayout(horizonBox, BoxLayout.Y_AXIS));
        horizonBox.setBorder(BorderFactory.createTitledBorder(SettingsTexts.get("look.horizon.colors")));
        horizonBox.add(line(new JLabel(SettingsTexts.get("look.scheme")), dark, light));
        horizonBox.add(line(new JLabel(SettingsTexts.get("look.accent")), accentRow));
        horizonBox.add(line(backdrop));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        lookBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        horizonBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(lookBox);
        content.add(Box.createVerticalStrut(10));
        content.add(horizonBox);
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(content, BorderLayout.NORTH);
        layoutPage(holder);
        choose(look);
    }

    /** The Horizon colours last chosen (look.conf), shown while the classic look is active. */
    private static String storedHorizonId() {
        java.util.Properties p = eu.amadeos.commons.theme.ThemeStore.readLook(
                eu.amadeos.system.base.xdg.XdgDirectories.amadeosConfigDir());
        return HorizonThemes.id(HorizonThemes.Scheme.of(p.getProperty("scheme")),
                HorizonThemes.Accent.of(p.getProperty("accent")));
    }

    private void choose(DesktopLook chosen) {
        look = chosen;
        classic.setSelected(chosen == DesktopLook.CLASSIC);
        horizon.setSelected(chosen == DesktopLook.HORIZON);
        setEnabledDeep(horizonBox, chosen == DesktopLook.HORIZON);
        repaintCards();
    }

    private static void setEnabledDeep(Component c, boolean enabled) {
        c.setEnabled(enabled);
        if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) setEnabledDeep(child, enabled);
        }
    }

    private void repaintCards() {
        classic.repaint();
        horizon.repaint();
    }

    HorizonThemes.Scheme scheme() {
        return light.isSelected() ? HorizonThemes.Scheme.LIGHT : HorizonThemes.Scheme.DARK;
    }

    /** The theme id the page would apply: Horizon's colours, or the classic palette last chosen. */
    String chosenThemeId() {
        return look == DesktopLook.HORIZON ? HorizonThemes.id(scheme(), accent) : null;
    }

    // --8<-- [start:apply]
    @Override
    protected String apply() {
        DesktopTheme before = ThemeManager.currentTheme();
        if (look == DesktopLook.HORIZON) {
            ThemeManager.applyHorizonColors(scheme(), accent);
        } else {
            ThemeManager.applyLook(DesktopLook.CLASSIC);
        }
        DesktopTheme after = ThemeManager.currentTheme();
        if (after.id().equals(before.id())) return SettingsTexts.get("look.unchanged");
        matchBackdrop(before, after);
        // The window manager and the panel follow the theme on the root window
        try (X11EwmhHelper helper = X11EwmhHelper.open(null)) {
            helper.setDesktopTheme(after.id());
        } catch (Exception e) {
            LOGGER.fine("No X11 theme broadcast: " + e.getMessage());
        }
        return look == DesktopLook.HORIZON
                ? SettingsTexts.format("look.status.horizon", SettingsTexts.get("look.scheme." + scheme().id()),
                        SettingsTexts.get("look.accent." + accent.id()))
                : SettingsTexts.get("look.status.classic");
    }
    // --8<-- [end:apply]

    // --8<-- [start:backdrop]
    /**
     * Horizon brings its picture for the chosen scheme (unless the box is cleared); back to classic, a Horizon
     * picture gives way to amadeOS's own backdrop again. Backdrops the user chose stay.
     */
    private void matchBackdrop(DesktopTheme before, DesktopTheme after) {
        String current = WallpaperManager.defaultBackdrop().path();
        boolean horizonPicture = current != null && current.startsWith(HORIZON_BACKDROPS);
        if (after.look() == DesktopLook.HORIZON) {
            if (!backdrop.isSelected()) return;
            String picture = HORIZON_BACKDROPS + scheme().id() + ".png";
            boolean schemeChanged = before.look() == DesktopLook.CLASSIC || !picture.equals(current);
            if (schemeChanged && new java.io.File(picture).isFile()) {
                WallpaperManager.applyWallpaper(picture, WallpaperManager.ScaleMode.FILL, after.base());
            }
        } else if (horizonPicture) {
            WallpaperManager.applyWallpaper(WallpaperManager.initialWallpaper(), WallpaperManager.ScaleMode.FILL, null);
        }
    }
    // --8<-- [end:backdrop]

    @Override
    protected void cancelled() {
        DesktopTheme current = ThemeManager.currentTheme();
        String id = current.look() == DesktopLook.HORIZON ? current.id() : storedHorizonId();
        accent = HorizonThemes.accent(id);
        accents.values().forEach(x -> x.setSelected(x.accent == accent));
        (HorizonThemes.scheme(id) == HorizonThemes.Scheme.LIGHT ? light : dark).setSelected(true);
        choose(current.look());
    }

    /** A selectable picture of a look: a small desktop drawn in that look's style. */
    private final class LookCard extends JToggleButton {
        private final DesktopLook cardLook;

        LookCard(DesktopLook cardLook) {
            super(SettingsTexts.get("look." + cardLook.id()));
            this.cardLook = cardLook;
            setFocusPainted(false);
            setPreferredSize(new Dimension(210, 150));
            setVerticalTextPosition(BOTTOM);
            setHorizontalTextPosition(CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 24;
            int h = getHeight() - 44;
            g2.translate(12, 10);
            if (cardLook == DesktopLook.CLASSIC) paintClassic(g2, w, h); else paintHorizon(g2, w, h);
            g2.dispose();
            if (isSelected()) {
                Graphics2D g3 = (Graphics2D) g.create();
                g3.setColor(DesktopTheme.isDark(getBackground()) ? Color.WHITE : Color.BLACK);
                g3.setStroke(new BasicStroke(2f));
                g3.drawRect(9, 7, w + 5, h + 5);
                g3.dispose();
            }
        }

        private void paintClassic(Graphics2D g, int w, int h) {
            DesktopTheme t = ThemeManager.currentTheme().look() == DesktopLook.CLASSIC
                    ? ThemeManager.currentTheme() : DesktopTheme.WARM_MOTIF;
            g.setColor(t.workspaceColor(0));
            g.fillRect(0, 0, w, h);
            // a window with a Motif title bar
            g.setColor(t.base());
            g.fillRect(w / 6, h / 8, w / 2, h / 2);
            g.setColor(t.activeTitleBg());
            g.fillRect(w / 6 + 3, h / 8 + 3, w / 2 - 6, 8);
            // the Front Panel
            g.setColor(t.base());
            g.fillRect(w / 8, h - 22, w * 3 / 4, 20);
            g.setColor(t.darkShadow());
            g.drawRect(w / 8, h - 22, w * 3 / 4, 20);
            for (int i = 0; i < 6; i++) {
                g.setColor(t.shadow());
                g.fillRect(w / 8 + 6 + i * (w * 3 / 4 - 12) / 6, h - 18, 10, 12);
            }
        }

        private void paintHorizon(Graphics2D g, int w, int h) {
            DesktopTheme t = HorizonThemes.theme(scheme(), accent);
            g.setColor(t.look() == DesktopLook.HORIZON && scheme() == HorizonThemes.Scheme.DARK
                    ? new Color(0x23, 0x24, 0x3C) : new Color(0x9C, 0xA8, 0xC8));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(t.activeTitleBg().getRed(), t.activeTitleBg().getGreen(), t.activeTitleBg().getBlue(), 90));
            g.fillArc(-w / 2, h / 3, w * 2, h * 2, 60, 60);
            // top bar
            g.setColor(t.panelFrameBg());
            g.fillRect(0, 0, w, 9);
            g.setColor(t.activeTitleBg());
            g.drawOval(3, 2, 5, 5);
            // a window with a flat title bar and the accent line
            g.setColor(t.base());
            g.fillRoundRect(w / 6, h / 5, w / 2, h / 2, 8, 8);
            g.setColor(t.activeTitleBg());
            g.fillRect(w / 6 + 5, h / 5, w / 2 - 10, 2);
            g.setColor(t.textInactive());
            g.fillOval(w / 6 + w / 2 - 12, h / 5 + 4, 4, 4);
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
