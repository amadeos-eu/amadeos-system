package eu.amadeos.system.base.theme.backdrop;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

/**
 * X11 colour specifications as they appear in XPM files: {@code #RGB}, {@code #RRGGBB},
 * {@code #RRRGGGBBB}, {@code #RRRRGGGGBBBB}, {@code grayN}/{@code greyN} and the common rgb.txt names.
 */
public final class X11Colors {
    private static final Map<String, Integer> NAMES = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("white", 0xFFFFFF),
            Map.entry("red", 0xFF0000), Map.entry("green", 0x00FF00), Map.entry("blue", 0x0000FF),
            Map.entry("cyan", 0x00FFFF), Map.entry("magenta", 0xFF00FF), Map.entry("yellow", 0xFFFF00),
            Map.entry("gray", 0xBEBEBE), Map.entry("grey", 0xBEBEBE),
            Map.entry("lightgray", 0xD3D3D3), Map.entry("lightgrey", 0xD3D3D3),
            Map.entry("darkgray", 0xA9A9A9), Map.entry("darkgrey", 0xA9A9A9),
            Map.entry("dimgray", 0x696969), Map.entry("dimgrey", 0x696969),
            Map.entry("orange", 0xFFA500), Map.entry("brown", 0xA52A2A), Map.entry("pink", 0xFFC0CB),
            Map.entry("purple", 0xA020F0), Map.entry("navy", 0x000080), Map.entry("navyblue", 0x000080),
            Map.entry("maroon", 0xB03060), Map.entry("gold", 0xFFD700), Map.entry("tan", 0xD2B48C),
            Map.entry("wheat", 0xF5DEB3), Map.entry("khaki", 0xF0E68C), Map.entry("beige", 0xF5F5DC),
            Map.entry("darkgreen", 0x006400), Map.entry("forestgreen", 0x228B22),
            Map.entry("skyblue", 0x87CEEB), Map.entry("steelblue", 0x4682B4),
            Map.entry("slategray", 0x708090), Map.entry("slategrey", 0x708090),
            Map.entry("lightblue", 0xADD8E6), Map.entry("darkblue", 0x00008B), Map.entry("darkred", 0x8B0000));

    private X11Colors() {
    }

    /** @return the colour, or {@code null} if the specification is unknown */
    public static Color parse(String spec) {
        if (spec == null) return null;
        String s = spec.strip();
        if (s.startsWith("#")) return hex(s.substring(1));
        String name = s.toLowerCase(Locale.ROOT).replace(" ", "");
        Integer rgb = NAMES.get(name);
        if (rgb != null) return new Color(rgb);
        for (String prefix : new String[]{"gray", "grey"}) {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                try {
                    int percent = Integer.parseInt(name.substring(prefix.length()));
                    if (percent >= 0 && percent <= 100) {
                        int v = (int) Math.round(percent * 2.55);
                        return new Color(v, v, v);
                    }
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Color hex(String digits) {
        if (digits.isEmpty() || digits.length() % 3 != 0 || digits.length() > 12) return null;
        int per = digits.length() / 3;
        try {
            int[] c = new int[3];
            for (int i = 0; i < 3; i++) {
                int v = Integer.parseInt(digits.substring(i * per, i * per + per), 16);
                // Scale to 8 bits like XParseColor: "#F" means 0xFF, "#FFFF" means 0xFF as well.
                int max = (1 << (4 * per)) - 1;
                c[i] = Math.round(v * 255f / max);
            }
            return new Color(c[0], c[1], c[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
