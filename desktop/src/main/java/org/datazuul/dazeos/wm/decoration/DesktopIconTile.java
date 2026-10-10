package org.datazuul.dazeos.wm.decoration;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.datazuul.dazeos.wm.x11.LibC;
import org.datazuul.dazeos.wm.x11.LibX11;

public final class DesktopIconTile {
    public static final int TILE_WIDTH = 68;
    public static final int TILE_HEIGHT = 68;

    private static final Color COLOR_BG = new Color(0xC0, 0xC0, 0xC0);
    private static final Color COLOR_LIGHT = Color.WHITE;
    private static final Color COLOR_DARK = new Color(0x55, 0x55, 0x55);
    private static final Color COLOR_SHADOW = new Color(0x33, 0x33, 0x33);
    private static final Color COLOR_PLATE_BG = new Color(0xA4, 0xA4, 0xA4);
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    private final long clientWin;
    private final NativeLong tileWin;
    private int index;
    private String title;
    private final BufferedImage image;

    public DesktopIconTile(long clientWin, NativeLong tileWin, int index, String title) {
        this.clientWin = clientWin;
        this.tileWin = tileWin;
        this.index = index;
        this.title = (title != null && !title.isBlank()) ? title : "Window";
        this.image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    }

    public long clientWin() {
        return clientWin;
    }

    public NativeLong tileWin() {
        return tileWin;
    }

    public int index() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int x() {
        return 0;
    }

    public int y() {
        return index * TILE_HEIGHT;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = (title != null && !title.isBlank()) ? title : "Window";
    }

    public void render() {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Fill base background
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, TILE_WIDTH, TILE_HEIGHT);

        // 2. Outer 3D raised Motif border
        g.setColor(COLOR_LIGHT);
        g.drawLine(0, 0, TILE_WIDTH - 1, 0);
        g.drawLine(0, 0, 0, TILE_HEIGHT - 1);
        g.drawLine(1, 1, TILE_WIDTH - 2, 1);
        g.drawLine(1, 1, 1, TILE_HEIGHT - 2);

        g.setColor(COLOR_DARK);
        g.drawLine(TILE_WIDTH - 2, 1, TILE_WIDTH - 2, TILE_HEIGHT - 2);
        g.drawLine(1, TILE_HEIGHT - 2, TILE_WIDTH - 2, TILE_HEIGHT - 2);

        g.setColor(Color.BLACK);
        g.drawLine(TILE_WIDTH - 1, 0, TILE_WIDTH - 1, TILE_HEIGHT - 1);
        g.drawLine(0, TILE_HEIGHT - 1, TILE_WIDTH - 1, TILE_HEIGHT - 1);

        // 3. Icon Area: upper 46px
        renderIconGlyph(g);

        // 4. Horizontal separator line
        g.setColor(COLOR_DARK);
        g.drawLine(3, 46, TILE_WIDTH - 4, 46);
        g.setColor(COLOR_LIGHT);
        g.drawLine(3, 47, TILE_WIDTH - 4, 47);

        // 5. Sunken Title Plate: 48px to 64px
        int plateX = 4;
        int plateY = 49;
        int plateW = TILE_WIDTH - 8;
        int plateH = 15;

        g.setColor(COLOR_PLATE_BG);
        g.fillRect(plateX, plateY, plateW, plateH);

        g.setColor(COLOR_SHADOW);
        g.drawLine(plateX, plateY, plateX + plateW - 1, plateY);
        g.drawLine(plateX, plateY, plateX, plateY + plateH - 1);

        g.setColor(COLOR_LIGHT);
        g.drawLine(plateX + plateW - 1, plateY + 1, plateX + plateW - 1, plateY + plateH - 1);
        g.drawLine(plateX + 1, plateY + plateH - 1, plateX + plateW - 1, plateY + plateH - 1);

        // Label text
        g.setFont(LABEL_FONT);
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();

        String display = title;
        while (fm.stringWidth(display) > plateW - 4 && display.length() > 3) {
            display = display.substring(0, display.length() - 2) + "…";
        }

        int textX = plateX + (plateW - fm.stringWidth(display)) / 2;
        int textY = plateY + (plateH - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(display, textX, textY);

        g.dispose();
    }

