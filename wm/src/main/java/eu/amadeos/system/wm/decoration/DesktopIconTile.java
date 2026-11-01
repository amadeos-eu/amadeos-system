package eu.amadeos.system.wm.decoration;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.swing.Icon;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.IconManager;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.base.x11.LibC;
import eu.amadeos.system.base.x11.LibX11;

public final class DesktopIconTile {
    public static final int TILE_WIDTH = 68;
    public static final int TILE_HEIGHT = 68;

    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    private final long clientWin;
    private final NativeLong tileWin;
    private int index;
    private String title;
    private final BufferedImage image;
    private DesktopTheme theme;

    public DesktopIconTile(long clientWin, NativeLong tileWin, int index, String title) {
        this.clientWin = clientWin;
        this.tileWin = tileWin;
        this.index = index;
        this.title = (title != null && !title.isBlank()) ? title : "Window";
        this.image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        this.theme = ThemeManager.currentTheme();
    }

    public DesktopTheme theme() {
        return theme;
    }

    public void setTheme(DesktopTheme theme) {
        this.theme = theme;
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
        return top + index * TILE_HEIGHT;
    }

    /** Where the column of tiles starts: below a top bar (Horizon), else at the top of the screen. */
    private static volatile int top;

    public static void setTop(int y) {
        top = Math.max(0, y);
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = (title != null && !title.isBlank()) ? title : "Window";
    }

    public void render() {
        DesktopTheme t = (theme != null) ? theme : ThemeManager.currentTheme();
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Fill base background using theme base color
        g.setColor(t.base());
        g.fillRect(0, 0, TILE_WIDTH, TILE_HEIGHT);

        // 2. Outer 3D raised Motif border
        g.setColor(t.highlight());
        g.drawLine(0, 0, TILE_WIDTH - 1, 0);
        g.drawLine(0, 0, 0, TILE_HEIGHT - 1);
        g.setColor(t.softHighlight());
        g.drawLine(1, 1, TILE_WIDTH - 2, 1);
        g.drawLine(1, 1, 1, TILE_HEIGHT - 2);

        g.setColor(t.shadow());
        g.drawLine(TILE_WIDTH - 2, 1, TILE_WIDTH - 2, TILE_HEIGHT - 2);
        g.drawLine(1, TILE_HEIGHT - 2, TILE_WIDTH - 2, TILE_HEIGHT - 2);

        g.setColor(t.darkShadow());
        g.drawLine(TILE_WIDTH - 1, 0, TILE_WIDTH - 1, TILE_HEIGHT - 1);
        g.drawLine(0, TILE_HEIGHT - 1, TILE_WIDTH - 1, TILE_HEIGHT - 1);

        // 3. Icon Area: upper 46px with crisp theme pixmap
        renderIconGlyph(g);

        // 4. Horizontal separator line
        g.setColor(t.shadow());
        g.drawLine(3, 46, TILE_WIDTH - 4, 46);
        g.setColor(t.highlight());
        g.drawLine(3, 47, TILE_WIDTH - 4, 47);

        // 5. Sunken Title Plate: 48px to 64px
        int plateX = 4;
        int plateY = 49;
        int plateW = TILE_WIDTH - 8;
        int plateH = 15;

        g.setColor(t.sunkenBg());
        g.fillRect(plateX, plateY, plateW, plateH);

        g.setColor(t.darkShadow());
        g.drawLine(plateX, plateY, plateX + plateW - 1, plateY);
        g.drawLine(plateX, plateY, plateX, plateY + plateH - 1);

        g.setColor(t.highlight());
        g.drawLine(plateX + plateW - 1, plateY + 1, plateX + plateW - 1, plateY + plateH - 1);
        g.drawLine(plateX + 1, plateY + plateH - 1, plateX + plateW - 1, plateY + plateH - 1);

        // Label text
        g.setFont(LABEL_FONT);
        g.setColor(t.textActive());
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
        String iconKey = "window";
        if (lower.contains("term") || lower.contains("bash") || lower.contains("sh")) {
            iconKey = "terminal";
        } else if (lower.contains("doc") || lower.contains("text") || lower.contains("edit") || lower.contains("note")) {
            iconKey = "text-editor";
        } else if (lower.contains("control") || lower.contains("panel") || lower.contains("setting") || lower.contains("diag")) {
            iconKey = "diagnostics";
        } else if (lower.contains("file") || lower.contains("dir") || lower.contains("folder")) {
            iconKey = "file-manager";
        }

        Icon icon = IconManager.getIcon(iconKey, 32);
        icon.paintIcon(null, g, (TILE_WIDTH - 32) / 2, 7);
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

