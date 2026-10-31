package eu.amadeos.system.notify;

import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.util.List;

import org.freedesktop.dbus.Container;

/**
 * The {@code image-data} hint: a struct {@code (iiibiiay)} of width, height, row stride, alpha, bits per sample,
 * channels and the pixel bytes (RGB or RGBA, 8 bits). Firefox sends the site's icon and Thunderbird the sender's
 * picture this way.
 */
final class NotificationImages {
    /** Larger images are not accepted (a pop-up shows 48 pixels). */
    static final int MAX_SIDE = 1024;

    private NotificationImages() {
    }

    static BufferedImage fromImageData(Object value) {
        Object[] fields = fields(value);
        if (fields == null || fields.length < 7) return null;
        try {
            int width = ((Number) fields[0]).intValue();
            int height = ((Number) fields[1]).intValue();
            int stride = ((Number) fields[2]).intValue();
            boolean alpha = (Boolean) fields[3];
            int bits = ((Number) fields[4]).intValue();
            int channels = ((Number) fields[5]).intValue();
            byte[] data = bytes(fields[6]);
            if (data == null || bits != 8 || width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                    || channels != (alpha ? 4 : 3) || stride < width * channels
                    || (long) stride * (height - 1) + (long) width * channels > data.length) {
                return null;
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = y * stride + x * channels;
                    int a = alpha ? data[i + 3] & 0xFF : 0xFF;
                    image.setRGB(x, y, a << 24 | (data[i] & 0xFF) << 16 | (data[i + 1] & 0xFF) << 8 | data[i + 2] & 0xFF);
                }
            }
            return image;
        } catch (ClassCastException | NullPointerException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static Object[] fields(Object value) {
        if (value instanceof Object[] array) return array;
        if (value instanceof Container c) return c.getParameters();
        if (value instanceof List<?> list) return list.toArray();
        return null;
    }

    private static byte[] bytes(Object o) {
        if (o instanceof byte[] b) return b;
        if (o instanceof List<?> list) {
            byte[] out = new byte[list.size()];
            for (int i = 0; i < out.length; i++) out[i] = ((Number) list.get(i)).byteValue();
            return out;
        }
        if (o != null && o.getClass().isArray()) {
            int n = Array.getLength(o);
            byte[] out = new byte[n];
            for (int i = 0; i < n; i++) out[i] = ((Number) Array.get(o, i)).byteValue();
            return out;
        }
        return null;
    }
}
