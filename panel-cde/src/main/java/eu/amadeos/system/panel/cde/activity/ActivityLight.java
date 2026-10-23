package eu.amadeos.system.panel.cde.activity;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.theme.ThemeManager;
import eu.amadeos.system.panel.cde.PanelTexts;

/**
 * The busy light above Exit: a sunken slit with ten LED dashes, as in CDE. All ten blink yellow while an
 * application starts; an amber chaser runs while software is installed; otherwise the left five show the
 * disks in green and the right five the network in cyan, more dashes the more bytes move, and the slit is
 * dark when nothing happens. Settings > Front Panel turns the disk and network halves off, which leaves the
 * plain CDE busy light.
 * <p>
 * The counters are read from {@code /proc} every {@link #TICK_MILLIS} on a thread of the light's own; the
 * dashes change on the event dispatch thread. The LED colours are fixed, a real LED does not follow the
 * palette; the slit around them does.
 */
public final class ActivityLight extends JComponent {
    static final int DASHES = 10;
    static final int HALF = DASHES / 2;
    static final long TICK_MILLIS = 250;
    /** Whether dpkg runs is checked every this many ticks (2 s); it walks all processes. */
    static final int INSTALLER_EVERY = 8;
    /** Bytes per second from which the disk half lights 1, 2, ... 5 dashes. */
    static final long[] DISK_STEPS = {64L << 10, 512L << 10, 4L << 20, 16L << 20, 50L << 20};
    /** Bytes per second from which the network half lights 1, 2, ... 5 dashes. */
    static final long[] NETWORK_STEPS = {4L << 10, 64L << 10, 512L << 10, 2L << 20, 10L << 20};
    /** The length of the install chaser, in dashes. */
    static final int CHASER = 3;

    enum Dash {
        OFF(new Color(0x45, 0x42, 0x30)),
        BUSY(new Color(0xFA, 0xCC, 0x15)),
        INSTALL(new Color(0xF5, 0x9E, 0x0B)),
        DISK(new Color(0x4A, 0xDE, 0x80)),
        NETWORK(new Color(0x38, 0xBD, 0xF8));

        final Color color;

        Dash(Color color) {
            this.color = color;
        }
    }

    private static final Color SLIT = new Color(0x1E, 0x24, 0x2B);

    private final ActivityCounters counters;
    private DesktopTheme theme = ThemeManager.currentTheme();
    private ScheduledExecutorService sampler;
    private Runnable onClick;

    // State, on the event dispatch thread
    private boolean launching;
    private boolean installing;
    private boolean installerRunning;
    private boolean meterShown = true;
    private int diskLevel;
    private int networkLevel;
    private long diskRate;
    private long networkRate;
    private int tick;
    private Dash[] shown = dashes();

    public ActivityLight() {
        this(ActivityCounters.system());
    }

