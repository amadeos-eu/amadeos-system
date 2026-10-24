package eu.amadeos.system.wm.x11;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import eu.amadeos.system.base.x11.LibX11;

/**
 * The pictures the Alt+Tab switcher shows: a small preview of each window, read from the screen while the
 * window was in full view, and the application's icon from _NET_WM_ICON. Without a compositor the X server
 * keeps nothing of a covered window, so a preview is the window as it looked when it was last seen whole.
 * Used on the X event thread only.
 */
final class WindowPreviews {
    private static final Logger LOGGER = Logger.getLogger(WindowPreviews.class.getName());

    /** Previews are kept at twice the switcher's tile size at most; that is plenty and keeps memory small. */
    static final int MAX_WIDTH = 392;
    static final int MAX_HEIGHT = 264;
    private static final int ICON_SIZE = 32;
    /** _NET_WM_ICON holds every size the application offers; 1 MiB of longs covers 256x256 and the rest. */
    private static final long ICON_PROPERTY_LONGS = 1L << 17;

    private final LibX11 x11;
    private final Pointer display;
    private final long rootWindow;
    private final NativeLong netWmIcon;
    private final Map<Long, BufferedImage> previews = new HashMap<>();
    private final Map<Long, BufferedImage> icons = new HashMap<>();

    WindowPreviews(LibX11 x11, Pointer display, long rootWindow) {
        this.x11 = x11;
        this.display = display;
        this.rootWindow = rootWindow;
        this.netWmIcon = x11.XInternAtom(display, "_NET_WM_ICON", 0);
    }

    /** Reads {@code area} of the screen as the preview of {@code clientWin}. */
    void capture(long clientWin, Rectangle area) {
        BufferedImage shot = readScreen(area);
        if (shot != null) {
            previews.put(clientWin, scaleDown(shot, MAX_WIDTH, MAX_HEIGHT));
        }
    }

    BufferedImage preview(long clientWin) {
        return previews.get(clientWin);
    }

    /** The application's icon, about 32 pixels, or null if the window has none. */
    BufferedImage icon(long clientWin) {
        return icons.computeIfAbsent(clientWin, this::readIcon);
    }

    /** The icon property changed: read it again next time. */
    void iconChanged(long clientWin) {
        icons.remove(clientWin);
    }

    boolean isIconProperty(NativeLong atom) {
        return netWmIcon != null && netWmIcon.equals(atom);
    }

    void forget(long clientWin) {
        previews.remove(clientWin);
        icons.remove(clientWin);
    }

    // --8<-- [start:read-screen]
    private BufferedImage readScreen(Rectangle area) {
        x11.XSync(display, 0);
        Pointer image = x11.XGetImage(display, new NativeLong(rootWindow), area.x, area.y, area.width, area.height,
                new NativeLong(-1L), LibX11.ZPixmap);
        if (image == null) {
            return null;
        }
        try {
            // struct XImage: width, height, xoffset, format, char *data, byte_order, bitmap_unit,
            // bitmap_bit_order, bitmap_pad, depth, bytes_per_line, bits_per_pixel, ...
            int dataOffset = 16;
            int intsAfterData = dataOffset + Native.POINTER_SIZE;
            Pointer data = image.getPointer(dataOffset);
            int bytesPerLine = image.getInt(intsAfterData + 5 * 4);
            int bitsPerPixel = image.getInt(intsAfterData + 6 * 4);
            if (data == null || bitsPerPixel != 32) {
                LOGGER.fine(() -> "No window preview: the screen has " + bitsPerPixel + " bits per pixel");
                return null;
            }
            BufferedImage shot = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB);
            int[] row = new int[area.width];
            for (int y = 0; y < area.height; y++) {
                data.read((long) y * bytesPerLine, row, 0, area.width);
                shot.setRGB(0, y, area.width, 1, row, 0, area.width);
            }
            return shot;
        } finally {
            x11.XDestroyImage(image);
        }
    }
    // --8<-- [end:read-screen]

    /** Halves the picture step by step, then scales to fit: one bilinear step from a large window blurs badly. */
    static BufferedImage scaleDown(BufferedImage source, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight()));
        int targetW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetW || current.getHeight() != targetH) {
            current = resize(current, targetW, targetH);
        }
        return current;
    }

    private static BufferedImage resize(BufferedImage source, int w, int h) {
        BufferedImage target = new BufferedImage(w, h, source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType());
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private BufferedImage readIcon(long clientWin) {
        if (netWmIcon == null || netWmIcon.longValue() == 0) {
            return null;
        }
        PointerByReference actualType = new PointerByReference();
        IntByReference actualFormat = new IntByReference();
        NativeLongByReference nitems = new NativeLongByReference();
        NativeLongByReference bytesAfter = new NativeLongByReference();
        PointerByReference prop = new PointerByReference();
        int status = x11.XGetWindowProperty(display, new NativeLong(clientWin), netWmIcon,
                new NativeLong(0), new NativeLong(ICON_PROPERTY_LONGS), 0, new NativeLong(LibX11.XA_CARDINAL),
                actualType, actualFormat, nitems, bytesAfter, prop);
        Pointer data = prop.getValue();
        if (status != 0 || data == null) {
            return null;
        }
        try {
            long count = nitems.getValue().longValue();
            if (actualFormat.getValue() != 32 || count < 3) {
                return null;
            }
            // Format 32 items are C longs in memory. The property is a list of width, height, ARGB pixels.
            long[] values = new long[(int) count];
            for (int i = 0; i < count; i++) {
                values[i] = data.getNativeLong((long) i * NativeLong.SIZE).longValue();
            }
            return pickIcon(values, ICON_SIZE);
        } finally {
            x11.XFree(data);
        }
    }

    /**
     * The icon closest to {@code size} from a _NET_WM_ICON value: the smallest one at least that large, else
     * the largest, scaled to {@code size}.
     */
    static BufferedImage pickIcon(long[] values, int size) {
        int best = -1;
        int bestW = 0;
        int bestH = 0;
        int i = 0;
        while (i + 2 <= values.length) {
            int w = (int) values[i];
            int h = (int) values[i + 1];
            if (w <= 0 || h <= 0 || i + 2 + (long) w * h > values.length) break;
            boolean better = best < 0
                    || (w >= size && (bestW < size || w < bestW))
                    || (bestW < size && w > bestW);
            if (better) {
                best = i;
                bestW = w;
                bestH = h;
            }
            i += 2 + w * h;
        }
        if (best < 0) {
            return null;
        }
        BufferedImage icon = new BufferedImage(bestW, bestH, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[bestW * bestH];
        for (int p = 0; p < pixels.length; p++) {
            pixels[p] = (int) values[best + 2 + p];
        }
        icon.setRGB(0, 0, bestW, bestH, pixels, 0, bestW);
        return bestW == size && bestH == size ? icon : scaleDown(icon, size, size);
    }
}
