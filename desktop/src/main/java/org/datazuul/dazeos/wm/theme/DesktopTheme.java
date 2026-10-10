package org.datazuul.dazeos.wm.theme;

import java.awt.Color;
import java.util.Objects;

public final class DesktopTheme {
    private final String id;
    private final String name;
    private final Color base;
    private final Color highlight;
    private final Color softHighlight;
    private final Color shadow;
    private final Color darkShadow;
    private final Color sunkenBg;
    private final Color activeTitleBg;
    private final Color inactiveTitleBg;
    private final Color textActive;
    private final Color textInactive;
    private final String iconTheme;

    public DesktopTheme(String id, String name, Color base, Color highlight, Color softHighlight,
                        Color shadow, Color darkShadow, Color sunkenBg, Color activeTitleBg,
                        Color inactiveTitleBg, Color textActive, Color textInactive, String iconTheme) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.base = Objects.requireNonNull(base, "base");
        this.highlight = highlight != null ? highlight : deriveHighlight(base);
        this.softHighlight = softHighlight != null ? softHighlight : deriveSoftHighlight(base);
        this.shadow = shadow != null ? shadow : deriveShadow(base);
        this.darkShadow = darkShadow != null ? darkShadow : deriveDarkShadow(base);
        this.sunkenBg = sunkenBg != null ? sunkenBg : deriveSunken(base);
        this.activeTitleBg = activeTitleBg != null ? activeTitleBg : base;
        this.inactiveTitleBg = inactiveTitleBg != null ? inactiveTitleBg : deriveInactive(base);
        this.textActive = textActive != null ? textActive : (isDark(base) ? Color.WHITE : Color.BLACK);
        this.textInactive = textInactive != null ? textInactive : (isDark(base) ? new Color(0xAA, 0xAA, 0xAA) : new Color(0x70, 0x70, 0x70));
        this.iconTheme = iconTheme != null ? iconTheme : "classic";
    }

    public static DesktopTheme fromBase(String id, String name, Color base) {
        return new DesktopTheme(id, name, base, null, null, null, null, null, null, null, null, null, "classic");
    }

    // Built-in theme definitions
    public static final DesktopTheme WARM_MOTIF = new DesktopTheme(
            "warm-motif", "Warm Motif (Default)",
            new Color(0xCE, 0xC2, 0xBB),
            Color.WHITE,
            new Color(0xEA, 0xE5, 0xE2),
            new Color(0x8C, 0x82, 0x7A),
            new Color(0x42, 0x3B, 0x35),
            new Color(0xBA, 0xAF, 0xA8),
            new Color(0xCE, 0xC2, 0xBB),
            new Color(0xBF, 0xB6, 0xAF),
            Color.BLACK,
            new Color(0x75, 0x6E, 0x68),
            "classic"
    );

    public static final DesktopTheme CDE_BROCADE = new DesktopTheme(
            "cde-brocade", "CDE Brocade (Teal / Blue)",
            new Color(0x66, 0x80, 0x99),
            new Color(0xA8, 0xBD, 0xD1),
            new Color(0x85, 0x9E, 0xB8),
            new Color(0x46, 0x5A, 0x6E),
            new Color(0x25, 0x34, 0x42),
            new Color(0x54, 0x6C, 0x82),
            new Color(0x7A, 0x96, 0xB0),
            new Color(0x5C, 0x73, 0x8A),
            Color.WHITE,
            new Color(0xCA, 0xD6, 0xE2),
            "classic"
    );

    public static final DesktopTheme CDE_DESERT = new DesktopTheme(
            "cde-desert", "CDE Desert (Sand / Amber)",
            new Color(0xC4, 0xA6, 0x7A),
            new Color(0xF5, 0xE0, 0xC2),
            new Color(0xDB, 0xBE, 0x93),
            new Color(0x8A, 0x70, 0x4D),
            new Color(0x47, 0x36, 0x21),
            new Color(0xB0, 0x92, 0x68),
            new Color(0xC4, 0xA6, 0x7A),
            new Color(0xA8, 0x8D, 0x65),
            Color.BLACK,
            new Color(0x59, 0x44, 0x2C),
            "classic"
    );

    public static final DesktopTheme CLASSIC_GRAY = new DesktopTheme(
            "classic-gray", "Classic Gray",
            new Color(0xC0, 0xC0, 0xC0),
            Color.WHITE,
            new Color(0xE0, 0xE0, 0xE0),
            new Color(0x80, 0x80, 0x80),
            new Color(0x40, 0x40, 0x40),
            new Color(0xA8, 0xA8, 0xA8),
            new Color(0xC0, 0xC0, 0xC0),
            new Color(0xAF, 0xAF, 0xAF),
            Color.BLACK,
            new Color(0x60, 0x60, 0x60),
            "classic"
    );

    public static final DesktopTheme DARK_SLATE = new DesktopTheme(
            "dark-slate", "Dark Slate (Dark Mode)",
            new Color(0x38, 0x3E, 0x4A),
            new Color(0x65, 0x6F, 0x82),
            new Color(0x4B, 0x53, 0x63),
            new Color(0x24, 0x28, 0x30),
            new Color(0x13, 0x15, 0x1A),
            new Color(0x2B, 0x30, 0x3A),
            new Color(0x47, 0x4E, 0x5D),
            new Color(0x30, 0x35, 0x3F),
            new Color(0xE6, 0xE9, 0xF0),
            new Color(0x8E, 0x95, 0xA5),
            "classic"
    );

    public String id() { return id; }
    public String name() { return name; }
    public Color base() { return base; }
    public Color highlight() { return highlight; }
    public Color softHighlight() { return softHighlight; }
    public Color shadow() { return shadow; }
    public Color darkShadow() { return darkShadow; }
    public Color sunkenBg() { return sunkenBg; }
    public Color activeTitleBg() { return activeTitleBg; }
    public Color inactiveTitleBg() { return inactiveTitleBg; }
    public Color textActive() { return textActive; }
    public Color textInactive() { return textInactive; }
    public String iconTheme() { return iconTheme; }

    public static boolean isDark(Color c) {
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
        return luminance < 0.5;
    }

    private static Color deriveHighlight(Color c) {
        return Color.WHITE;
    }

    private static Color deriveSoftHighlight(Color c) {
        return new Color(
                Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * 0.45)),
                Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * 0.45)),
                Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * 0.45))
        );
    }

    private static Color deriveShadow(Color c) {
        return new Color(
                (int) (c.getRed() * 0.68),
                (int) (c.getGreen() * 0.68),
                (int) (c.getBlue() * 0.68)
        );
    }

    private static Color deriveDarkShadow(Color c) {
        return new Color(
                (int) (c.getRed() * 0.35),
                (int) (c.getGreen() * 0.35),
                (int) (c.getBlue() * 0.35)
        );
    }

    private static Color deriveSunken(Color c) {
        return new Color(
                (int) (c.getRed() * 0.88),
                (int) (c.getGreen() * 0.88),
                (int) (c.getBlue() * 0.88)
        );
    }

    private static Color deriveInactive(Color c) {
        return new Color(
                (int) (c.getRed() * 0.92),
                (int) (c.getGreen() * 0.92),
                (int) (c.getBlue() * 0.92)
        );
    }
}