    ActivityLight(ActivityCounters counters) {
        this.counters = counters;
        setOpaque(false);
        setToolTipText(PanelTexts.get("activity.tooltip"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && onClick != null) onClick.run();
            }
        });
    }

    /** What a click does: System Information. */
    public void setOnClick(Runnable action) {
        onClick = action;
    }

    public void setTheme(DesktopTheme theme) {
        this.theme = theme;
        repaint();
    }

    /** An application is starting (the window manager's busy flag); on the event dispatch thread. */
    public void setLaunching(boolean busy) {
        if (launching == busy) return;
        launching = busy;
        refresh();
    }

    /** The updates cell installs; on the event dispatch thread. */
    public void setInstalling(boolean busy) {
        if (installing == busy) return;
        installing = busy;
        refresh();
    }

    /** Whether the disk and network halves light up (Settings > Front Panel); on the event dispatch thread. */
    public void setMeterShown(boolean show) {
        if (meterShown == show) return;
        meterShown = show;
        refresh();
    }

    /** Starts reading the counters. */
    public synchronized void start() {
        if (sampler != null) return;
        sampler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "activity-light");
            t.setDaemon(true);
            return t;
        });
        sampler.scheduleWithFixedDelay(new Sampler(), 0, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (sampler == null) return;
        sampler.shutdownNow();
        sampler = null;
    }

    /** Reads the counters and hands the rates to the event dispatch thread. */
    private final class Sampler implements Runnable {
        private long lastDisk = -1;
        private long lastNetwork = -1;
        private long lastNanos;
        private int count;
        private boolean dpkg;

        @Override
        public void run() {
            long now = System.nanoTime();
            long disk = counters.diskBytes();
            long network = counters.networkBytes();
            if (count++ % INSTALLER_EVERY == 0) dpkg = counters.installerRunning();
            if (lastDisk >= 0) {
                double seconds = Math.max(1e-3, (now - lastNanos) / 1e9);
                long diskPerSecond = (long) (Math.max(0, disk - lastDisk) / seconds);
                long networkPerSecond = (long) (Math.max(0, network - lastNetwork) / seconds);
                boolean installer = dpkg;
                SwingUtilities.invokeLater(() -> tick(diskPerSecond, networkPerSecond, installer));
            }
            lastDisk = disk;
            lastNetwork = network;
            lastNanos = now;
        }
    }

    /** One step of the light with the rates just measured; on the event dispatch thread. */
    void tick(long diskPerSecond, long networkPerSecond, boolean installer) {
        tick++;
        diskRate = diskPerSecond;
        networkRate = networkPerSecond;
        installerRunning = installer;
        // Up at once, down one dash per tick, so that a short burst stays visible like the glow of a real LED
        diskLevel = Math.max(level(diskPerSecond, DISK_STEPS), diskLevel - 1);
        networkLevel = Math.max(level(networkPerSecond, NETWORK_STEPS), networkLevel - 1);
        refresh();
    }

    /** How many dashes of a half a rate lights: 0 below the first step, 5 from the last. */
    static int level(long bytesPerSecond, long[] steps) {
        int level = 0;
        while (level < steps.length && bytesPerSecond >= steps[level]) level++;
        return level;
    }

    // --8<-- [start:dashes]
    /** The ten dashes as they are to be seen now. */
    Dash[] dashes() {
        Dash[] dashes = new Dash[DASHES];
        Arrays.fill(dashes, Dash.OFF);
        if (launching) {
            // 2 Hz: on for one tick, off for the next
            if (tick % 2 == 0) Arrays.fill(dashes, Dash.BUSY);
        } else if (installing || installerRunning) {
            // The chaser comes in on the left and leaves on the right, then starts again
            int head = tick % (DASHES + CHASER - 1);
            for (int i = head - CHASER + 1; i <= head; i++) {
                if (i >= 0 && i < DASHES) dashes[i] = Dash.INSTALL;
            }
        } else if (meterShown) {
            for (int i = 0; i < diskLevel; i++) dashes[i] = Dash.DISK;
            for (int i = 0; i < networkLevel; i++) dashes[DASHES - 1 - i] = Dash.NETWORK;
        }
        return dashes;
    }
    // --8<-- [end:dashes]

    /** The tooltip: the title, and what the light shows. */
    String describe() {
        String title = PanelTexts.get("activity.tooltip");
        String detail;
        if (launching) {
            detail = PanelTexts.get("activity.launching");
        } else if (installing || installerRunning) {
            detail = PanelTexts.get("activity.installing");
        } else if (meterShown) {
            detail = PanelTexts.format("activity.rates", rate(diskRate), rate(networkRate));
        } else {
            return title;
        }
        return "<html>" + escape(title) + "<br>" + escape(detail) + "</html>";
    }

    /** "340 KB/s" or "12 MB/s". */
    static String rate(long bytesPerSecond) {
        if (bytesPerSecond < 1L << 20) return PanelTexts.format("activity.rate.kb", bytesPerSecond >> 10);
        return PanelTexts.format("activity.rate.mb", bytesPerSecond / (double) (1L << 20));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    private void refresh() {
        Dash[] now = dashes();
        if (!Arrays.equals(now, shown)) {
            shown = now;
            repaint();
        }
        String tip = describe();
        if (!tip.equals(getToolTipText())) setToolTipText(tip);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int sx = (getWidth() - 24) / 2;
        int sy = (getHeight() - 7) / 2;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Sunken slit border
        g2.setColor(theme.darkShadow());
        g2.drawLine(sx, sy, sx + 23, sy);
        g2.drawLine(sx, sy, sx, sy + 6);
        g2.setColor(Color.WHITE);
        g2.drawLine(sx + 1, sy + 7, sx + 24, sy + 7);
        g2.drawLine(sx + 24, sy + 1, sx + 24, sy + 7);

        // Dark interior
        g2.setColor(SLIT);
        g2.fillRect(sx + 1, sy + 1, 23, 6);

        // The LED dashes, one pixel wide with a pixel between them
        for (int i = 0; i < DASHES; i++) {
            g2.setColor(shown[i].color);
            int dx = sx + 3 + 2 * i;
            g2.drawLine(dx, sy + 2, dx, sy + 5);
        }

        g2.dispose();
    }
}
