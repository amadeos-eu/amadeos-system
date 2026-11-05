package eu.amadeos.system.screen.modules;

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
    // Emblem colors, the same as in the boot menu, the wallpaper and on amadeos.eu: "One" in the Horizon teal,
    // the other workspaces grey (#8C8EA2 at 55 % on black).
    private static final Color ACTIVE = new Color(0x2F, 0xB8, 0xB0);
    private static final Color FACE = new Color(0x4D, 0x4E, 0x59);
    private static final Color CLOCK = new Color(0xBF, 0xB6, 0xAF);

    private double x;
    private double y;
    private double dx;
    private double dy;
    private float hue;
    // The fonts depend only on the height; made again when it changes, not in every frame.
    private int fontHeight = -1;
    private Font title;
    private Font clock;

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
        if (h != fontHeight) {
            title = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, h / 9));
            clock = title.deriveFont(Font.PLAIN, Math.max(8f, h / 22f));
            fontHeight = h;
        }
        FontMetrics tm = g.getFontMetrics(title);
        FontMetrics cm = g.getFontMetrics(clock);
        String name = "amadeOS";
        String time = LocalTime.now().format(TIME);
        // Emblem proportions as in icons/src/boot-splash.svg: two rows of square tiles about as high as the capitals.
        int cell = Math.max(3, Math.round(title.getSize() * 0.41f));
        int cellGap = Math.max(1, Math.round(cell * 3f / 23f));
        int emblemW = 2 * cell + cellGap;
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
        paintEmblem(g, logoX, baseline - 2 * cell - cellGap, cell, cellGap);
        g.setFont(title);
        g.setColor(Color.getHSBColor(hue, 0.35f, 0.9f));
        g.drawString(name, logoX + emblemW + emblemGap, baseline);
        g.setFont(clock);
        g.setColor(CLOCK);
        g.drawString(time, (int) x + (blockW - cm.stringWidth(time)) / 2, (int) y + tm.getHeight() + cm.getAscent());
    }

    /** The four workspaces as flat rounded tiles, "One" in teal. */
    private static void paintEmblem(Graphics2D g, int x, int y, int cell, int gap) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = Math.max(2, Math.round(cell * 12f / 23f));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                g.setColor(row == 0 && col == 0 ? ACTIVE : FACE);
                g.fillRoundRect(x + col * (cell + gap), y + row * (cell + gap), cell, cell, arc, arc);
            }
        }
    }
}
