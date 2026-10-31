package eu.amadeos.system.panel.cde.status;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import eu.amadeos.commons.i18n.LocaleResolution;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.base.power.PowerSupplies;
import eu.amadeos.system.base.theme.ThemeManager;

/**
 * Battery in the status slot, shown only on a computer that has one: a lying battery filled to the charge,
 * green above 50 %, yellow down to 21 % and red at 20 % and below, with a lightning bolt while it charges.
 * The tooltip gives the percentage and, where the kernel reports a rate, the time until empty or full.
 * <p>
 * The cell reads {@code /sys/class/power_supply} every {@value #POLL_SECONDS} seconds, so a battery that is
 * put in or taken out (or a mains adapter plugged in) shows up without restarting the panel.
 */
public final class BatteryCell implements StatusCell {
    static final long POLL_SECONDS = 20;

    private final Supplier<Optional<BatteryStatus>> source;
    private final Messages texts;
    private final CellView view = new CellView();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "battery-status");
        t.setDaemon(true);
        return t;
    });
    private DesktopTheme theme = ThemeManager.currentTheme();
    private Optional<BatteryStatus> status;
    private Runnable relayout = () -> {};
    private java.util.function.Consumer<Optional<BatteryStatus>> readings = s -> { };

    public BatteryCell(PowerSupplies supplies) {
        this(supplies::battery, Messages.load(BatteryCell.class, NetworkCell.DESKTOP_TEXTS,
                LocaleResolution.resolve(Optional.empty(), Optional.empty(), System.getenv()).language().locale()));
    }

    BatteryCell(Supplier<Optional<BatteryStatus>> source, Messages texts) {
        this.source = source;
        this.texts = texts;
        // Reading sysfs takes microseconds; read once now, so the slot is laid out right from the start
        show(source.get());
    }

    @Override
    public JComponent component() {
        return view;
    }

    @Override
    public boolean available() {
        return status.isPresent();
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
            Optional<BatteryStatus> s = source.get();
            SwingUtilities.invokeLater(() -> show(s));
        }, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        poller.shutdownNow();
    }

    /** Hears every changed reading, e.g. {@link DesktopNotices#battery} for the low battery warnings. */
    public void onReading(java.util.function.Consumer<Optional<BatteryStatus>> listener) {
        this.readings = listener;
        if (status != null) listener.accept(status);
    }

    Optional<BatteryStatus> status() {
        return status;
    }

    // --8<-- [start:show]
    /** Shows a reading on the cell; called on the event dispatch thread. */
    void show(Optional<BatteryStatus> s) {
        boolean wasAvailable = status != null && status.isPresent();
        if (Objects.equals(status, s)) return;
        status = s;
        view.setToolTipText(s.map(this::describe).orElse(null));
        view.repaint();
        if (wasAvailable != s.isPresent()) relayout.run();
        readings.accept(s);
    }
    // --8<-- [end:show]

    /** The tooltip: the charge, what the battery does and how long it lasts. */
    String describe(BatteryStatus s) {
        boolean timed = s.minutesLeft() >= 0;
        return switch (s.state()) {
            case DISCHARGING -> timed
                    ? texts.format("battery.discharging.time", s.percent(), duration(s.minutesLeft()))
                    : texts.format("battery.discharging", s.percent());
            case CHARGING -> timed
                    ? texts.format("battery.charging.time", s.percent(), duration(s.minutesLeft()))
                    : texts.format("battery.charging", s.percent());
            case FULL -> texts.format("battery.full", s.percent());
            case NOT_CHARGING -> texts.format("battery.not.charging", s.percent());
        };
    }

    String duration(int minutes) {
        return minutes >= 60
                ? texts.format("battery.time.hours", minutes / 60, String.format("%02d", minutes % 60))
                : texts.format("battery.time.minutes", minutes);
    }

    /** Flat cell on the button plate like the other cells, but nothing to click. */
    private final class CellView extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            Optional<BatteryStatus> current = status;
            if (current.isEmpty()) return;
            BatteryStatus s = current.get();
            Graphics2D g2 = NetworkIcons.smooth(g);
            int w = getWidth();
            int h = getHeight();
            double size = Math.min(w, h) - 4;
            double x = (w - size) / 2;
            double y = (h - size) / 2;
            BatteryIcons.paintBattery(g2, x, y, size, s.percent(), !s.onBattery(), theme.textActive(),
                    theme.sunkenBg());
            g2.dispose();
        }
    }
}