    private void renderIconGlyph(Graphics2D g) {
        String lower = title.toLowerCase();

        if (lower.contains("term") || lower.contains("bash") || lower.contains("sh")) {
            // Terminal screen icon
            g.setColor(new Color(0x30, 0x30, 0x30));
            g.fillRect(16, 8, 36, 26);
            g.setColor(COLOR_LIGHT);
            g.drawRect(15, 7, 37, 27);

            // Screen bezel
            g.setColor(Color.BLACK);
            g.fillRect(18, 10, 32, 22);

            // Terminal prompt >_
            g.setColor(new Color(0x33, 0xFF, 0x66)); // Phosphor green
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
            g.drawString(">_", 21, 23);

            // Stand
            g.setColor(new Color(0x50, 0x50, 0x50));
            g.fillRect(30, 35, 8, 4);
            g.fillRect(24, 39, 20, 2);
        } else if (lower.contains("doc") || lower.contains("text") || lower.contains("edit") || lower.contains("note")) {
            // Document sheet icon
            g.setColor(Color.WHITE);
            g.fillRect(19, 7, 30, 34);
            g.setColor(Color.BLACK);
            g.drawRect(18, 6, 31, 35);

            // Text lines on sheet
            g.setColor(new Color(0x40, 0x60, 0x80));
            g.drawLine(23, 14, 43, 14);
            g.drawLine(23, 19, 43, 19);
            g.drawLine(23, 24, 43, 24);
            g.drawLine(23, 29, 37, 29);
            g.drawLine(23, 34, 41, 34);
        } else if (lower.contains("control") || lower.contains("panel") || lower.contains("setting") || lower.contains("diag")) {
            // Control panel / toolbox icon
            g.setColor(new Color(0xB8, 0x5A, 0x30)); // Tool chest reddish-brown
            g.fillRect(17, 14, 34, 22);
            g.setColor(Color.BLACK);
            g.drawRect(16, 13, 35, 23);

            // Handle
            g.setColor(new Color(0x40, 0x40, 0x40));
            g.drawRect(28, 9, 12, 5);

            // Silver latch
            g.setColor(new Color(0xE0, 0xE0, 0xE0));
            g.fillRect(31, 21, 6, 5);
            g.setColor(Color.BLACK);
            g.drawRect(31, 21, 6, 5);
        } else {
            // Workstation application window icon
            g.setColor(new Color(0x50, 0x70, 0x90));
            g.fillRect(18, 8, 32, 28);
            g.setColor(COLOR_LIGHT);
            g.drawRect(17, 7, 33, 29);

            // Window titlebar
            g.setColor(new Color(0xC0, 0xC0, 0xC0));
            g.fillRect(19, 9, 30, 6);
            g.setColor(Color.BLACK);
            g.drawRect(19, 9, 30, 6);

            // Content area
            g.setColor(Color.WHITE);
            g.fillRect(20, 16, 28, 18);
        }
    }

    public void blit(LibX11 x11, Pointer display, Pointer gc, int screen) {
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        long dataSize = (long) data.length * 4L;
        Pointer nativeMem = LibC.INSTANCE.malloc(dataSize);
        if (nativeMem == null) {
            return;
        }

        boolean imageCreated = false;
        try {
            nativeMem.write(0, data, 0, data.length);
            Pointer visual = x11.XDefaultVisual(display, screen);
            int depth = x11.XDefaultDepth(display, screen);

            Pointer ximage = x11.XCreateImage(
                    display, visual, depth, LibX11.ZPixmap, 0, nativeMem,
                    TILE_WIDTH, TILE_HEIGHT, 32, TILE_WIDTH * 4);

            if (ximage != null) {
                imageCreated = true;
                x11.XPutImage(display, tileWin, gc, ximage, 0, 0, 0, 0, TILE_WIDTH, TILE_HEIGHT);
                // XDestroyImage frees both the XImage structure and the underlying nativeMem data
                x11.XDestroyImage(ximage);
            }
        } finally {
            if (!imageCreated) {
                LibC.INSTANCE.free(nativeMem);
            }
        }
    }

    public void destroy(LibX11 x11, Pointer display) {
        if (tileWin != null && tileWin.longValue() != 0) {
            x11.XDestroyWindow(display, tileWin);
        }
    }
}

