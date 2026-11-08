package eu.amadeos.system.display;

import java.util.Locale;

/** How a monitor is turned, with xrandr's names for it. */
public enum Rotation {
    NORMAL("normal"), LEFT("left"), RIGHT("right"), INVERTED("inverted");

    private final String xrandr;

    Rotation(String xrandr) {
        this.xrandr = xrandr;
    }

    public String xrandr() {
        return xrandr;
    }

    /** True if the monitor stands upright, so its width and height swap on the screen. */
    public boolean portrait() {
        return this == LEFT || this == RIGHT;
    }

    /** The rotation of an xrandr name, NORMAL for anything unknown. */
    public static Rotation of(String name) {
        if (name != null) {
            for (Rotation r : values()) {
                if (r.xrandr.equals(name.strip().toLowerCase(Locale.ROOT))) return r;
            }
        }
        return NORMAL;
    }
}
