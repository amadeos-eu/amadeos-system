package eu.amadeos.system.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The controls of the Settings: CDE's dtstyle controls in their order, grouped for the sidebar, with amadeOS's
 * own controls added: Workspaces and Front Panel after Backdrop, Display before Screen, Clipboard after Window,
 * and the System group (Network, Passwords, Updates, Language) at the end.
 */
public enum SettingsCategory {
    COLOR(Group.APPEARANCE, true),
    FONT(Group.APPEARANCE, true),
    BACKDROP(Group.APPEARANCE, true),
    WORKSPACES(Group.APPEARANCE, true),
    PANEL(Group.APPEARANCE, true),
    KEYBOARD(Group.INPUT, true),
    MOUSE(Group.INPUT, true),
    BEEP(Group.INPUT, true),
    DISPLAY(Group.DESKTOP, true),
    SCREEN(Group.DESKTOP, true),
    WINDOW(Group.DESKTOP, true),
    CLIPBOARD(Group.DESKTOP, true),
    STARTUP(Group.DESKTOP, true),
    NETWORK(Group.SYSTEM, true),
    PASSWORDS(Group.SYSTEM, true),
    UPDATES(Group.SYSTEM, true),
    LANGUAGE(Group.SYSTEM, true);

    /** The headings of the sidebar. */
    public enum Group {
        APPEARANCE, INPUT, DESKTOP, SYSTEM;

        public String label() {
            return SettingsTexts.get("group." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final Group group;
    private final boolean available;

    SettingsCategory(Group group, boolean available) {
        this.group = group;
        this.available = available;
    }

    public Group group() {
        return group;
    }

    /** The name in the sidebar and on the button, from {@code category.<name>} in the texts. */
    public String label() {
        return SettingsTexts.get(key());
    }

    /** The line under the page title and the tooltip, from {@code category.<name>.description}. */
    public String description() {
        return SettingsTexts.get(key() + ".description");
    }

    /** More words the search finds this control by, from {@code category.<name>.keywords}, comma-separated. */
    public List<String> keywords() {
        return Arrays.stream(SettingsTexts.get(key() + ".keywords").split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** True if the search text is part of the name, the description or one of the keywords, in any case. */
    public boolean matches(String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        if (label().toLowerCase(Locale.ROOT).contains(q) || description().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        return keywords().stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains(q));
    }

    private String key() {
        return "category." + name().toLowerCase(Locale.ROOT);
    }

    /** False for controls that exist in CDE but are not implemented in amadeOS yet. */
    public boolean available() {
        return available;
    }
}
