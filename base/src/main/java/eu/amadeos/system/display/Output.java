package eu.amadeos.system.display;

import java.util.List;
import java.util.Optional;

/**
 * A connector of the graphics card as {@code xrandr --query} reports it, with the monitor on it.
 *
 * @param name      the connector, e.g. {@code HDMI-1} or {@code eDP-1}
 * @param enabled   true if the monitor shows a part of the desktop
 * @param width     the width of its current mode, before rotation; 0 if it is off
 * @param height    the height of its current mode, before rotation; 0 if it is off
 * @param rate      the current refresh rate in Hz, 0 if unknown
 * @param widthMm   the physical width xrandr reports, 0 if unknown (projectors, virtual screens)
 * @param heightMm  the physical height, 0 if unknown
 * @param modes     the resolutions the monitor offers, largest first as xrandr lists them
 */
public record Output(String name, boolean connected, boolean primary, boolean enabled, int x, int y, int width,
                     int height, double rate, Rotation rotation, int widthMm, int heightMm, List<DisplayMode> modes) {
    public Output {
        modes = List.copyOf(modes);
    }

    /** The resolution the monitor asks for, else the largest it offers. */
    public Optional<DisplayMode> preferredMode() {
        return modes.stream().filter(DisplayMode::preferred).findFirst().or(() -> modes.stream().findFirst());
    }

    public Optional<DisplayMode> mode(int w, int h) {
        return modes.stream().filter(m -> m.width() == w && m.height() == h).findFirst();
    }

    /** The diagonal in inches, rounded to a half, or 0 if xrandr does not know the size. */
    public double diagonalInches() {
        if (widthMm <= 0 || heightMm <= 0) return 0;
        double inches = Math.hypot(widthMm, heightMm) / 25.4;
        return Math.round(inches * 2) / 2.0;
    }

    /** True for the built-in screen of a laptop. */
    public boolean builtIn() {
        return name.startsWith("eDP") || name.startsWith("LVDS") || name.startsWith("DSI");
    }

    /** The setting that keeps the monitor as it is. */
    public OutputSetting current() {
        return new OutputSetting(name, enabled, width, height, rate, x, y, rotation, primary);
    }
}
