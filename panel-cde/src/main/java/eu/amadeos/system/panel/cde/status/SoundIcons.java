package eu.amadeos.system.panel.cde.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import eu.amadeos.commons.theme.DesktopTheme;

/**
 * Vector drawings for the sound cell and its menu, so they scale with the cell and follow the theme colors:
 * a loudspeaker with 0-3 sound waves for the volume, a cross beside it for "muted", and a microphone.
 */
final class SoundIcons {
    private SoundIcons() {
    }

    /** How many waves a volume shows: none at 0, one up to a third, two up to two thirds, three above. */
    static int waves(int volume) {
        if (volume <= 0) return 0;
        if (volume <= 33) return 1;
        if (volume <= 66) return 2;
        return 3;
    }

    /** Loudspeaker on the left half, {@code waves} of three arcs in {@code on}, the rest in {@code off}. */
    static void paintSpeaker(Graphics2D g, double x, double y, double size, int waves, Color on, Color off) {
        paintBody(g, x, y, size, on);
        double cx = x + size * 0.42;
        double cy = y + size / 2;
        g.setStroke(new BasicStroke((float) Math.max(1.2, size / 12), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int wave = 1; wave <= 3; wave++) {
            double r = size * 0.15 * wave;
            g.setColor(waves >= wave ? on : off);
            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, -50, 100, Arc2D.OPEN));
        }
    }

    /** Loudspeaker with an X where the waves would be. */
    static void paintMuted(Graphics2D g, double x, double y, double size, Color body, Color cross) {
        paintBody(g, x, y, size, body);
        g.setColor(cross);
        g.setStroke(new BasicStroke((float) Math.max(1.5, size / 10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double l = x + size * 0.60;
        double r = x + size * 0.90;
        double t = y + size * 0.35;
        double b = y + size * 0.65;
        g.draw(new Line2D.Double(l, t, r, b));
        g.draw(new Line2D.Double(l, b, r, t));
    }

    /** The speaker itself: a small box and the cone opening to the right. */
    private static void paintBody(Graphics2D g, double x, double y, double size, Color fg) {
        double cy = y + size / 2;
        g.setColor(fg);
        Path2D.Double cone = new Path2D.Double();
        cone.moveTo(x + size * 0.10, cy - size * 0.12);
        cone.lineTo(x + size * 0.24, cy - size * 0.12);
        cone.lineTo(x + size * 0.44, cy - size * 0.30);
        cone.lineTo(x + size * 0.44, cy + size * 0.30);
        cone.lineTo(x + size * 0.24, cy + size * 0.12);
        cone.lineTo(x + size * 0.10, cy + size * 0.12);
        cone.closePath();
        g.fill(cone);
    }

    /** A studio microphone: capsule, holder and stand. */
    static void paintMicrophone(Graphics2D g, double x, double y, double size, Color fg) {
        g.setColor(fg);
        double w = size * 0.30;
        g.fill(new RoundRectangle2D.Double(x + (size - w) / 2, y + size * 0.08, w, size * 0.48, w, w));
        g.setStroke(new BasicStroke((float) Math.max(1, size / 12), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double hw = size * 0.46;
        g.draw(new Arc2D.Double(x + (size - hw) / 2, y + size * 0.16, hw, size * 0.52, 180, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(x + size / 2, y + size * 0.68, x + size / 2, y + size * 0.86));
        g.draw(new Line2D.Double(x + size * 0.32, y + size * 0.88, x + size * 0.68, y + size * 0.88));
    }

    /** 16 pixel speaker for the menu, showing the volume the slider stands at. */
    static Icon speakerIcon(DesktopTheme theme, java.util.function.IntSupplier volume,
                            java.util.function.BooleanSupplier muted) {
        return icon((g, x, y) -> {
            if (muted.getAsBoolean()) {
                paintMuted(g, x, y, 16, theme.textActive(), theme.darkShadow());
            } else {
                paintSpeaker(g, x, y, 16, waves(volume.getAsInt()), theme.textActive(), theme.shadow());
            }
        });
    }

    /** 16 pixel speaker or microphone in front of the device headings of the menu. */
    static Icon headingIcon(DesktopTheme theme, boolean input) {
        return icon((g, x, y) -> {
            if (input) {
                paintMicrophone(g, x, y, 16, theme.textActive());
            } else {
                paintSpeaker(g, x, y, 16, 3, theme.textActive(), theme.textActive());
            }
        });
    }

    private interface Painter {
        void paint(Graphics2D g, double x, double y);
    }

    private static Icon icon(Painter painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = NetworkIcons.smooth(g);
                painter.paint(g2, x, y);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }
}
