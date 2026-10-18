package eu.amadeos.base.theme.backdrop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads X bitmaps (the C-source format of CDE's {@code .bm} backdrops). Set bits are drawn in the
 * foreground colour, clear bits in the background colour, as dtwm does for bitmap backdrops.
 */
public final class XbmDecoder {
    private static final Pattern WIDTH = Pattern.compile("#define\\s+\\S*?width\\s+(\\d+)");
    private static final Pattern HEIGHT = Pattern.compile("#define\\s+\\S*?height\\s+(\\d+)");
    private static final Pattern BYTE = Pattern.compile("0[xX]([0-9a-fA-F]{1,4})|\\b(\\d+)\\b");

    private XbmDecoder() {
    }

    public static BufferedImage decode(String source, Color foreground, Color background) throws IOException {
        int width = define(WIDTH, source);
        int height = define(HEIGHT, source);
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new IOException("unsupported XBM size " + width + "x" + height);
        }
        int open = source.indexOf('{');
        int close = source.indexOf('}', open + 1);
        if (open < 0 || close < 0) throw new IOException("not an XBM image: no data array");

        int bytesPerRow = (width + 7) / 8;
        byte[] data = new byte[bytesPerRow * height];
        Matcher m = BYTE.matcher(source.substring(open + 1, close));
        int count = 0;
        while (m.find() && count < data.length) {
            int value = m.group(1) != null ? Integer.parseInt(m.group(1), 16) : Integer.parseInt(m.group(2));
            data[count++] = (byte) value;
        }
        if (count < data.length) throw new IOException("truncated XBM image: " + count + " of " + data.length + " bytes");

        int fg = foreground.getRGB();
        int bg = background.getRGB();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int b = data[y * bytesPerRow + x / 8];
                row[x] = ((b >> (x % 8)) & 1) != 0 ? fg : bg; // least significant bit first
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    private static int define(Pattern pattern, String source) throws IOException {
        Matcher m = pattern.matcher(source);
        if (!m.find()) throw new IOException("not an XBM image: missing " + pattern.pattern());
        return Integer.parseInt(m.group(1));
    }
}
