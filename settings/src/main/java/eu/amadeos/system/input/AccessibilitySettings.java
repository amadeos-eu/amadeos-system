package eu.amadeos.system.input;

import java.util.Locale;

/**
 * Settings &gt; Accessibility: the mouse pointer, the keyboard aids of XKB's AccessX (sticky, slow and bounce keys,
 * mouse keys) and high contrast. The defaults change nothing: the classic X pointer and no keyboard aid.
 *
 * @param pointer       the look of the mouse pointer
 * @param pointerSize   its size in pixels for the white and black pointers (24, 32 or 48)
 * @param stickyKeys    Shift, Ctrl, Alt and Super stay pressed until the next key
 * @param stickyLock    pressing a modifier twice locks it until it is pressed a third time
 * @param stickyTwoKeys pressing two keys together switches sticky keys off again
 * @param slowKeys      a key counts only after it has been held for {@code slowKeysMs}
 * @param bounceKeys    a key pressed again within {@code bounceKeysMs} is ignored
 * @param mouseKeys     the number pad moves the pointer and clicks
 * @param gestures      Shift pressed five times switches sticky keys on, Shift held for 8 seconds slow keys
 * @param feedback      a beep when a key aid is switched on or off, a key is accepted or rejected
 * @param contrast      high contrast colours; {@code previousTheme} is the palette to go back to
 */
public record AccessibilitySettings(Pointer pointer, int pointerSize, boolean stickyKeys, boolean stickyLock,
                                    boolean stickyTwoKeys, boolean slowKeys, int slowKeysMs, boolean bounceKeys,
                                    int bounceKeysMs, boolean mouseKeys, boolean gestures, boolean feedback,
                                    Contrast contrast, String previousTheme) {

    public static final int[] POINTER_SIZES = {24, 32, 48};

    public static final AccessibilitySettings DEFAULTS = new AccessibilitySettings(Pointer.CLASSIC, 24, false, true,
            false, false, 300, false, 300, false, false, true, Contrast.OFF, "");

    /** The classic X11 pointer, or the white or black pointer of the DMZ cursor theme in three sizes. */
    public enum Pointer {
        CLASSIC(""), WHITE("DMZ-White"), BLACK("DMZ-Black");

        private final String theme;

        Pointer(String theme) {
            this.theme = theme;
        }

        /** The Xcursor theme, empty for the classic pointer. */
        public String theme() {
            return theme;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** No high contrast, white on black, or black on white. */
    public enum Contrast {
        OFF(""), DARK("high-contrast-dark"), LIGHT("high-contrast-light");

        private final String themeId;

        Contrast(String themeId) {
            this.themeId = themeId;
        }

        /** The palette in /etc/amadeos/themes, empty for OFF. */
        public String themeId() {
            return themeId;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public AccessibilitySettings {
        if (pointer == null) pointer = Pointer.CLASSIC;
        pointerSize = nearestSize(pointerSize);
        slowKeysMs = Math.clamp(slowKeysMs, 50, 2000);
        bounceKeysMs = Math.clamp(bounceKeysMs, 50, 2000);
        if (contrast == null) contrast = Contrast.OFF;
        if (previousTheme == null) previousTheme = "";
    }

    private static int nearestSize(int size) {
        int best = POINTER_SIZES[0];
        for (int s : POINTER_SIZES) {
            if (Math.abs(s - size) < Math.abs(best - size)) best = s;
        }
        return best;
    }

    public AccessibilitySettings withPointer(Pointer value, int size) {
        return new AccessibilitySettings(value, size, stickyKeys, stickyLock, stickyTwoKeys, slowKeys, slowKeysMs,
                bounceKeys, bounceKeysMs, mouseKeys, gestures, feedback, contrast, previousTheme);
    }

    public AccessibilitySettings withKeys(boolean sticky, boolean lock, boolean twoKeys, boolean slow, int slowMs,
                                          boolean bounce, int bounceMs, boolean mouse, boolean gestureKeys,
                                          boolean beep) {
        return new AccessibilitySettings(pointer, pointerSize, sticky, lock, twoKeys, slow, slowMs, bounce, bounceMs,
                mouse, gestureKeys, beep, contrast, previousTheme);
    }

    public AccessibilitySettings withContrast(Contrast value, String previous) {
        return new AccessibilitySettings(pointer, pointerSize, stickyKeys, stickyLock, stickyTwoKeys, slowKeys,
                slowKeysMs, bounceKeys, bounceKeysMs, mouseKeys, gestures, feedback, value, previous);
    }
}
