package eu.amadeos.system.style;

/**
 * The controls of the Style Manager, in the order of CDE's dtstyle.
 */
public enum StyleCategory {
    COLOR(true),
    FONT(false),
    BACKDROP(true),
    KEYBOARD(true),
    MOUSE(true),
    BEEP(true),
    SCREEN(true),
    WINDOW(false),
    STARTUP(false);

    private final boolean available;

    StyleCategory(boolean available) {
        this.available = available;
    }

    /** The name on the button, from {@code category.<name>} in the texts. */
    public String label() {
        return StyleTexts.get(key());
    }

    /** The tooltip, from {@code category.<name>.description}. */
    public String description() {
        return StyleTexts.get(key() + ".description");
    }

    private String key() {
        return "category." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** False for controls that exist in CDE but are not implemented in amadeOS yet. */
    public boolean available() {
        return available;
    }
}
