package eu.amadeos.system.screen.modules;

import java.awt.Color;
import java.util.List;

/**
 * The colour sets of the {@link LavaLampModule}: the wax and the liquid it floats in. The id is stored in
 * {@code screen.conf} as {@code saver.lava.colors}.
 *
 * @param id          stable id
 * @param name        English name; the Settings show a translation
 * @param liquidTop   liquid at the top of the screen, far from the lamp
 * @param liquidLow   liquid at the bottom, lit by the lamp
 * @param waxEdge     wax at its edge and on the shadowed side
 * @param waxCore     wax inside thick blobs and on the lit side
 */
public record LavaColors(String id, String name, Color liquidTop, Color liquidLow, Color waxEdge, Color waxCore) {

    /** The id of the set whose colours change slowly through all hues. */
    public static final String CHANGING = "changing";
    public static final String DEFAULT = "classic";

    public static final List<LavaColors> ALL = List.of(
            new LavaColors(DEFAULT, "Classic", rgb(0x12061E), rgb(0x3A0C2E), rgb(0xC0281A), rgb(0xFFA23A)),
            new LavaColors("ocean", "Ocean", rgb(0x020A1C), rgb(0x06264A), rgb(0x0E7E9C), rgb(0x7CF2E6)),
            new LavaColors("lime", "Lime", rgb(0x03140F), rgb(0x0B3328), rgb(0x5C9E12), rgb(0xE6F55A)),
            new LavaColors("rose", "Rose", rgb(0x0E0618), rgb(0x2C0C3A), rgb(0xB02470), rgb(0xFF9AD0)),
            new LavaColors("amber", "Amber", rgb(0x120A04), rgb(0x3A1C08), rgb(0xB8620A), rgb(0xFFDA6A)),
            changing(0));

    /** The set with this id; the classic one for unknown ids. */
    public static LavaColors byId(String id) {
        return ALL.stream().filter(c -> c.id().equals(id)).findFirst().orElse(ALL.getFirst());
    }

    public boolean changes() {
        return CHANGING.equals(id);
    }

    /**
     * The changing set at a hue (0 to 1): the classic set turned round the colour wheel, so wax and liquid
     * keep their contrast at every hue.
     */
    static LavaColors changing(double hue) {
        float h = (float) (hue - Math.floor(hue));
        return new LavaColors(CHANGING, "Changing", hsb(h + 0.78f, 0.70f, 0.12f), hsb(h + 0.88f, 0.75f, 0.24f),
                hsb(h + 0.01f, 0.88f, 0.76f), hsb(h + 0.08f, 0.76f, 1f));
    }

    @Override
    public String toString() {
        return name;
    }

    private static Color rgb(int rgb) {
        return new Color(rgb);
    }

    private static Color hsb(float h, float s, float b) {
        return Color.getHSBColor(h - (float) Math.floor(h), s, b);
    }
}
