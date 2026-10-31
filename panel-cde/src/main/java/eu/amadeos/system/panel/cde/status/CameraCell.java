package eu.amadeos.system.panel.cde.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * Camera light in the status slot: shown only while a program has a camera open, as a camera with a red
 * dot, and the tooltip names the programs. It cannot be switched off in Settings, because it exists so that
 * the user always sees when a camera is on.
 * <p>
 * The cell asks {@link CameraUsers} every {@value #POLL_SECONDS} seconds.
 */
public final class CameraCell implements StatusCell {
    static final long POLL_SECONDS = 2;
    static final Color RED = new Color(0xE0, 0x30, 0x2C);

    private final Supplier<Set<String>> source;
    private final Messages texts;
    private final CellView view = new CellView();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "camera-users");
        t.setDaemon(true);
        return t;
    });
    private DesktopTheme theme = ThemeManager.currentTheme();
    private Set<String> programs = Set.of();
    private Runnable relayout = () -> {};

    public CameraCell(CameraUsers users) {
        this(users::programs, Messages.load(CameraCell.class, NetworkCell.DESKTOP_TEXTS,
                LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale()));
    }

    CameraCell(Supplier<Set<String>> source, Messages texts) {
        this.source = source;
        this.texts = texts;
    }

    @Override
    public JComponent component() {
        return view;
    }

    @Override
    public boolean available() {
        return !programs.isEmpty();
    }

    @Override
    public void onAvailabilityChanged(Runnable relayout) {
        this.relayout = relayout;
    }

    @Override
    public void onThemeChanged(DesktopTheme theme) {
        this.theme = theme;
        view.repaint();
    }

    @Override
    public void start() {
        poller.scheduleWithFixedDelay(() -> {
            Set<String> now = source.get();
            SwingUtilities.invokeLater(() -> show(now));
        }, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        poller.shutdownNow();
    }

    // --8<-- [start:show]
    /** Shows who uses a camera; called on the event dispatch thread. */
    void show(Set<String> now) {
        if (programs.equals(now)) return;
        boolean wasOn = !programs.isEmpty();
        programs = Set.copyOf(now);
        view.setToolTipText(programs.isEmpty() ? null : describe(programs));
        view.repaint();
        if (wasOn != !programs.isEmpty()) relayout.run();
    }
    // --8<-- [end:show]

    /** "Camera in use by amade-webcam, firefox". */
    String describe(Set<String> names) {
        return texts.format("camera.in.use", String.join(", ", new java.util.TreeSet<>(names)));
    }

    Set<String> programs() {
        return programs;
    }

    /** A camera body with its lens in the theme's text colour, and a red light in the top right corner. */
    static void paintCamera(Graphics2D g, double x, double y, double size, Color fg) {
        double stroke = Math.max(1.5, size / 11);
        double bodyW = size * 0.74;
        double bodyH = size * 0.50;
        double bx = x + size * 0.06;
        double by = y + size * 0.32;
        g.setColor(fg);
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D hump = new Path2D.Double();
        hump.moveTo(bx + bodyW * 0.28, by);
        hump.lineTo(bx + bodyW * 0.36, by - size * 0.11);
        hump.lineTo(bx + bodyW * 0.64, by - size * 0.11);
        hump.lineTo(bx + bodyW * 0.72, by);
        g.draw(hump);
        g.draw(new RoundRectangle2D.Double(bx, by, bodyW, bodyH, size * 0.12, size * 0.12));
        double lens = bodyH * 0.62;
        g.draw(new Ellipse2D.Double(bx + (bodyW - lens) / 2, by + (bodyH - lens) / 2, lens, lens));
        double dot = size * 0.30;
        double dx = x + size - dot - size * 0.02;
        double dy = y + size * 0.04;
        g.setColor(RED);
        g.fill(new Ellipse2D.Double(dx, dy, dot, dot));
        g.setColor(fg);
        g.setStroke(new BasicStroke((float) Math.max(1, stroke * 0.5)));
        g.draw(new Ellipse2D.Double(dx, dy, dot, dot));
    }

    /** Flat cell on the button plate like the battery: nothing to click. */
    private final class CellView extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            if (programs.isEmpty()) return;
            Graphics2D g2 = NetworkIcons.smooth(g);
            int w = getWidth();
            int h = getHeight();
            double size = Math.min(w, h) - 4;
            paintCamera(g2, (w - size) / 2, (h - size) / 2, size, theme.textActive());
            g2.dispose();
        }
    }
}
