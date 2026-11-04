package eu.amadeos.system.base.x11;

import java.util.List;
import java.util.Optional;

/**
 * The pointer as the X resources name it: {@code Xcursor.theme} and {@code Xcursor.size} in the root window's
 * {@code RESOURCE_MANAGER} property, which {@code xrdb -merge} rewrites.
 *
 * @param theme the cursor theme, empty when the resources name none
 * @param size the pointer size in pixels, 0 when the resources name none
 */
// --8<-- [start:xcursor-resources]
public record XcursorResources(String theme, int size) {

    /**
     * The cursor names programs ask for: the shapes of X's cursor font, which libXcursor looks up by these
     * names, and the CSS names newer toolkits use. The window manager swaps each of them when the pointer changes.
     */
    public static final List<String> CURSOR_NAMES = List.of(
            "X_cursor", "arrow", "based_arrow_down", "based_arrow_up", "boat", "bogosity", "bottom_left_corner",
            "bottom_right_corner", "bottom_side", "bottom_tee", "box_spiral", "center_ptr", "circle", "clock",
            "coffee_mug", "cross", "cross_reverse", "crosshair", "diamond_cross", "dot", "dotbox", "double_arrow",
            "draft_large", "draft_small", "draped_box", "exchange", "fleur", "gobbler", "gumby", "hand1", "hand2",
            "heart", "icon", "iron_cross", "left_ptr", "left_side", "left_tee", "leftbutton", "ll_angle",
            "lr_angle", "man", "middlebutton", "mouse", "pencil", "pirate", "plus", "question_arrow", "right_ptr",
            "right_side", "right_tee", "rightbutton", "rtl_logo", "sailboat", "sb_down_arrow",
            "sb_h_double_arrow", "sb_left_arrow", "sb_right_arrow", "sb_up_arrow", "sb_v_double_arrow",
            "shuttle", "sizing", "spider", "spraycan", "star", "target", "tcross", "top_left_arrow",
            "top_left_corner", "top_right_corner", "top_side", "top_tee", "trek", "ul_angle", "umbrella",
            "ur_angle", "watch", "xterm",
            "default", "pointer", "text", "wait", "progress", "help", "move", "all-scroll", "grab", "grabbing",
            "not-allowed", "no-drop", "col-resize", "row-resize", "ew-resize", "ns-resize", "n-resize",
            "s-resize", "e-resize", "w-resize", "ne-resize", "nw-resize", "se-resize", "sw-resize",
            "context-menu", "cell", "copy", "alias", "vertical-text", "zoom-in", "zoom-out");

    /** Reads the two lines from the text of {@code RESOURCE_MANAGER}; other resources are left alone. */
    public static XcursorResources parse(String resourceManager) {
        String theme = "";
        int size = 0;
        if (resourceManager != null) {
            for (String line : resourceManager.split("\n")) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (name.equals("Xcursor.theme") || name.equals("*Xcursor.theme")) {
                    theme = value;
                } else if (name.equals("Xcursor.size") || name.equals("*Xcursor.size")) {
                    try {
                        size = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        size = 0;
                    }
                }
            }
        }
        return new XcursorResources(theme, size);
    }

    public Optional<String> themeName() {
        return theme.isEmpty() ? Optional.empty() : Optional.of(theme);
    }
}
// --8<-- [end:xcursor-resources]
