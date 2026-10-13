package org.datazuul.amadeos.screen.modules;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/** The amadeOS name and the time, drifting slowly across the screen to avoid burn-in. */
final class LogoModule implements SaverModule {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

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
        int blockW = Math.max(tm.stringWidth(name), cm.stringWidth(time));
        int blockH = tm.getHeight() + cm.getHeight();

        // Bounce inside the visible area.
        if (x < 0) { x = 0; dx = Math.abs(dx); }
        if (y < 0) { y = 0; dy = Math.abs(dy); }
        if (x + blockW > w) { x = Math.max(0, w - blockW); dx = -Math.abs(dx); }
        if (y + blockH > h) { y = Math.max(0, h - blockH); dy = -Math.abs(dy); }

        g.setFont(title);
        g.setColor(Color.getHSBColor(hue, 0.35f, 0.9f));
        g.drawString(name, (int) x + (blockW - tm.stringWidth(name)) / 2, (int) y + tm.getAscent());
        g.setFont(clock);
        g.setColor(new Color(0xBF, 0xB6, 0xAF));
        g.drawString(time, (int) x + (blockW - cm.stringWidth(time)) / 2, (int) y + tm.getHeight() + cm.getAscent());
    }
}
