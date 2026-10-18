package eu.amadeos.system.screen;

import eu.amadeos.system.screen.modules.SaverModule;

import javax.swing.JWindow;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * An undecorated window above everything, covering all monitors, with an invisible pointer. The daemon
 * notices input through the X server's idle counter; the Style Manager's "Test" ends the preview on the
 * first key or mouse event through {@link #dismissOnInput(Runnable)}.
 */
public final class SaverWindow extends JWindow {
    private final SaverCanvas canvas;

    public SaverWindow(SaverModule module) {
        canvas = new SaverCanvas(module);
        // POPUP windows are override-redirect on X11: the window manager neither frames nor moves them.
        setType(Type.POPUP);
        setAlwaysOnTop(true);
        setContentPane(canvas);
        setBounds(virtualBounds());
        setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "amadeos-blank"));
        canvas.setCursor(getCursor());
    }

    /** The union of all screens. */
    static Rectangle virtualBounds() {
        Rectangle all = new Rectangle();
        for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            all = all.union(device.getDefaultConfiguration().getBounds());
        }
        return all;
    }

    public void showSaver() {
        setVisible(true);
        toFront();
        canvas.start();
    }

    public void close() {
        canvas.stop();
        dispose();
    }

    /** Ends the saver at the first key press, mouse click or noticeable mouse movement. */
    public void dismissOnInput(Runnable onDismiss) {
        Runnable dismiss = () -> {
            close();
            onDismiss.run();
        };
        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                dismiss.run();
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            private Point first;

            @Override
            public void mousePressed(MouseEvent e) {
                dismiss.run();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (first == null) first = e.getPoint();
                else if (first.distance(e.getPoint()) > 12) dismiss.run();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                canvas.requestFocusInWindow();
            }
        });
        setFocusableWindowState(true);
    }
}
