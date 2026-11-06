package eu.amadeos.system.widgets;

import com.sun.jna.Native;
import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.commons.widget.WidgetCard;
import eu.amadeos.commons.widget.WidgetSettings;
import eu.amadeos.system.base.x11.X11EwmhHelper;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One widget copy on the desktop: a frameless window, marked {@code _AMADEOS_DESKTOP_WIDGET} so that the
 * window manager keeps it below the windows, with the widget on a {@link WidgetCard}. Dragging the card moves
 * it, dragging the lower right corner resizes it; the right mouse button opens Settings and Remove. Both also
 * work on parts of the widget that handle the mouse themselves.
 */
final class WidgetWindow extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(WidgetWindow.class.getName());
    static final String TITLE_PREFIX = "amadeOS Widget ";
    /** The corner that resizes, in pixels. */
    static final int GRIP = 16;
    static final int MIN_SIZE = 90;

    private final String id;
    private final DesktopWidget widget;
    private final HostContext context;
    private final WidgetCard card;
    /** Mouse events on parts of the widget that take the mouse themselves, see {@link #forwardToCard}. */
    private final AWTEventListener mouseOnContent = this::forwardToCard;

    WidgetWindow(String id, DesktopWidget widget, HostContext context, Rectangle bounds,
                 Consumer<Rectangle> moved, Runnable removed) {
        super(TITLE_PREFIX + id);
        this.id = id;
        this.widget = widget;
        this.context = context;
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setType(Type.UTILITY);
        JComponent content;
        try {
            content = widget.create(context);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Widget " + widget.id() + " failed", e);
            content = new JLabel(WidgetTexts.format("widget.failed", widget.name()), SwingConstants.CENTER);
        }
        card = new WidgetCard(content, context::colors);
        setContentPane(card);
        setBounds(bounds);
        shape();

        MouseAdapter drag = new MouseAdapter() {
            private Point start;
            private Rectangle startBounds;
            private boolean resizing;

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    menu(removed).show(card, e.getX(), e.getY());
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                start = e.getLocationOnScreen();
                startBounds = getBounds();
                resizing = inGrip(e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (start == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - start.x;
                int dy = now.y - start.y;
                if (resizing) {
                    setSize(Math.max(MIN_SIZE, startBounds.width + dx), Math.max(MIN_SIZE, startBounds.height + dy));
                    card.revalidate();
                    shape();
                } else {
                    setLocation(startBounds.x + dx, startBounds.y + dy);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (start != null && !getBounds().equals(startBounds)) moved.accept(getBounds());
                start = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                card.setCursor(Cursor.getPredefinedCursor(inGrip(e.getPoint())
                        ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        card.addMouseListener(drag);
        card.addMouseMotionListener(drag);
        Toolkit.getDefaultToolkit().addAWTEventListener(mouseOnContent,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    /**
     * A part of the widget with its own mouse listener (such as the calendar, which opens Calendar on a click)
     * gets the mouse events, so they would never reach the card. The card gets a copy of them, so that the
     * widget can be moved and the right mouse button opens the menu there too, unless the part has its own
     * menu or is a text field.
     */
    private void forwardToCard(AWTEvent event) {
        if (!(event instanceof MouseEvent e) || !forwards(e.getComponent())) return;
        if (e.getID() == MouseEvent.MOUSE_CLICKED || e.getID() == MouseEvent.MOUSE_ENTERED
                || e.getID() == MouseEvent.MOUSE_EXITED) return;
        // controls keep the left button to themselves; the card then sees no press, so no drag either
        if (e.getID() == MouseEvent.MOUSE_PRESSED && !SwingUtilities.isRightMouseButton(e)
                && isControl(e.getComponent())) return;
        card.dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, card));
    }

    /** True for a part inside the card whose mouse events don't reach the card's own listener. */
    boolean forwards(Component source) {
        if (source == null || source == card || !SwingUtilities.isDescendingFrom(source, card)) return false;
        if (source instanceof JTextComponent) return false;
        if (source instanceof JComponent c && c.getComponentPopupMenu() != null) return false;
        return source.getMouseListeners().length > 0 || source.getMouseMotionListeners().length > 0;
    }

    private static boolean isControl(Component c) {
        return c instanceof AbstractButton || c instanceof JSlider || c instanceof JScrollBar
                || c instanceof JComboBox || c instanceof JList || c instanceof JTable || c instanceof JSpinner;
    }

    private boolean inGrip(Point p) {
        return p.x >= card.getWidth() - GRIP && p.y >= card.getHeight() - GRIP;
    }

    String id() {
        return id;
    }

    HostContext context() {
        return context;
    }

    private void shape() {
        try {
            setShape(WidgetCard.shape(getWidth(), getHeight()));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException e) {
            LOGGER.fine("No shaped widget: " + e.getMessage());
        }
    }

    private JPopupMenu menu(Runnable removed) {
        JPopupMenu menu = new JPopupMenu();
        JLabel title = new JLabel(widget.name());
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
        title.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        menu.add(title);
        WidgetSettings settings = widget.settings(context);
        JMenuItem settingsItem = new JMenuItem(WidgetTexts.get("menu.settings"));
        settingsItem.setEnabled(settings != null);
        settingsItem.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, settings.form(), widget.name(), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
                settings.apply().run();
                card.revalidate();
                card.repaint();
            }
        });
        menu.add(settingsItem);
        JMenuItem gallery = new JMenuItem(WidgetTexts.get("menu.gallery"));
        gallery.addActionListener(e -> WidgetHost.instance().showGallery());
        menu.add(gallery);
        menu.addSeparator();
        JMenuItem remove = new JMenuItem(WidgetTexts.get("menu.remove"));
        remove.addActionListener(e -> removed.run());
        menu.add(remove);
        return menu;
    }

    // --8<-- [start:on-desktop]
    /** Shows the window as a desktop widget: below the windows, without a frame. */
    void showOnDesktop(X11EwmhHelper helper) {
        addNotify();
        try {
            long win = Native.getWindowID(this);
            if (win != 0 && helper != null) helper.setDesktopWidget(win);
        } catch (Exception e) {
            LOGGER.fine("No window id: " + e.getMessage());
        }
        setVisible(true);
    }
    // --8<-- [end:on-desktop]

    void close() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(mouseOnContent);
        context.close();
        dispose();
    }
}
