package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.system.taskmanager.model.ProgramRow;
import eu.amadeos.system.taskmanager.model.Sample;
import eu.amadeos.system.taskmanager.model.Sampler;
import eu.amadeos.system.taskmanager.x.WindowInfo;
import eu.amadeos.system.taskmanager.x.Windows;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Samples {@code /proc} and the window list on one background thread at the chosen speed and hands each
 * {@link Snapshot} to the listeners on the event dispatch thread. Everything that talks to the X server
 * ({@link Windows}) runs on that thread too, through {@link #onWorker}.
 */
public final class Monitor {
    private static final Logger LOGGER = Logger.getLogger(Monitor.class.getName());

    private final Sampler sampler;
    private final Windows windows;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "taskmanager-monitor");
        t.setDaemon(true);
        return t;
    });
    private final List<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> ticks;
    private volatile Snapshot last;

    public Monitor(Sampler sampler, Windows windows) {
        this.sampler = sampler;
        this.windows = windows;
    }

    public Sampler sampler() {
        return sampler;
    }

    public void addListener(Consumer<Snapshot> listener) {
        listeners.add(listener);
    }

    /** The latest snapshot, null before the first. */
    public Snapshot last() {
        return last;
    }

    /** Samples every {@code seconds} seconds; 0 pauses. */
    public synchronized void setInterval(int seconds) {
        if (ticks != null) ticks.cancel(false);
        ticks = null;
        if (seconds > 0) ticks = worker.scheduleAtFixedRate(this::tick, 0, seconds, TimeUnit.SECONDS);
    }

    /** One sample now, whatever the speed. */
    public void refresh() {
        worker.execute(this::tick);
    }

    /** Runs work that uses the window list (switching to a window) on the monitor's thread. */
    public void onWorker(Consumer<Windows> action) {
        worker.execute(() -> {
            try {
                action.accept(windows);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Window action failed", e);
            }
        });
    }

    private void tick() {
        try {
            Sample sample = sampler.sample();
            List<WindowInfo> list = windows.list();
            Snapshot snapshot = new Snapshot(sample, ProgramRow.of(list, sample.processes()), list,
                    windows.workspaceNames());
            last = snapshot;
            SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.accept(snapshot)));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Sampling failed", e);
        }
    }

    public void shutdown() {
        worker.shutdownNow();
        windows.close();
    }
}
