package eu.amadeos.system.base.theme.backdrop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Reads XPM 3 images (the C-source format used by CDE's {@code .pm} backdrops and icons).
 *
 * <p>Each colour entry may carry several keys: {@code c} (colour), {@code m} (mono), {@code g}/{@code g4}
 * (grey) and {@code s} (a symbolic name). Like libXpm with a colour-symbol override, the symbolic name wins
 * when the caller knows it, so {@code s background} follows the desktop palette; otherwise the {@code c}
 * value is used, then {@code g}, {@code g4}, {@code m}. {@code None} becomes transparent.
 */
public final class XpmDecoder {
    private XpmDecoder() {
    }

    /**
     * @param source  the XPM text
     * @param symbols colours for symbolic names, or {@code null} for names the caller does not map
     */
    public static BufferedImage decode(String source, Function<String, Color> symbols) throws IOException {
        List<String> strings = quotedStrings(source);
        if (strings.isEmpty()) throw new IOException("not an XPM image: no strings");
        String[] header = strings.get(0).trim().split("\\s+");
        if (header.length < 4) throw new IOException("bad XPM header: " + strings.get(0));
        int width, height, colors, cpp;
        try {
            width = Integer.parseInt(header[0]);
            height = Integer.parseInt(header[1]);
            colors = Integer.parseInt(header[2]);
            cpp = Integer.parseInt(header[3]);
        } catch (NumberFormatException e) {
            throw new IOException("bad XPM header: " + strings.get(0), e);
        }
        if (width <= 0 || height <= 0 || colors <= 0 || cpp <= 0 || width > 8192 || height > 8192) {
            throw new IOException("unsupported XPM size: " + strings.get(0));
        }
        if (strings.size() < 1 + colors + height) throw new IOException("truncated XPM image");

        Map<String, Integer> table = new HashMap<>();
        for (int i = 0; i < colors; i++) {
            String line = strings.get(1 + i);
            if (line.length() < cpp) throw new IOException("bad XPM colour line: " + line);
            table.put(line.substring(0, cpp), argb(line.substring(cpp), symbols));
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            String line = strings.get(1 + colors + y);
            if (line.length() < width * cpp) throw new IOException("short XPM row " + y);
            for (int x = 0; x < width; x++) {
                Integer pixel = table.get(line.substring(x * cpp, x * cpp + cpp));
                if (pixel == null) throw new IOException("unknown XPM pixel in row " + y);
                row[x] = pixel;
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    /** Resolves one colour definition such as {@code "s background m black c #949494949494"}. */
    static int argb(String definition, Function<String, Color> symbols) throws IOException {
        Map<String, String> keys = keys(definition);
        String symbol = keys.get("s");
        if (symbol != null && symbols != null) {
            Color mapped = symbols.apply(symbol);
            if (mapped != null) return mapped.getRGB();
        }
        for (String key : new String[]{"c", "g", "g4", "m"}) {
            String value = keys.get(key);
            if (value == null) continue;
            if (value.equalsIgnoreCase("none")) return 0;
            Color color = X11Colors.parse(value);
            if (color != null) return color.getRGB();
        }
        throw new IOException("unresolvable XPM colour: " + definition.trim());
    }

    /** Splits a colour definition into key/value pairs; values may contain spaces ("light grey"). */
    static Map<String, String> keys(String definition) {
        Map<String, String> result = new HashMap<>();
        String key = null;
        StringBuilder value = new StringBuilder();
        for (String word : definition.trim().split("\\s+")) {
            String lower = word.toLowerCase(Locale.ROOT);
            boolean isKey = switch (lower) {
                case "c", "m", "g", "g4", "s" -> true;
                default -> false;
            };
            if (isKey && (key == null || !value.isEmpty())) {
                if (key != null) result.put(key, value.toString());
                key = lower;
                value.setLength(0);
            } else if (key != null) {
                if (!value.isEmpty()) value.append(' ');
                value.append(word);
            }
        }
        if (key != null && !value.isEmpty()) result.put(key, value.toString());
        return result;
    }

    /** The contents of all string literals outside comments, in order. */
    static List<String> quotedStrings(String source) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = source.length();
        while (i < n) {
            char ch = source.charAt(i);
            if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                int end = source.indexOf('\n', i);
                i = end < 0 ? n : end + 1;
            } else if (ch == '"') {
                StringBuilder s = new StringBuilder();
                i++;
                // Like libXpm, no escape processing: a backslash is an ordinary pixel character
                // (SkyDark.pm uses it), so a string simply ends at the next quote.
                while (i < n && source.charAt(i) != '"') {
                    s.append(source.charAt(i));
                    i++;
                }
                out.add(s.toString());
                i++;
            } else {
                i++;
            }
        }
        return out;
    }
}
