package eu.amadeos.system.display;

/**
 * What one monitor should show: its resolution, refresh rate, place on the desktop and rotation, or nothing.
 *
 * @param rate 0 lets xrandr pick the rate
 * @param x    the left edge on the desktop, in pixels
 * @param y    the top edge on the desktop
 */
public record OutputSetting(String name, boolean enabled, int width, int height, double rate, int x, int y,
                            Rotation rotation, boolean primary) {

    /** The width the monitor takes on the desktop, after rotation. */
    public int screenWidth() {
        return rotation.portrait() ? height : width;
    }

    /** The height the monitor takes on the desktop, after rotation. */
    public int screenHeight() {
        return rotation.portrait() ? width : height;
    }

    public OutputSetting withMode(int w, int h, double r) {
        return new OutputSetting(name, enabled, w, h, r, x, y, rotation, primary);
    }

    public OutputSetting withPosition(int newX, int newY) {
        return new OutputSetting(name, enabled, width, height, rate, newX, newY, rotation, primary);
    }

    public OutputSetting withRotation(Rotation r) {
        return new OutputSetting(name, enabled, width, height, rate, x, y, r, primary);
    }

    public OutputSetting withEnabled(boolean on) {
        return new OutputSetting(name, on, width, height, rate, x, y, rotation, primary);
    }

    public OutputSetting withPrimary(boolean p) {
        return new OutputSetting(name, enabled, width, height, rate, x, y, rotation, p);
    }
}
