package org.datazuul.amadeos.panel.cde.widget;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.base.theme.ThemeManager;

/**
 * Pixel-exact geometry and painting helpers for the authentic CDE Front Panel chrome,
 * measured from the original CDE screenshot.
 *
 * <pre>
 * Vertical layout of the 85px panel (rows from top):
 *   0..1   outer bevel highlight (frame highlight)
 *   2..3   frame body
 *   4      inner frame highlight
 *   5..19  drawer tab row (15px, raised tabs: highlight left, shadow right)
 *   20     button plate top highlight
 *   21..78 button plate (seamless, no vertical dividers)
 *   79     button plate bottom shadow
 *   80     inner frame shadow
 *   81..82 frame body
 *   83..84 outer bevel shadow
 * </pre>
 *
 * Horizontally: 2px outer bevel, 2px frame, 1px inner bevel, 20px grip handle, then slots.
 */
public final class CdePanelChrome {
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

    /** Returns a graphics copy with anti-aliasing disabled for pixel-exact bevel lines. */
    public static Graphics crisp(Graphics g) {
        Graphics c = g.create();
        if (c instanceof Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
        return c;
    }

    /**
     * Paints one slot bay: a raised drawer tab segment on top (without triangle)
     * and the seamless button plate below (top highlight row, bottom shadow row,
     * no vertical dividers).
     */
    public static void paintSlotBay(Graphics g, int w, int h, DesktopTheme t) {
        Graphics g2 = crisp(g);
        paintTab(g2, 0, 0, w, TAB_HEIGHT, t.panelFrameBg(), t);
        paintPlate(g2, w, h, t);
        g2.dispose();
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
    public static void paintTab(Graphics gIn, int x, int y, int w, int h, Color fill, DesktopTheme t) {
        Graphics g = crisp(gIn);
        Color bg = fill != null ? fill : t.panelFrameBg();
        g.setColor(bg);
        g.fillRect(x, y, w, h);
        g.setColor(t.panelFrameHighlight());
        g.drawLine(x, y, x, y + h - 1);
        g.setColor(t.panelFrameShadow());
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        g.dispose();
    }

    /** Overload for backwards compatibility. */
    public static void paintTab(Graphics gIn, int x, int y, int w, int h, Color fill) {
        paintTab(gIn, x, y, w, h, fill, ThemeManager.currentTheme());
    }

    /** Paints the complete outer frame including both grip handles. */
    public static void paintFrame(Graphics gIn, int w, int h, DesktopTheme t) {
        Graphics g = crisp(gIn);
        Color frameBg = t.panelFrameBg();
        Color frameHi = t.panelFrameHighlight();
        Color frameSh = t.panelFrameShadow();

        // Frame body
        g.setColor(frameBg);
        g.fillRect(0, 0, w, FRAME_INSET_V);
        g.fillRect(0, h - FRAME_INSET_V, w, FRAME_INSET_V);
        g.fillRect(0, 0, FRAME_INSET_H, h);
        g.fillRect(w - FRAME_INSET_H, 0, FRAME_INSET_H, h);

        // Outer bevel (2px): shadow bottom/right first, highlight top/left wins at corners
        g.setColor(frameSh);
        g.fillRect(0, h - 2, w, 2);
        g.fillRect(w - 2, 0, 2, h);
        g.setColor(frameHi);
        g.fillRect(0, 0, w - 1, 2);
        g.fillRect(0, 0, 2, h);
        g.drawLine(w - 2, 0, w - 2, 0);

        // Inner bevel (1px): highlight top/left, shadow bottom/right
        g.setColor(frameSh);
        g.drawLine(5, h - 5, w - 5, h - 5);
        g.drawLine(w - 5, 4, w - 5, h - 5);
        g.setColor(frameHi);
        g.drawLine(4, 4, w - 5, 4);
        g.drawLine(4, 4, 4, h - 5);

        paintGrip(g, 5, h, frameBg, frameHi, frameSh);
        paintGrip(g, w - 5 - GRIP_WIDTH, h, frameBg, frameHi, frameSh);
        g.dispose();
    }

    public static void paintFrame(Graphics gIn, int w, int h) {
        paintFrame(gIn, w, h, ThemeManager.currentTheme());
    }

    /** Paints a grip handle column starting at x = gx (20px wide) with collapse button and ribs. */
    private static void paintGrip(Graphics g, int gx, int h, Color frameBg, Color frameHi, Color frameSh) {
        int gr = gx + GRIP_WIDTH - 1;
        int top = FRAME_INSET_V;           // row 5
        int boxBottom = top + 13;          // row 18 (shadow)
        int ribStart = top + 14;           // row 19 (first highlight rib)
        int ribEnd = h - FRAME_INSET_V - 1; // row 79

        // Collapse button: raised box (highlight top/left, shadow bottom/right)
        g.setColor(frameBg);
        g.fillRect(gx, top, GRIP_WIDTH, 14);
        g.setColor(frameHi);
        g.drawLine(gx, top, gr, top);
        g.drawLine(gx, top, gx, boxBottom);
        g.setColor(frameSh);
        g.drawLine(gx + 1, boxBottom, gr, boxBottom);
        g.drawLine(gr, top + 1, gr, boxBottom);

        // Minus symbol: small raised bar (12x4)
        int mx = gx + 4;
        int my = top + 5;
        g.setColor(frameHi);
        g.drawLine(mx, my, mx + 11, my);
        g.drawLine(mx, my, mx, my + 3);
        g.setColor(frameSh);
        g.drawLine(mx + 1, my + 3, mx + 11, my + 3);
        g.drawLine(mx + 11, my + 1, mx + 11, my + 3);

        // Ribs: alternating 1px highlight / 1px shadow lines
        for (int y = ribStart; y <= ribEnd; y++) {
            g.setColor(((y - ribStart) % 2 == 0) ? frameHi : frameSh);
            g.drawLine(gx, y, gr, y);
        }
    }

    /** Paints the authentic 11x6 carved CDE drawer triangle in a tab. */
    public static void paintDrawerTriangle(Graphics gIn, int tabW, int tabH, boolean isOpen, boolean isPressed, DesktopTheme t) {
        Graphics g = crisp(gIn);
        int cx = tabW / 2;
        int cy = tabH / 2;
        if (isPressed) {
            cx += 1;
            cy += 1;
        }

        Color hi = t.panelFrameHighlight();
        Color sh = t.panelFrameShadow();

        if (!isOpen) {
            // Points UP: 11px wide base, 6px tall (y: cy-3 to cy+2)
            int topY = cy - 3;
            int botY = cy + 2;
            int leftX = cx - 5;
            int rightX = cx + 5;

            // Fill
            g.setColor(new Color(0x82, 0x82, 0x82));
            int[] xp = { cx, leftX, rightX };
            int[] yp = { topY, botY, botY };
            g.fillPolygon(xp, yp, 3);

            // Left slope: shadow
            g.setColor(sh);
            g.drawLine(cx, topY, leftX, botY);

            // Right slope: highlight
            g.setColor(hi);
            g.drawLine(cx, topY, rightX, botY);

            // Bottom base: highlight
            g.drawLine(leftX + 1, botY, rightX, botY);
        } else {
            // Points DOWN
            int topY = cy - 3;
            int botY = cy + 2;
            int leftX = cx - 5;
            int rightX = cx + 5;

            // Fill
            g.setColor(new Color(0x82, 0x82, 0x82));
            int[] xp = { leftX, rightX, cx };
            int[] yp = { topY, topY, botY };
            g.fillPolygon(xp, yp, 3);

            // Top base: shadow
            g.setColor(sh);
            g.drawLine(leftX, topY, rightX, topY);

            // Left slope: shadow
            g.drawLine(leftX, topY, cx, botY);

            // Right slope: highlight
            g.setColor(hi);
            g.drawLine(rightX, topY, cx, botY);
        }
        g.dispose();
    }
}
