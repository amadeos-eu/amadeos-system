package eu.amadeos.input;

/**
 * Keyboard, mouse and bell settings of the Style Manager. The defaults are the X server's own defaults,
 * so applying them at login changes nothing until the user changes something.
 */
public record InputSettings(Keyboard keyboard, Mouse mouse, Beep beep) {
    // The nested records must not call back into this class while they initialise their DEFAULTS
    // (that would see InputSettings.DEFAULTS half-built), hence Math.clamp instead of a helper here.

    public static final InputSettings DEFAULTS = new InputSettings(Keyboard.DEFAULTS, Mouse.DEFAULTS, Beep.DEFAULTS);

    public InputSettings withKeyboard(Keyboard value) {
        return new InputSettings(value, mouse, beep);
    }

    public InputSettings withMouse(Mouse value) {
        return new InputSettings(keyboard, value, beep);
    }

    public InputSettings withBeep(Beep value) {
        return new InputSettings(keyboard, mouse, value);
    }

    /**
     * @param autoRepeat     keys repeat while held down
     * @param repeatDelayMs  delay before the first repeat
     * @param repeatRate     repeats per second
     * @param clickVolume    key click volume in percent, 0 = off
     */
    public record Keyboard(boolean autoRepeat, int repeatDelayMs, int repeatRate, int clickVolume) {
        public static final Keyboard DEFAULTS = new Keyboard(true, 660, 25, 0);

        public Keyboard {
            repeatDelayMs = Math.clamp(repeatDelayMs, 100, 2000);
            repeatRate = Math.clamp(repeatRate, 1, 100);
            clickVolume = Math.clamp(clickVolume, 0, 100);
        }
    }

    /**
     * @param leftHanded     swap the first and third button
     * @param doubleClickMs  maximum time between the clicks of a double click
     * @param acceleration   pointer acceleration factor (1 = none)
     * @param threshold      pixels the pointer moves quickly before the acceleration applies
     */
    public record Mouse(boolean leftHanded, int doubleClickMs, int acceleration, int threshold) {
        public static final Mouse DEFAULTS = new Mouse(false, 400, 2, 4);

        public Mouse {
            doubleClickMs = Math.clamp(doubleClickMs, 100, 1500);
            acceleration = Math.clamp(acceleration, 1, 10);
            threshold = Math.clamp(threshold, 1, 50);
        }
    }

    /**
     * @param volume      bell volume in percent, 0 = silent
     * @param pitchHz     tone of the bell
     * @param durationMs  length of the bell
     */
    public record Beep(int volume, int pitchHz, int durationMs) {
        public static final Beep DEFAULTS = new Beep(50, 400, 100);

        public Beep {
            volume = Math.clamp(volume, 0, 100);
            pitchHz = Math.clamp(pitchHz, 55, 4000);
            durationMs = Math.clamp(durationMs, 10, 2000);
        }
    }
}
