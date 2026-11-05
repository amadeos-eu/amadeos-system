package eu.amadeos.system.lock;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import eu.amadeos.commons.swing.HorizonUI;
import eu.amadeos.commons.theme.DesktopTheme;

/**
 * The lock screen in the Horizon look: Horizon's picture of the scheme, dimmed, with the time and date, the
 * user's round picture (the first letter of the name on the accent colour), the name, and the typed password
 * as dots in a rounded field.
 */
final class HorizonLockPane extends JPanel {
    static final String PICTURES = "/usr/share/backgrounds/amadeos/horizon-";
    private static final Color TEXT = new Color(0xF0, 0xF1, 0xF6);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DesktopTheme theme;
    private final Image picture;
    private final Timer clock;

    HorizonLockPane(String user, DesktopTheme theme, JLabel dots, JLabel status) {
        super(new GridBagLayout());
        this.theme = theme;
        this.picture = picture(theme.isDark() ? "dark" : "light");

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel time = label(64, Font.PLAIN);
        JLabel date = label(18, Font.PLAIN);
        Runnable tick = () -> {
            LocalDateTime now = LocalDateTime.now();
            time.setText(now.format(TIME));
            date.setText(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())));
        };
        tick.run();
        clock = new Timer(1000, e -> tick.run());
        clock.start();

        JLabel name = label(18, Font.BOLD);
        name.setText(user);
        dots.setForeground(TEXT);
        dots.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        dots.setHorizontalAlignment(SwingConstants.CENTER);
        PasswordPill pill = new PasswordPill(dots);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        status.setHorizontalAlignment(SwingConstants.CENTER);

        add(column, time, 0);
        add(column, date, 64);
        add(column, new Avatar(user), 14);
        add(column, name, 18);
        add(column, pill, 10);
        add(column, status, 0);
        add(column);
    }

    private static void add(JPanel column, JComponent c, int gapBelow) {
        c.setAlignmentX(0.5f);
        column.add(c);
        if (gapBelow > 0) column.add(Box.createVerticalStrut(gapBelow));
    }

    private static JLabel label(int size, int style) {
        JLabel l = new JLabel(" ", SwingConstants.CENTER);
        l.setFont(new Font(Font.SANS_SERIF, style, size));
        l.setForeground(TEXT);
        return l;
    }

    static Image picture(String scheme) {
        File file = new File(PICTURES + scheme + ".png");
        try {
            return file.isFile() ? ImageIO.read(file) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void removeNotify() {
        clock.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth(), h = getHeight();
        if (picture != null && picture.getWidth(null) > 0) {
            int pw = picture.getWidth(null), ph = picture.getHeight(null);
            double scale = Math.max(w / (double) pw, h / (double) ph);
            int sw = (int) Math.ceil(pw * scale), sh = (int) Math.ceil(ph * scale);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(picture, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        } else {
            g2.setColor(new Color(0x14, 0x15, 0x1D));
            g2.fillRect(0, 0, w, h);
        }
        // Dimmed, so the white text reads on the light scheme too
        g2.setColor(new Color(0, 0, 0, theme.isDark() ? 110 : 140));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }

    /** The user's round picture: the first letter of the name on the accent colour. */
    private final class Avatar extends JComponent {
        private static final int SIZE = 96;
        private final String letter;

        Avatar(String user) {
            String n = user == null ? "" : user.strip();
            letter = n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
            Dimension d = new Dimension(SIZE, SIZE);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            HorizonUI.Palette p = HorizonUI.Palette.of(theme);
            g2.setColor(p.accent());
            g2.fillOval(1, 1, SIZE - 2, SIZE - 2);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, SIZE / 2));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(p.onAccent());
            g2.drawString(letter, (SIZE - fm.stringWidth(letter)) / 2, (SIZE - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** A rounded, see-through field that holds the dots of the typed password. */
    private static final class PasswordPill extends JPanel {
        PasswordPill(JLabel dots) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 16, 6, 16));
            Dimension d = new Dimension(320, 42);
            setPreferredSize(d);
            setMaximumSize(d);
            dots.setOpaque(false);
            dots.setBorder(null);
            add(dots);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f,
                    getHeight(), getHeight());
            g2.setColor(new Color(255, 255, 255, 38));
            g2.fill(r);
            g2.setColor(new Color(255, 255, 255, 90));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(r);
            g2.dispose();
        }
    }
}
