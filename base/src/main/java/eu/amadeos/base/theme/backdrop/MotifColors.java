package eu.amadeos.base.theme.backdrop;

import java.awt.Color;
import java.util.Locale;

/**
 * The five colours Motif derives from one background colour (XmGetColors): foreground, top shadow,
 * bottom shadow and select colour. CDE colours its symbolic backdrops with exactly these, so a backdrop
 * like Concave or Pebbles takes on the hue the user picks.
 *
 * <p>The factors are Motif's defaults for dark, medium and light backgrounds.
 */
public record MotifColors(Color background, Color foreground, Color topShadow, Color bottomShadow, Color select) {
    private static final double MAX = 65535.0;
    private static final double DARK_THRESHOLD = 0.20 * MAX;
    private static final double LIGHT_THRESHOLD = 0.93 * MAX;
    private static final double FOREGROUND_THRESHOLD = 0.70 * MAX;

    public static MotifColors of(Color background) {
        double[] bg = {scale(background.getRed()), scale(background.getGreen()), scale(background.getBlue())};
        double brightness = brightness(bg);
        Color fg = brightness > FOREGROUND_THRESHOLD ? Color.BLACK : Color.WHITE;
        double[] sel = new double[3];
        double[] ts = new double[3];
        double[] bs = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = bg[i];
            if (brightness < DARK_THRESHOLD) {
                sel[i] = v + 15 * (MAX - v) / 100;
                bs[i] = v - v * 45 / 100;
                ts[i] = v + 40 * (MAX - v) / 100;
            } else if (brightness > LIGHT_THRESHOLD) {
                sel[i] = v - v * 15 / 100;
                bs[i] = v - v * 40 / 100;
                ts[i] = v - v * 20 / 100;
            } else {
                double f = brightness / MAX;
                sel[i] = v - v * 15 / 100;
                bs[i] = v - v * (30 + f * (45 - 30)) / 100;
                ts[i] = v + (50 + f * (70 - 50)) * (MAX - v) / 100;
            }
        }
        return new MotifColors(background, fg, color(ts), color(bs), color(sel));
    }

    /**
     * The colour for a symbolic XPM name, or {@code null} for names that keep their own colour
     * (CDE's {@code iconColorN}/{@code iconGrayN} and anything unknown).
     */
    public Color symbol(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "background" -> background;
            case "foreground" -> foreground;
            case "topshadowcolor" -> topShadow;
            case "bottomshadowcolor" -> bottomShadow;
            case "selectcolor" -> select;
            default -> null;
        };
    }

    private static double brightness(double[] c) {
        double intensity = (c[0] + c[1] + c[2]) / 3;
        double luminosity = 0.30 * c[0] + 0.59 * c[1] + 0.11 * c[2];
        return (intensity * 75 + luminosity * 25) / 100;
    }

    private static double scale(int v) {
        return v * 257.0;
    }

    private static Color color(double[] c) {
        return new Color(channel(c[0]), channel(c[1]), channel(c[2]));
    }

    private static int channel(double v) {
        return (int) Math.round(Math.clamp(v, 0, MAX) / 257.0);
    }
}
