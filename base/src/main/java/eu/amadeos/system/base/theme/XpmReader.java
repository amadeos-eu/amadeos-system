package eu.amadeos.system.base.theme;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads XPM 3 pictures, the icon format of many older X11 programs in {@code /usr/share/pixmaps}
 * (xterm, Python, OpenJDK, ...). ImageIO has no reader for it. Colours are {@code #rgb} in any depth,
 * {@code None} for transparent and the common X11 colour names.
 */
final class XpmReader {
    private static final Map<String, Integer> NAMED = new HashMap<>();
    static {
        NAMED.put("black", 0x000000);
        NAMED.put("white", 0xFFFFFF);
        NAMED.put("red", 0xFF0000);
        NAMED.put("green", 0x00FF00);
        NAMED.put("blue", 0x0000FF);
        NAMED.put("yellow", 0xFFFF00);
        NAMED.put("cyan", 0x00FFFF);
        NAMED.put("magenta", 0xFF00FF);
        NAMED.put("gray", 0xBEBEBE);
        NAMED.put("grey", 0xBEBEBE);
        NAMED.put("darkgray", 0xA9A9A9);
        NAMED.put("darkgrey", 0xA9A9A9);
        NAMED.put("lightgray", 0xD3D3D3);
        NAMED.put("lightgrey", 0xD3D3D3);
        NAMED.put("orange", 0xFFA500);
        NAMED.put("brown", 0xA52A2A);
        NAMED.put("navy", 0x000080);
        NAMED.put("darkgreen", 0x006400);
    }

    private XpmReader() {}

    static BufferedImage read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.ISO_8859_1));
    }

    /** Parses the text of an XPM file; null when it is not one. */
    static BufferedImage parse(String text) {
        List<String> strings = quotedStrings(text);
        if (strings.isEmpty()) return null;
        String[] head = strings.get(0).trim().split("\\s+");
        if (head.length < 4) return null;
        int width;
        int height;
        int colors;
        int cpp;
        try {
            width = Integer.parseInt(head[0]);
            height = Integer.parseInt(head[1]);
            colors = Integer.parseInt(head[2]);
            cpp = Integer.parseInt(head[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (width <= 0 || height <= 0 || cpp <= 0 || width > 4096 || height > 4096
                || strings.size() < 1 + colors + height) {
            return null;
        }

        Map<String, Integer> palette = new HashMap<>();
        for (int i = 0; i < colors; i++) {
            String line = strings.get(1 + i);
            if (line.length() < cpp) return null;
            palette.put(line.substring(0, cpp), color(line.substring(cpp)));
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            String row = strings.get(1 + colors + y);
            for (int x = 0; x < width && (x + 1) * cpp <= row.length(); x++) {
                Integer argb = palette.get(row.substring(x * cpp, (x + 1) * cpp));
                img.setRGB(x, y, argb == null ? 0 : argb);
            }
        }
        return img;
    }

    /** The colour of one palette line after its characters: the {@code c} key, else the first one given. */
    static int color(String spec) {
        String[] words = spec.trim().split("\\s+");
        String value = null;
        for (int i = 0; i + 1 < words.length; i += 2) {
            if (value == null || "c".equals(words[i])) {
                value = words[i + 1];
            }
            if ("c".equals(words[i])) break;
        }
        if (value == null) return 0;
        if (value.equalsIgnoreCase("none")) return 0;
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            if (hex.length() % 3 != 0 || hex.isEmpty()) return 0xFF000000;
            int n = hex.length() / 3;
            try {
                int r = Integer.parseInt(hex.substring(0, Math.min(2, n)), 16);
                int g = Integer.parseInt(hex.substring(n, n + Math.min(2, n)), 16);
                int b = Integer.parseInt(hex.substring(2 * n, 2 * n + Math.min(2, n)), 16);
                if (n == 1) {
                    r *= 17;
                    g *= 17;
                    b *= 17;
                }
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            } catch (NumberFormatException e) {
                return 0xFF000000;
            }
        }
        Integer rgb = NAMED.get(value.toLowerCase(Locale.ROOT).replaceAll("\\d+$", ""));
        return 0xFF000000 | (rgb == null ? 0x808080 : rgb);
    }

    /** The C string literals of the file, outside comments. */
    private static List<String> quotedStrings(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? len : end + 2;
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < len && text.charAt(i) != '"') {
                    char ch = text.charAt(i);
                    if (ch == '\\' && i + 1 < len) {
                        i++;
                        ch = text.charAt(i);
                    }
                    sb.append(ch);
                    i++;
                }
                out.add(sb.toString());
                i++;
            } else {
                i++;
            }
        }
        return out;
    }
}
