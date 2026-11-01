package eu.amadeos.system.wm.decoration;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rounded corners for frame windows through the X Shape extension (libXext). Without a compositor the edge
 * is hard (a pixel is in or out), so the corner is cut as a stack of rectangles, one per pixel row of the
 * arc. If libXext or the extension is missing, windows keep square corners.
 */
final class FrameShape {
    private static final Logger LOGGER = Logger.getLogger(FrameShape.class.getName());
    private static final int SHAPE_BOUNDING = 0;
    private static final int SHAPE_SET = 0;
    private static final int UNSORTED = 0;

    interface LibXext extends Library {
        boolean XShapeQueryExtension(Pointer display, Pointer eventBase, Pointer errorBase);

        void XShapeCombineRectangles(Pointer display, NativeLong window, int destKind, int xOff, int yOff,
                                     Pointer rectangles, int count, int op, int ordering);

        void XShapeCombineMask(Pointer display, NativeLong window, int destKind, int xOff, int yOff,
                               NativeLong pixmap, int op);
    }

    private static volatile LibXext xext;
    private static volatile boolean unavailable;

    private FrameShape() {
    }

    /** Sets the window's shape to a rectangle with rounded corners, or back to a plain rectangle for radius 0. */
    static boolean apply(Pointer display, NativeLong window, int width, int height, int radius) {
        LibXext lib = library(display);
        if (lib == null) return false;
        try {
            if (radius <= 0) {
                lib.XShapeCombineMask(display, window, SHAPE_BOUNDING, 0, 0, new NativeLong(0), SHAPE_SET);
                return true;
            }
            List<int[]> rects = rows(width, height, radius);
            // XRectangle: short x, short y, unsigned short width, unsigned short height
            Memory mem = new Memory(8L * rects.size());
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                mem.setShort(8L * i, (short) r[0]);
                mem.setShort(8L * i + 2, (short) r[1]);
                mem.setShort(8L * i + 4, (short) r[2]);
                mem.setShort(8L * i + 6, (short) r[3]);
            }
            lib.XShapeCombineRectangles(display, window, SHAPE_BOUNDING, 0, 0, mem, rects.size(), SHAPE_SET, UNSORTED);
            return true;
        } catch (RuntimeException | LinkageError e) {
            LOGGER.log(Level.FINE, "Cannot shape frame window", e);
            unavailable = true;
            return false;
        }
    }

    // --8<-- [start:rows]
    /**
     * The shape as rectangles: one per pixel row for the rows of the top and bottom arcs, one for the middle.
     * A pixel row is inside the arc where the circle through its middle reaches.
     */
    static List<int[]> rows(int width, int height, int radius) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        List<int[]> rects = new ArrayList<>();
        for (int y = 0; y < r; y++) {
            double dy = r - y - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
            int w = width - 2 * inset;
            if (w <= 0) continue;
            rects.add(new int[]{inset, y, w, 1});
            rects.add(new int[]{inset, height - 1 - y, w, 1});
        }
        if (height - 2 * r > 0) rects.add(new int[]{0, r, width, height - 2 * r});
        return rects;
    }
    // --8<-- [end:rows]

    private static LibXext library(Pointer display) {
        if (unavailable) return null;
        if (xext == null) {
            synchronized (FrameShape.class) {
                if (xext == null && !unavailable) {
                    try {
                        LibXext lib = Native.load("Xext", LibXext.class);
                        Memory a = new Memory(4);
                        Memory b = new Memory(4);
                        if (lib.XShapeQueryExtension(display, a, b)) {
                            xext = lib;
                        } else {
                            LOGGER.info("The X server has no Shape extension: square window corners");
                            unavailable = true;
                        }
                    } catch (RuntimeException | LinkageError e) {
                        LOGGER.info("libXext not found: square window corners");
                        unavailable = true;
                    }
                }
            }
        }
        return xext;
    }
}
