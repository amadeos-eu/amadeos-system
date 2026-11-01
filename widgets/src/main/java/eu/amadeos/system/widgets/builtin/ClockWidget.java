package eu.amadeos.system.widgets.builtin;

import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.commons.widget.WidgetColors;
import eu.amadeos.commons.widget.WidgetContext;
import eu.amadeos.commons.widget.WidgetSettings;
import eu.amadeos.system.widgets.WidgetTexts;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** A clock: a round face with the accent-coloured second hand and the date below, or large digits. */
public final class ClockWidget implements DesktopWidget {
    @Override
    public String id() {
        return "clock";
    }

    @Override
    public String name() {
        return WidgetTexts.get("clock.name");
    }

    @Override
    public String description() {
        return WidgetTexts.get("clock.description");
    }

    @Override
    public Dimension defaultSize() {
        return new Dimension(220, 250);
    }

    @Override
    public JComponent create(WidgetContext context) {
        JComponent face = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                paintClock((Graphics2D) g0, getWidth(), getHeight(), LocalDateTime.now(), context);
            }
        };
        context.every(Duration.ofSeconds(1), face::repaint);
        return face;
    }

    static boolean digital(WidgetContext context) {
        return "digital".equals(context.get("style", "analog"));
    }

    static void paintClock(Graphics2D g0, int w, int h, LocalDateTime now, WidgetContext context) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        WidgetColors c = context.colors();
        String date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", context.locale()));
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, Math.min(w, h) / 14));
        if (digital(context)) {
            String time = now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(context.locale()));
            Font big = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(18, Math.min(w / 3, h / 2)));
            g.setFont(big);
            FontMetrics fm = g.getFontMetrics();
            int y = h / 2 + fm.getAscent() / 3;
            g.setColor(c.text());
            g.drawString(time, (w - fm.stringWidth(time)) / 2, y);
            g.setFont(small);
            FontMetrics sm = g.getFontMetrics();
            g.setColor(c.dim());
            g.drawString(date, (w - sm.stringWidth(date)) / 2, y + sm.getHeight() + 4);
            g.dispose();
            return;
        }
        g.setFont(small);
        FontMetrics sm = g.getFontMetrics();
        int dateH = sm.getHeight() + 6;
        double d = Math.min(w, h - dateH) - 4;
        double cx = w / 2.0;
        double cy = (h - dateH) / 2.0;
        double r = d / 2;
        g.setColor(WidgetColors.mix(c.card(), c.text(), 0.06));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, d, d));
        g.setColor(c.line());
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, d, d));
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            double inner = r * (i % 3 == 0 ? 0.80 : 0.86);
            g.setColor(i % 3 == 0 ? c.text() : c.dim());
            g.setStroke(new BasicStroke(i % 3 == 0 ? 2.4f : 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(cx + Math.sin(a) * inner, cy - Math.cos(a) * inner,
                    cx + Math.sin(a) * r * 0.92, cy - Math.cos(a) * r * 0.92));
        }
        double sec = now.getSecond();
        double min = now.getMinute() + sec / 60;
        double hour = now.getHour() % 12 + min / 60;
        hand(g, cx, cy, hour * 30, r * 0.5, 4.2f, c.text());
        hand(g, cx, cy, min * 6, r * 0.75, 3f, c.text());
        hand(g, cx, cy, sec * 6, r * 0.82, 1.4f, c.accent());
        g.setColor(c.accent());
        g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
        g.setColor(c.dim());
        g.drawString(date, (int) (cx - sm.stringWidth(date) / 2.0), (int) (cy + r) + sm.getAscent() + 6);
        g.dispose();
    }

    private static void hand(Graphics2D g, double cx, double cy, double degrees, double length, float width,
                             java.awt.Color color) {
        double a = Math.toRadians(degrees);
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx - Math.sin(a) * length * 0.12, cy + Math.cos(a) * length * 0.12,
                cx + Math.sin(a) * length, cy - Math.cos(a) * length));
    }

    @Override
    public WidgetSettings settings(WidgetContext context) {
        JRadioButton analog = new JRadioButton(WidgetTexts.get("clock.analog"), !digital(context));
        JRadioButton digital = new JRadioButton(WidgetTexts.get("clock.digital"), digital(context));
        ButtonGroup group = new ButtonGroup();
        group.add(analog);
        group.add(digital);
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(analog);
        form.add(digital);
        return new WidgetSettings(form, () -> context.set("style", digital.isSelected() ? "digital" : "analog"));
    }
}
