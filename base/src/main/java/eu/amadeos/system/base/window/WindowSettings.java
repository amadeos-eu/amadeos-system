package eu.amadeos.system.base.window;

/**
 * How the window manager treats windows, CDE's Window control of the Settings: which window gets the keyboard
 * and whether it comes to the front, and whether a window shows its contents while it is moved.
 *
 * @param focus                  how a window is made active
 * @param raiseWhenActive        whether the window that is made active comes to the front; a click on its
 *                               frame always raises it
 * @param showContentsDuringMove move the window itself instead of an outline
 */
public record WindowSettings(Focus focus, boolean raiseWhenActive, boolean showContentsDuringMove) {
    /** CDE's defaults, which amadeOS had before the setting existed: click to focus, raise, move an outline. */
    public static final WindowSettings DEFAULTS = new WindowSettings(Focus.CLICK, true, false);

    public enum Focus {
        /** A click in a window makes it active. */
        CLICK,
        /** The window under the pointer is active. */
        POINTER
    }

    public WindowSettings {
        if (focus == null) focus = DEFAULTS.focus();
    }

    public WindowSettings withFocus(Focus newFocus) {
        return new WindowSettings(newFocus, raiseWhenActive, showContentsDuringMove);
    }

    public WindowSettings withRaiseWhenActive(boolean raise) {
        return new WindowSettings(focus, raise, showContentsDuringMove);
    }

    public WindowSettings withShowContentsDuringMove(boolean show) {
        return new WindowSettings(focus, raiseWhenActive, show);
    }
}
