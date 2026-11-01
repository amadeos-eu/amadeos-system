package eu.amadeos.system.icons;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Paints the three shapes of the Horizon icons on a 128-unit grid, at any size:
 * <ul>
 * <li>a <b>tile</b> for programs and groups: a rounded square with a top-to-bottom gradient, a soft curve of light
 * across its lower part (the horizon) and the glyph in white lines;</li>
 * <li>a <b>folder</b> for places, in blue, with a small white glyph on its front;</li>
 * <li>a <b>page</b> with a folded corner for file types, with a coloured band and glyph.</li>
 * </ul>
 * Small sizes (24 pixels and less) leave out the curve and the shadows, which would only blur there, and keep the
 * lines at least a pixel and a quarter wide.
 */
public final class IconPainter {
    private IconPainter() {
    }

    static BufferedImage image(int size, Consumer<Graphics2D> painter) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.scale(size / 128.0, size / 128.0);
        painter.accept(g);
        g.dispose();
        return image;
    }

    private static float minWidth(int size) {
        return (float) (1.25 / (size / 128.0));
    }

    private static boolean small(int size) {
        return size <= 24;
    }

    /** The glyph twice: a soft dark copy one step lower as its shadow, then the glyph itself. */
    private static void glyph(Graphics2D g, int size, Consumer<Pen> glyph, Color color) {
        if (!small(size)) {
            Graphics2D shadow = (Graphics2D) g.create();
            shadow.translate(0, 2);
            glyph.accept(new Pen(shadow, minWidth(size)).color(Color.BLACK, 0.22).width(6));
            shadow.dispose();
        }
        glyph.accept(new Pen(g, minWidth(size)).color(color).width(6));
    }

    // ---- tile ------------------------------------------------------------------------------------

    public static BufferedImage tile(int size, Color top, Color bottom, Consumer<Pen> glyph) {
        return image(size, g -> {
            boolean small = small(size);
            double inset = small ? 4 : 8;
            double side = 128 - 2 * inset;
            double radius = small ? 26 : 26;
            Shape body = new RoundRectangle2D.Double(inset, inset, side, side, 2 * radius, 2 * radius);
            if (!small) {
                g.setColor(new Color(0, 0, 0, 64));
                g.fill(new RoundRectangle2D.Double(inset, inset + 2, side, side, 2 * radius, 2 * radius));
            }
            g.setPaint(new GradientPaint(0, (float) inset, top, 0, (float) (inset + side), bottom));
            g.fill(body);
            if (!small) {
                Graphics2D clip = (Graphics2D) g.create();
                clip.clip(body);
                Path2D wave = Pen.parse("M 8 104 C 44 82 84 74 120 78 L 120 120 L 8 120 Z");
                clip.setColor(new Color(255, 255, 255, 26));
                clip.fill(wave);
                clip.setColor(new Color(255, 255, 255, 89));
                clip.setStroke(new BasicStroke(1.5f));
                clip.draw(Pen.parse("M 8 104 C 44 82 84 74 120 78"));
                clip.dispose();
                g.setColor(new Color(255, 255, 255, 56));
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new RoundRectangle2D.Double(inset + 0.75, inset + 0.75, side - 1.5, side - 1.5,
                        2 * radius - 1.5, 2 * radius - 1.5));
            }
            if (small) {
                // a little larger glyph on the small tiles, which have less margin
                g.translate(64, 64);
                g.scale(1.12, 1.12);
                g.translate(-64, -64);
            }
            glyph(g, size, glyph, Color.WHITE);
        });
    }

    // ---- folder ----------------------------------------------------------------------------------

    static final Color FOLDER_TOP = new Color(0x5B, 0xB4, 0xF0);
    static final Color FOLDER_BOTTOM = new Color(0x2E, 0x78, 0xC8);
    static final Color FOLDER_BACK = new Color(0x2A, 0x62, 0xA8);

    public static BufferedImage folder(int size, Color top, Color bottom, Consumer<Pen> glyph) {
        return image(size, g -> {
            boolean small = small(size);
            Color back = mix(bottom, Color.BLACK, 0.18);
            // back with the tab
            Path2D backShape = Pen.parse("M 10 30 Q 10 22 18 22 L 48 22 Q 53 22 56 26 L 62 33 L 110 33 Q 118 33 118 41"
                    + " L 118 100 Q 118 108 110 108 L 18 108 Q 10 108 10 100 Z");
            if (!small) {
                g.setColor(new Color(0, 0, 0, 56));
                g.fill(AffineTransform.getTranslateInstance(0, 2).createTransformedShape(backShape));
            }
            g.setColor(back);
            g.fill(backShape);
            // a sheet of paper peeking out
            g.setColor(new Color(0xF4, 0xF6, 0xFA));
            g.fill(new RoundRectangle2D.Double(18, 36, 92, 40, 8, 8));
            // the front
            Shape front = Pen.parse("M 8 52 Q 8 44 16 44 L 112 44 Q 120 44 120 52 L 118 102 Q 118 110 110 110"
                    + " L 18 110 Q 10 110 10 102 Z");
            g.setPaint(new GradientPaint(0, 44, top, 0, 110, bottom));
            g.fill(front);
            if (!small) {
                g.setColor(new Color(255, 255, 255, 70));
                g.setStroke(new BasicStroke(1.5f));
                g.draw(Pen.parse("M 9 52 Q 9 45 16 45 L 112 45 Q 119 45 119 52"));
            }
            if (glyph != null && size >= 22) {
                Graphics2D gg = (Graphics2D) g.create();
                gg.translate(64, 78);
                gg.scale(0.5, 0.5);
                gg.translate(-64, -64);
                gg.setComposite(AlphaComposite.SrcOver.derive(0.95f));
                glyph.accept(new Pen(gg, minWidth(size) / 0.5f).color(Color.WHITE).width(9));
                gg.dispose();
            }
        });
    }

    // ---- page ------------------------------------------------------------------------------------

    public static BufferedImage page(int size, Color band, Consumer<Pen> glyph) {
        return image(size, g -> {
            boolean small = small(size);
            Path2D sheet = Pen.parse("M 30 10 L 80 10 L 106 36 L 106 112 Q 106 118 100 118 L 30 118 Q 24 118 24 112"
                    + " L 24 16 Q 24 10 30 10 Z");
            if (!small) {
                g.setColor(new Color(0, 0, 0, 56));
                g.fill(AffineTransform.getTranslateInstance(0, 2).createTransformedShape(sheet));
            }
            g.setPaint(new GradientPaint(0, 10, new Color(0xFF, 0xFF, 0xFF), 0, 118, new Color(0xE6, 0xE9, 0xF0)));
            g.fill(sheet);
            g.setColor(new Color(0xB8, 0xBE, 0xCC));
            g.setStroke(new BasicStroke(Math.max(1.5f, minWidth(size) * 0.8f)));
            g.draw(sheet);
            // the folded corner
            g.setColor(new Color(0xCF, 0xD5, 0xE2));
            g.fill(Pen.parse("M 80 10 L 80 30 Q 80 36 86 36 L 106 36 Z"));
            // the coloured band at the bottom with the glyph
            Graphics2D clip = (Graphics2D) g.create();
            clip.clip(sheet);
            clip.setPaint(new GradientPaint(0, 62, mix(band, Color.WHITE, 0.12), 0, 118, band));
            clip.fill(new java.awt.Rectangle(24, 62, 82, 56));
            clip.dispose();
            if (glyph != null) {
                Graphics2D gg = (Graphics2D) g.create();
                gg.translate(65, 90);
                gg.scale(0.40, 0.40);
                gg.translate(-64, -64);
                glyph.accept(new Pen(gg, minWidth(size) / 0.40f).color(Color.WHITE).width(9));
                gg.dispose();
            }
            if (!small) {
                // text lines above the band
                g.setColor(new Color(0xC4, 0xCA, 0xD6));
                g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(Pen.parse("M 36 30 L 66 30 M 36 42 L 92 42 M 36 52 L 82 52"));
            }
        });
    }

    static Color mix(Color a, Color b, double w) {
        return new Color(
                (int) Math.round(a.getRed() * (1 - w) + b.getRed() * w),
                (int) Math.round(a.getGreen() * (1 - w) + b.getGreen() * w),
                (int) Math.round(a.getBlue() * (1 - w) + b.getBlue() * w));
    }
}
