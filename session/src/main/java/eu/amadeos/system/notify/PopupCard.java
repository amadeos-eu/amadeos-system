package eu.amadeos.system.notify;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * The content of one pop-up: the icon on the left; the program's name, the age and a close box at the top; the
 * bold summary, the body and the program's buttons. Critical notifications get a red bar on the left edge.
 * A click anywhere but on a button or the close box is the {@code default} action.
 */
final class PopupCard extends JPanel {
    /** What the card does; the stack passes it on to the daemon's core. */
    interface Actions {
        void invoke(long id, String key);

        void dismiss(long id);
    }

    static final int WIDTH = 360;
    static final int ICON = 40;

    private final Notification notification;
    private final JLabel age = new JLabel();
    private final Messages texts;
    private final Supplier<Instant> now;

    PopupCard(Notification n, Messages texts, Actions actions, Supplier<Instant> now) {
        super(new BorderLayout(12, 0));
        this.notification = n;
        this.texts = texts;
        this.now = now;
        setName("popup." + n.id());
        DesktopTheme t = AmadeUI.theme();
        setBackground(t.base());
        int pad = Math.round(AmadeUI.scaled(10));
        setBorder(BorderFactory.createCompoundBorder(new AmadeUI.MotifBevelBorder(true, 2),
                BorderFactory.createEmptyBorder(pad, pad + (n.critical() ? 6 : 2), pad, pad)));

        JLabel icon = new JLabel(NotificationLook.icon(n, Math.round(AmadeUI.scaled(ICON))));
        icon.setVerticalAlignment(SwingConstants.TOP);
        add(icon, BorderLayout.WEST);

        // The card minus the bevel, the padding, the icon and the gap after it.
        int textWidth = Math.round(AmadeUI.scaled(WIDTH)) - 4 - 2 * pad - (n.critical() ? 8 : 2)
                - Math.round(AmadeUI.scaled(ICON)) - 12;
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JLabel app = new JLabel(n.appName());
        app.putClientProperty("html.disable", Boolean.TRUE);
        Font small = app.getFont().deriveFont(Font.PLAIN, AmadeUI.scaled(11f));
        app.setFont(small);
        app.setForeground(n.critical() ? NotificationLook.CRITICAL : t.textInactive());
        age.setFont(small);
        age.setForeground(t.textInactive());
        JButton close = closeButton(texts.get("close"));
        close.addActionListener(e -> actions.dismiss(n.id()));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(age);
        right.add(close);
        head.add(app, BorderLayout.CENTER);
        head.add(right, BorderLayout.EAST);
        head.setAlignmentX(LEFT_ALIGNMENT);
        text.add(head);

        JLabel summary = wrapped("<html><b>" + BodyMarkup.escape(n.summary()) + "</b></html>", textWidth);
        summary.setName("popup.summary");
        summary.setAlignmentX(LEFT_ALIGNMENT);
        text.add(summary);
        if (!n.body().isBlank()) {
            text.add(Box.createVerticalStrut(2));
            JLabel body = wrapped("<html>" + BodyMarkup.toHtml(clip(n.body())) + "</html>", textWidth);
            body.setName("popup.body");
            body.setAlignmentX(LEFT_ALIGNMENT);
            text.add(body);
        }
        add(text, BorderLayout.CENTER);

        if (!n.buttons().isEmpty()) {
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.setOpaque(false);
            buttons.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
            for (Notification.Action a : n.buttons()) {
                JButton b = new JButton(a.label());
                b.putClientProperty("html.disable", Boolean.TRUE);
                b.setName("popup.action." + a.key());
                b.setFocusable(false);
                b.addActionListener(e -> actions.invoke(n.id(), a.key()));
                buttons.add(b);
            }
            add(buttons, BorderLayout.SOUTH);
        }

        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) actions.invoke(n.id(), Notification.DEFAULT_ACTION);
                else actions.dismiss(n.id());
            }
        };
        listen(this, click);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshAge(ZoneId.systemDefault());
    }

    Notification notification() {
        return notification;
    }

    void refreshAge(ZoneId zone) {
        age.setText(NotificationLook.age(notification.time(), now.get(), zone, texts));
    }

    /** A label whose HTML wraps at {@code width} pixels; its height follows the wrapped text. */
    static JLabel wrapped(String html, int width) {
        JLabel label = new JLabel(html);
        View view = (View) label.getClientProperty(BasicHTML.propertyKey);
        if (view != null) {
            view.setSize(width, 0);
            int height = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
            Dimension size = new Dimension(width, height);
            label.setPreferredSize(size);
            label.setMaximumSize(size);
        }
        return label;
    }

    /** Very long bodies (some programs send whole messages) are cut after about ten lines. */
    static String clip(String body) {
        return body.length() <= 600 ? body : body.substring(0, 600) + "…";
    }

    private static void listen(Component c, MouseAdapter click) {
        if (c instanceof JButton) return;
        c.addMouseListener(click);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) listen(child, click);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (notification.critical()) {
            g.setColor(NotificationLook.CRITICAL);
            g.fillRect(3, 3, Math.round(AmadeUI.scaled(5)), getHeight() - 6);
        }
    }

    /** The small square close box with an ×, as in the mock-up. */
    private static JButton closeButton(String tooltip) {
        int size = Math.round(AmadeUI.scaled(15));
        JButton b = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(AmadeUI.theme().base());
                g2.fillRect(0, 0, getWidth(), getHeight());
                boolean pressed = getModel().isArmed() && getModel().isPressed();
                AmadeUI.paintBevel(g2, 0, 0, getWidth(), getHeight(), !pressed, 1);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AmadeUI.theme().textActive());
                g2.setStroke(new BasicStroke(Math.max(1.5f, getWidth() / 9f)));
                int m = getWidth() / 4;
                g2.drawLine(m, m, getWidth() - m - 1, getHeight() - m - 1);
                g2.drawLine(getWidth() - m - 1, m, m, getHeight() - m - 1);
                g2.dispose();
            }
        };
        b.setName("popup.close");
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setPreferredSize(new Dimension(size, size));
        return b;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.round(AmadeUI.scaled(WIDTH)), d.height);
    }
}
