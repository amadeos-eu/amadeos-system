package eu.amadeos.system.panel.horizon;

import eu.amadeos.commons.theme.DesktopTheme;

import java.awt.Color;

/** The colours of the top bar, taken from the Horizon theme: the bar colour, its text and the hover shade. */
record BarColors(Color bar, Color text, Color dim, Color hover, Color pressed, Color accent) {

    static BarColors of(DesktopTheme t) {
        Color bar = t.panelFrameBg();
        Color text = DesktopTheme.isDark(bar) ? new Color(0xEC, 0xED, 0xF4) : new Color(0x1E, 0x1F, 0x29);
        return new BarColors(bar, text, mix(bar, text, 0.55), mix(bar, text, 0.13), mix(bar, text, 0.22),
                t.activeTitleBg());
    }

    static Color mix(Color a, Color b, double weightOfB) {
        return new Color(
                (int) Math.round(a.getRed() * (1 - weightOfB) + b.getRed() * weightOfB),
                (int) Math.round(a.getGreen() * (1 - weightOfB) + b.getGreen() * weightOfB),
                (int) Math.round(a.getBlue() * (1 - weightOfB) + b.getBlue() * weightOfB));
    }
}
