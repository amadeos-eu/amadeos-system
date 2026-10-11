package org.datazuul.amadeos.wm.panel.widget;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import org.datazuul.amadeos.wm.theme.DesktopTheme;

/**
 * Pixel-exact geometry and painting helpers for the authentic CDE Front Panel chrome,
 * measured from an original CDE screenshot.
 *
 * <pre>
 * Vertical layout of the 85px panel (rows from top):
 *   0..1   outer bevel highlight (#D1D1D2)
 *   2..3   gray frame body (#999999)
 *   4      inner frame highlight
 *   5..19  drawer tab row (15px, gray, raised tabs: highlight left, shadow right)
 *   20     button plate top highlight
 *   21..78 button plate (seamless, no vertical dividers)
 *   79     button plate bottom shadow
 *   80     inner frame shadow (#4E4E4E)
 *   81..82 gray frame body
 *   83..84 outer bevel shadow
 * </pre>
 *
 * Horizontally: 2px outer bevel, 2px frame, 1px inner bevel, 20px grip handle, then slots.
 */
public final class CdePanelChrome {
    /** Gray frame / drawer tab / grip handle color. */
    public static final Color FRAME_BG = new Color(0x99, 0x99, 0x99);
    /** Highlight of the gray frame color set. */
    public static final Color FRAME_HI = new Color(0xD1, 0xD1, 0xD2);
    /** Shadow of the gray frame color set. */
    public static final Color FRAME_SH = new Color(0x4E, 0x4E, 0x4E);

    /** Height of the slot content area (tab row + button plate). */
    public static final int SLOT_HEIGHT = 75;
    /** Height of the drawer tab row. */
    public static final int TAB_HEIGHT = 15;
    /** Width of a standard slot. */
    public static final int SLOT_WIDTH = 58;
    /** Width of the first (clock) slot next to the grip handle. */
    public static final int FIRST_SLOT_WIDTH = 56;

    /** Insets of the frame around the slot content area (top/bottom). */
    public static final int FRAME_INSET_V = 5;
    /** Width of the side grip handle column. */
    public static final int GRIP_WIDTH = 20;
    /** Horizontal inset from panel edge to slot content area (frame + grip). */
    public static final int FRAME_INSET_H = 5 + GRIP_WIDTH;

    private CdePanelChrome() {
    }

    /**
     * Paints one slot bay: a raised drawer tab segment on top (without triangle)
     * and the seamless button plate below (top highlight row, bottom shadow row,
     * no vertical dividers).
     */
    public static void paintSlotBay(Graphics g, int w, int h, DesktopTheme t) {
        Graphics g2 = crisp(g);
        paintTab(g2, 0, 0, w, TAB_HEIGHT, FRAME_BG);
        paintPlate(g2, w, h, t);
        g2.dispose();
    }

    /** Returns a graphics copy with anti-aliasing disabled for pixel-exact bevel lines. */
    private static Graphics crisp(Graphics g) {
        Graphics c = g.create();
        if (c instanceof Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
        return c;
    }

    /** Paints the button plate part of a slot (rows TAB_HEIGHT .. h-1). */
    public static void paintPlate(Graphics g, int w, int h, DesktopTheme t) {
        g.setColor(t.base());
        g.fillRect(0, TAB_HEIGHT, w, h - TAB_HEIGHT);
        g.setColor(t.highlight());
        g.drawLine(0, TAB_HEIGHT, w - 1, TAB_HEIGHT);
        g.setColor(t.darkShadow());
        g.drawLine(0, h - 1, w - 1, h - 1);
    }

    /** Paints a raised flat drawer tab: highlight on left edge, shadow on right edge. */
    public static void paintTab(Graphics gIn, int x, int y, int w, int h, Color fill) {
        Graphics g = crisp(gIn);
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(FRAME_HI);
        g.drawLine(x, y, x, y + h - 1);
        g.setColor(FRAME_SH);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        g.dispose();
    }

    /** Paints the complete outer frame including both grip handles. */
    public static void paintFrame(Graphics gIn, int w, int h) {
        Graphics g = crisp(gIn);
        // Gray frame body
        g.setColor(FRAME_BG);
        g.fillRect(0, 0, w, FRAME_INSET_V);
        g.fillRect(0, h - FRAME_INSET_V, w, FRAME_INSET_V);
        g.fillRect(0, 0, FRAME_INSET_H, h);
        g.fillRect(w - FRAME_INSET_H, 0, FRAME_INSET_H, h);

        // Outer bevel (2px): shadow bottom/right first, highlight top/left wins at corners
        g.setColor(FRAME_SH);
        g.fillRect(0, h - 2, w, 2);
        g.fillRect(w - 2, 0, 2, h);
        g.setColor(FRAME_HI);
        g.fillRect(0, 0, w - 1, 2);
        g.fillRect(0, 0, 2, h);
        g.drawLine(w - 2, 0, w - 2, 0);

        // Inner bevel (1px): highlight top/left, shadow bottom/right
        g.setColor(FRAME_SH);
        g.drawLine(5, h - 5, w - 5, h - 5);
        g.drawLine(w - 5, 4, w - 5, h - 5);
        g.setColor(FRAME_HI);
        g.drawLine(4, 4, w - 5, 4);
        g.drawLine(4, 4, 4, h - 5);

        paintGrip(g, 5, h);
        paintGrip(g, w - 5 - GRIP_WIDTH, h);
        g.dispose();
    }

    /** Paints a grip handle column starting at x = gx (20px wide) with collapse button and ribs. */
    private static void paintGrip(Graphics g, int gx, int h) {
        int gr = gx + GRIP_WIDTH - 1;
        int top = FRAME_INSET_V;           // row 5
        int boxBottom = top + 13;          // row 18 (shadow)
        int ribStart = top + 14;           // row 19 (first highlight rib)
        int ribEnd = h - FRAME_INSET_V - 1; // row 79

        // Collapse button: raised box (highlight top/left, shadow bottom/right)
        g.setColor(FRAME_BG);
        g.fillRect(gx, top, GRIP_WIDTH, 14);
        g.setColor(FRAME_HI);
        g.drawLine(gx, top, gr, top);
        g.drawLine(gx, top, gx, boxBottom);
        g.setColor(FRAME_SH);
        g.drawLine(gx + 1, boxBottom, gr, boxBottom);
        g.drawLine(gr, top + 1, gr, boxBottom);

        // Minus symbol: small raised bar (12x4)
        int mx = gx + 4;
        int my = top + 5;
        g.setColor(FRAME_HI);
        g.drawLine(mx, my, mx + 11, my);
        g.drawLine(mx, my, mx, my + 3);
        g.setColor(FRAME_SH);
        g.drawLine(mx + 1, my + 3, mx + 11, my + 3);
        g.drawLine(mx + 11, my + 1, mx + 11, my + 3);

        // Ribs: alternating 1px highlight / 1px shadow lines
        for (int y = ribStart; y <= ribEnd; y++) {
            g.setColor(((y - ribStart) % 2 == 0) ? FRAME_HI : FRAME_SH);
            g.drawLine(gx, y, gr, y);
        }
    }
}
