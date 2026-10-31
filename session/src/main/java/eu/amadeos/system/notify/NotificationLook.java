package eu.amadeos.system.notify;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.system.base.theme.IconManager;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** What the pop-ups and the history window share: the icon of a notification, its age, the red of critical ones. */
final class NotificationLook {
    static final Color CRITICAL = new Color(0xB0, 0x30, 0x28);
    static final Color BELL = new Color(0xD8, 0xB0, 0x40);

    private NotificationLook() {
    }

    /** The image the sender passed, else its icon by name or path, else a bell. */
    static Icon icon(Notification n, int size) {
        if (n.image() != null) {
            return new ImageIcon(n.image().getScaledInstance(size, size, Image.SCALE_SMOOTH));
        }
        if (!n.icon().isEmpty()) {
            try {
                Icon icon = IconManager.getIcon(n.icon(), size);
                if (icon != null && icon.getIconWidth() > 0) return icon;
            } catch (RuntimeException ignored) {
                // fall back to the bell
            }
        }
        return bell(size, BELL);
    }

    /** A bell in the colours of the theme, the glyph of the notifications. */
    static Icon bell(int size, Color fill) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
                double s = size / 36.0;
                g2.scale(s, s);
                Path2D bell = new Path2D.Double();
                bell.moveTo(7, 28);
                bell.curveTo(10, 23, 9, 7, 18, 6);
                bell.curveTo(27, 7, 26, 23, 29, 28);
                bell.closePath();
                g2.setColor(fill);
                g2.fill(bell);
                g2.setColor(AmadeUI.theme().textActive());
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(bell);
                g2.fillOval(15, 28, 6, 5);
                g2.fillOval(16, 3, 4, 4);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** "now", "5 min", "2 h", then the time of day, then the date. */
    static String age(Instant time, Instant now, ZoneId zone, Messages texts) {
        Duration d = Duration.between(time, now);
        if (d.toMinutes() < 1) return texts.get("age.now");
        if (d.toMinutes() < 60) return texts.format("age.minutes", d.toMinutes());
        if (d.toHours() < 6) return texts.format("age.hours", d.toHours());
        return clock(time, now, zone, texts);
    }

    /** The time of day for today's notifications, else the short date and time. */
    static String clock(Instant time, Instant now, ZoneId zone, Messages texts) {
        LocalDate day = LocalDate.ofInstant(time, zone);
        DateTimeFormatter f = day.equals(LocalDate.ofInstant(now, zone))
                ? DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                : DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
        return f.withLocale(texts.locale()).format(time.atZone(zone));
    }
}
