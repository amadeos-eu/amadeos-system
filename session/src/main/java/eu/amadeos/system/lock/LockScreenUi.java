package eu.amadeos.system.lock;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * The visible part of the lock: one override-redirect window over all monitors. It never
 * receives input; keyboard and pointer are grabbed by {@link ScreenLock} on its own connection.
 * Must be used on the Swing event dispatch thread.
 */
final class LockScreenUi {
    static final String TITLE = "amadeOS Workstation Locked";
    private final JWindow window = new JWindow();
    private final JLabel dots = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel status = new JLabel("Type your password and press Enter.", SwingConstants.CENTER);

    LockScreenUi(String user) {
        DesktopTheme theme = ThemeManager.currentTheme();
        // POPUP windows are override-redirect on X11: the window manager neither frames nor moves them.
        window.setType(Window.Type.POPUP);
        window.setName(TITLE);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setBounds(allScreens());

        JPanel backdrop = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(0x1e, 0x26, 0x2c));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0x16, 0x1d, 0x22));
                for (int y = 0; y < getHeight(); y += 4) {
                    for (int x = (y % 8 == 0 ? 0 : 2); x < getWidth(); x += 4) {
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
        };

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(theme.base());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createRaisedBevelBorder(), BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        card.setPreferredSize(new Dimension(460, 200));

        JLabel title = new JLabel(TITLE, SwingConstants.CENTER);
        title.setOpaque(true);
        title.setBackground(theme.activeTitleBg());
        title.setForeground(theme.textActive());
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        title.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        card.add(title, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(theme.base());
        body.setBorder(BorderFactory.createEmptyBorder(16, 24, 12, 24));
        JLabel userLabel = new JLabel("User: " + user, SwingConstants.CENTER);
        userLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        dots.setOpaque(true);
        dots.setBackground(theme.sunkenBg());
        dots.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        dots.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        for (JLabel label : new JLabel[]{userLabel, dots, status}) {
            label.setAlignmentX(0.5f);
            label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            body.add(label);
            body.add(javax.swing.Box.createVerticalStrut(10));
        }
        card.add(body, BorderLayout.CENTER);
        backdrop.add(card);
        window.setContentPane(backdrop);
    }

    void show() {
        window.setVisible(true);
        window.toFront();
    }

    void raise() {
        if (window.isVisible()) {
            window.toFront();
        }
    }

    void setPasswordLength(int length) {
        dots.setText(length == 0 ? " " : "●".repeat(Math.min(length, 32)));
    }

    void setStatus(String message, boolean error) {
        status.setForeground(error ? new Color(0xB0, 0x20, 0x20) : Color.DARK_GRAY);
        status.setText(message);
    }

    void dispose() {
        window.dispose();
    }

    /** Union of all monitors, so no screen is left uncovered. */
    static Rectangle allScreens() {
        Rectangle bounds = new Rectangle();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            bounds = bounds.union(device.getDefaultConfiguration().getBounds());
        }
        return bounds.isEmpty() ? new Rectangle(0, 0, 1024, 768) : bounds;
    }
}
