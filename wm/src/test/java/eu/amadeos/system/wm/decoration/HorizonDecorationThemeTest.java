package eu.amadeos.system.wm.decoration;

import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.theme.HorizonThemes;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HorizonDecorationThemeTest {
    private static final DesktopTheme DARK = HorizonThemes.theme(HorizonThemes.Scheme.DARK, HorizonThemes.Accent.TEAL);

    @Test
    void buttonsSitOnTheRightInOrderAndTheIconOnTheLeft() {
        FrameMetrics m = new HorizonFrameMetrics();
        int w = 600;
        Rectangle min = m.getButtonBounds(w, WindowButton.MINIMIZE);
        Rectangle max = m.getButtonBounds(w, WindowButton.MAXIMIZE);
        Rectangle close = m.getButtonBounds(w, WindowButton.CLOSE);
        Rectangle menu = m.getButtonBounds(w, WindowButton.MENU);
        assertTrue(min.x < max.x && max.x < close.x);
        assertEquals(w - HorizonFrameMetrics.RIGHT_MARGIN, close.x + close.width);
        assertTrue(menu.x + menu.width < m.getTitleBarBounds(w).x);
        assertTrue(m.getTitleBarBounds(w).x + m.getTitleBarBounds(w).width < min.x);
        assertEquals(WindowButton.CLOSE, m.hitTestButton(w, close.x + 3, close.y + 3).orElseThrow());
        assertTrue(m.isTitleBarDragZone(w, 300, 15));
        assertFalse(m.isTitleBarDragZone(w, close.x + 3, close.y + 3));
        assertEquals(ResizeDirection.SE, m.hitTestBorder(w, 400, w - 1, 399));
    }

    @Test
    void framesAThinBorderAndATallerTitleBar() {
        FrameMetrics m = new HorizonFrameMetrics();
        assertEquals(400 + 2 * HorizonFrameMetrics.BORDER, m.frameWidth(400));
        assertEquals(300 + HorizonFrameMetrics.TITLE_BAR_HEIGHT + HorizonFrameMetrics.BORDER, m.frameHeight(300));
    }

    @Test
    void activeFrameShowsTheAccentLineInactiveDoesNot() {
        HorizonDecorationTheme theme = new HorizonDecorationTheme(DARK);
        BufferedImage active = paint(theme, true);
        BufferedImage inactive = paint(theme, false);
        int accent = DARK.activeTitleBg().getRGB() & 0xFFFFFF;
        assertEquals(accent, active.getRGB(300, 1) & 0xFFFFFF);
        assertNotEquals(accent, inactive.getRGB(300, 1) & 0xFFFFFF);
    }

    private static BufferedImage paint(HorizonDecorationTheme theme, boolean active) {
        BufferedImage img = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        theme.paintFrame(g, 600, 200, "Notes", active, null, null);
        g.dispose();
        return img;
    }

    @Test
    void switchableStyleFollowsTheLook() {
        SwitchableDecorationTheme style = new SwitchableDecorationTheme(DesktopTheme.WARM_MOTIF);
        assertEquals(MotifDecorationTheme.NAME, style.name());
        assertEquals(0, style.cornerRadius());
        assertTrue(style.setTheme(DARK));
        assertEquals(HorizonDecorationTheme.NAME, style.name());
        assertEquals(HorizonDecorationTheme.CORNER_RADIUS, style.cornerRadius());
        assertFalse(style.setTheme(HorizonThemes.theme(HorizonThemes.Scheme.LIGHT, HorizonThemes.Accent.RED)));
        assertTrue(style.setTheme(DesktopTheme.CDE_BROCADE));
    }

    @Test
    void roundedShapeCoversTheMiddleAndCutsTheCorners() {
        List<int[]> rows = FrameShape.rows(100, 60, 10);
        long area = rows.stream().mapToLong(r -> (long) r[2] * r[3]).sum();
        assertTrue(area < 100L * 60);
        assertTrue(area > 100L * 60 - 4 * 10 * 10);
        // the top row is inset, the middle spans the full width
        int[] top = rows.stream().filter(r -> r[1] == 0).findFirst().orElseThrow();
        assertTrue(top[0] > 0);
        assertTrue(rows.stream().anyMatch(r -> r[0] == 0 && r[2] == 100 && r[3] == 40));
    }
}
