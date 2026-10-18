package eu.amadeos.screen.modules;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * The amadeOS logo (the four workspaces emblem and the name) and the time, drifting slowly across
 * the screen to avoid burn-in.
 */
final class LogoModule implements SaverModule {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    // Emblem colors, the same as in the boot menu and the wallpaper
    private static final Color ACTIVE = new Color(0x4E7A96);
    private static final Color ACTIVE_SHADOW = new Color(0x27384A);
    private static final Color ACTIVE_HIGHLIGHT = new Color(0x9CB8D0);
    private static final Color FACE = new Color(0xC8BEB4);
    private static final Color HIGHLIGHT = new Color(0xF0ECE8);
    private static final Color SHADOW = new Color(0x6E6660);

    private double x;
    private double y;
    private double dx;
    private double dy;
    private float hue;

    @Override
    public String id() {
        return "logo";
    }

    @Override
    public String name() {
        return "Floating Logo";
    }

    @Override
    public void reset(int width, int height, Random random) {
        x = random.nextDouble() * Math.max(1, width / 2.0);
        y = random.nextDouble() * Math.max(1, height / 2.0);
        double speed = Math.max(0.4, Math.min(width, height) / 900.0);
        dx = (random.nextBoolean() ? 1 : -1) * speed;
        dy = (random.nextBoolean() ? 1 : -1) * speed;
        hue = random.nextFloat();
    }

    @Override
    public void step() {
        x += dx;
        y += dy;
        hue = (hue + 0.0008f) % 1f;
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, h / 9));
        Font clock = title.deriveFont(Font.PLAIN, Math.max(8f, h / 22f));
        FontMetrics tm = g.getFontMetrics(title);
        FontMetrics cm = g.getFontMetrics(clock);
        String name = "amadeOS";
        String time = LocalTime.now().format(TIME);
        // Emblem proportions as in icons/src/boot-splash.svg: two rows of cells about as high as the capitals.
        int cellH = Math.max(3, Math.round(title.getSize() * 0.39f));
        int cellW = Math.round(cellH * 28f / 22f);
        int cellGap = Math.max(1, title.getSize() / 14);
        int emblemW = 2 * cellW + cellGap;
        int emblemGap = Math.round(title.getSize() * 0.32f);
        int logoW = emblemW + emblemGap + tm.stringWidth(name);
        int blockW = Math.max(logoW, cm.stringWidth(time));
        int blockH = tm.getHeight() + cm.getHeight();

        // Bounce inside the visible area.
        if (x < 0) { x = 0; dx = Math.abs(dx); }
        if (y < 0) { y = 0; dy = Math.abs(dy); }
        if (x + blockW > w) { x = Math.max(0, w - blockW); dx = -Math.abs(dx); }
        if (y + blockH > h) { y = Math.max(0, h - blockH); dy = -Math.abs(dy); }

        int logoX = (int) x + (blockW - logoW) / 2;
        int baseline = (int) y + tm.getAscent();
        paintEmblem(g, logoX, baseline - 2 * cellH - cellGap, cellW, cellH, cellGap);
        g.setFont(title);
        g.setColor(Color.getHSBColor(hue, 0.35f, 0.9f));
        g.drawString(name, logoX + emblemW + emblemGap, baseline);
        g.setFont(clock);
        g.setColor(new Color(0xBF, 0xB6, 0xAF));
        g.drawString(time, (int) x + (blockW - cm.stringWidth(time)) / 2, (int) y + tm.getHeight() + cm.getAscent());
    }

    /** The four workspaces of the front panel as Motif buttons, "One" pressed and highlighted. */
    private static void paintEmblem(Graphics2D g, int x, int y, int cellW, int cellH, int gap) {
        int bevel = Math.max(1, cellH / 11);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                int cx = x + col * (cellW + gap);
                int cy = y + row * (cellH + gap);
                boolean active = row == 0 && col == 0;
                Color face = active ? ACTIVE : FACE;
                Color topLeft = active ? ACTIVE_SHADOW : HIGHLIGHT;
                Color bottomRight = active ? ACTIVE_HIGHLIGHT : SHADOW;
                g.setColor(face);
                g.fillRect(cx, cy, cellW, cellH);
                g.setColor(topLeft);
                g.fillRect(cx, cy, cellW, bevel);
                g.fillRect(cx, cy, bevel, cellH);
                g.setColor(bottomRight);
                g.fillRect(cx, cy + cellH - bevel, cellW, bevel);
                g.fillRect(cx + cellW - bevel, cy, bevel, cellH);
            }
        }
    }
}
