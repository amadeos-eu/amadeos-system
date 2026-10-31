package eu.amadeos.system.notify;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What the notification daemon decides, without D-Bus and windows, so that it can be tested: which id a
 * notification gets, whether it pops up, goes into the history only or is dropped, what Do not disturb holds back,
 * what the bell counts, and which signals go back to the sender. All methods are synchronized; the screen and the
 * bus are behind {@link Screen} and {@link Signals}.
 *
 * <p>A pop-up that times out leaves its notification open in the history, so that its actions still work from
 * there while the sender runs. Closing it (the × of the pop-up or of the history, Clear all) or choosing an action
 * closes it for the sender.
 */
final class NotificationCore {
    /** The windows: pop-ups and the history; implementations hand the work to the event dispatch thread. */
    interface Screen {
        /** Shows the pop-up for {@code timeoutMs} milliseconds (0: until closed), or updates the one with its id. */
        void popup(Notification n, long timeoutMs);

        /** Takes the pop-up away, if it is shown. */
        void unpop(long id);

        /** The history changed (new, seen, removed); the history window shows it again. */
        void historyChanged();

        /** Whether the active window is full screen just now. */
        boolean fullscreen();

        /** Whether the screen is locked just now: nothing pops up over the lock. */
        boolean locked();

        /** The beep for a new pop-up. */
        void beep();

        /** Starts the program of the desktop entry (a click in the history after the sender has quit). */
        void launch(String desktopEntry);
    }

    /** The signals of org.freedesktop.Notifications and of the bell's interface. */
    interface Signals {
        void closed(long id, int reason);

        void actionInvoked(long id, String key);

        void stateChanged(int unseen, boolean doNotDisturb);
    }

    static final int EXPIRED = 1;
    static final int DISMISSED = 2;
    static final int CLOSED_BY_CALL = 3;
    static final int UNDEFINED = 4;

    private final Supplier<NotificationSettings> settings;
    private final Consumer<NotificationSettings> saveSettings;
    private final Screen screen;
    private final Signals signals;
    private final Clock clock;
    private final NotificationHistory history;
    private final Runnable historyChanged;
    /** Open notifications that are not in the history (transient ones). */
    private final Map<Long, Notification> transients = new LinkedHashMap<>();
    /** Critical notifications that came while the screen was locked; they pop up after the unlock. */
    private final List<Long> heldCritical = new ArrayList<>();
    private long lastId;
    private int lastUnseen = -1;
    private boolean lastDnd;

    /**
     * @param saveSettings writes the settings: Do not disturb from the bell, a new program's name
     * @param historyChanged saves the history (the daemon does it on its worker)
     */
    NotificationCore(Supplier<NotificationSettings> settings, Consumer<NotificationSettings> saveSettings,
                     Screen screen, Signals signals, Clock clock, NotificationHistory history, Runnable historyChanged) {
        this.settings = settings;
        this.saveSettings = saveSettings;
        this.screen = screen;
        this.signals = signals;
        this.clock = clock;
        this.history = history;
        this.historyChanged = historyChanged;
        this.lastId = history.highestId();
    }

    /** {@code Notify}: returns the id. */
    synchronized long notify(Notification incoming, long replacesId) {
        long id = replacesId != 0 && (history.contains(replacesId) || transients.containsKey(replacesId))
                ? replacesId : nextId();
        Notification n = incoming.withId(id);
        NotificationSettings s = settings.get();
        rememberProgram(s, n);
        NotificationSettings.Mode mode = s.mode(n.programKey());
        if (mode == NotificationSettings.Mode.OFF) {
            signals.closed(id, UNDEFINED);
            return id;
        }
        boolean replacing = id == replacesId;
        if (n.transientHint()) {
            transients.put(id, n);
        } else {
            history.add(n, clock.instant());
            if (replacing) history.update(id, e -> e.seen(true));
            historyChanged();
        }
        boolean dnd = doNotDisturb(s);
        boolean pop = mode == NotificationSettings.Mode.POPUP && (n.critical() || !dnd);
        if (pop && screen.locked()) {
            if (n.critical()) heldCritical.add(id);
            pop = false;
        }
        if (pop) {
            screen.popup(n, timeout(n, s));
            if (s.beep() && !n.suppressSound() && !replacing) screen.beep();
        } else if (n.transientHint()) {
            // Nobody will see it: gone at once.
            transients.remove(id);
            signals.closed(id, EXPIRED);
        }
        state();
        return id;
    }

    /** How long the pop-up stays: critical ones and {@code expire_timeout} 0 until closed. */
    static long timeout(Notification n, NotificationSettings s) {
        if (n.critical() || n.timeoutMs() == 0) return 0;
        return n.timeoutMs() > 0 ? n.timeoutMs() : s.seconds() * 1000L;
    }

    /** The pop-up's time is up. Transient notifications end; the others stay open in the history. */
    synchronized void expired(long id) {
        screen.unpop(id);
        if (transients.remove(id) != null) signals.closed(id, EXPIRED);
    }

    /** {@code CloseNotification}: the sender takes it back, from the screen and from the history. */
    synchronized void closeByCall(long id) {
        screen.unpop(id);
        heldCritical.remove(id);
        boolean known = transients.remove(id) != null | history.remove(id);
        if (known) {
            historyChanged();
            signals.closed(id, CLOSED_BY_CALL);
            state();
        }
    }

    /** The × of a pop-up: seen, and closed for the sender; it stays in the history. */
    synchronized void dismiss(long id) {
        screen.unpop(id);
        if (transients.remove(id) != null) {
            signals.closed(id, DISMISSED);
            return;
        }
        NotificationHistory.Entry e = history.find(id);
        if (e == null) return;
        history.update(id, x -> x.seen(true).closed());
        if (e.open()) signals.closed(id, DISMISSED);
        historyChanged();
        state();
    }

    /** A button of a pop-up, or a click on its body ({@code default}). */
    synchronized void invoke(long id, String key) {
        Notification n = transients.containsKey(id) ? transients.get(id)
                : history.find(id) != null ? history.find(id).notification() : null;
        if (n == null) return;
        boolean open = (transients.containsKey(id) || history.find(id).open()) && n.senderAlive();
        boolean known = n.actions().stream().anyMatch(a -> a.key().equals(key));
        if (open && known) {
            signals.actionInvoked(id, key);
        } else if (Notification.DEFAULT_ACTION.equals(key) && !n.desktopEntry().isEmpty()) {
            screen.launch(n.desktopEntry());
        }
        if (n.resident() && open) {
            screen.unpop(id);
            history.update(id, x -> x.seen(true));
        } else {
            screen.unpop(id);
            if (transients.remove(id) != null) {
                signals.closed(id, DISMISSED);
            } else {
                history.update(id, x -> x.seen(true).closed());
                if (open) signals.closed(id, DISMISSED);
            }
        }
        historyChanged();
        state();
    }

    /** A click on an entry of the history window: its default action, or start its program. */
    synchronized void open(long id) {
        invoke(id, Notification.DEFAULT_ACTION);
    }

    /** The × of an entry in the history window. */
    synchronized void remove(long id) {
        NotificationHistory.Entry e = history.find(id);
        if (e == null) return;
        screen.unpop(id);
        history.remove(id);
        if (e.open()) signals.closed(id, DISMISSED);
        historyChanged();
        state();
    }

    synchronized void clearAll() {
        for (NotificationHistory.Entry e : history.entries()) {
            screen.unpop(e.notification().id());
            if (e.open()) signals.closed(e.notification().id(), DISMISSED);
        }
        history.clear();
        heldCritical.clear();
        historyChanged();
        state();
    }

    /** The history window was shown: everything in it counts as seen. */
    synchronized void historyShown() {
        if (history.unseen() == 0) return;
        history.markAllSeen();
        historyChanged();
        state();
    }

    /** The sender left the bus: its notifications can no longer act. */
    synchronized void senderGone(String sender) {
        if (sender.isEmpty()) return;
        history.senderGone(sender);
        transients.values().removeIf(n -> {
            if (!n.sender().equals(sender)) return false;
            screen.unpop(n.id());
            return true;
        });
    }

    /**
     * Do not disturb from the bell or the history window: {@link NotificationSettings#DND_OFF}, {@code DND_ON},
     * {@link NotificationServer#TOMORROW} or epoch seconds.
     */
    synchronized void setDoNotDisturb(long until) {
        NotificationSettings s = settings.get();
        long end = until == NotificationServer.TOMORROW ? s.tomorrowMorning(clock.instant(), clock.getZone()) : until;
        saveSettings.accept(s.withDndUntil(end));
        state();
    }

    /** Every few seconds and after the settings changed: the lock, the schedule, full screen windows. */
    synchronized void tick() {
        if (!heldCritical.isEmpty() && !screen.locked()) {
            NotificationSettings s = settings.get();
            for (long id : List.copyOf(heldCritical)) {
                NotificationHistory.Entry e = history.find(id);
                if (e != null && e.open()) screen.popup(e.notification(), timeout(e.notification(), s));
            }
            heldCritical.clear();
        }
        state();
    }

    synchronized List<NotificationHistory.Entry> entries() {
        return history.entries();
    }

    synchronized int unseen() {
        return history.unseen();
    }

    synchronized boolean doNotDisturb() {
        return doNotDisturb(settings.get());
    }

    private boolean doNotDisturb(NotificationSettings s) {
        return s.doNotDisturb(clock.instant(), clock.getZone(), screen.fullscreen());
    }

    /** Sends StateChanged when the count or Do not disturb changed. */
    private void state() {
        int unseen = history.unseen();
        boolean dnd = doNotDisturb(settings.get());
        if (unseen == lastUnseen && dnd == lastDnd) return;
        lastUnseen = unseen;
        lastDnd = dnd;
        signals.stateChanged(unseen, dnd);
    }

    private void historyChanged() {
        screen.historyChanged();
        historyChanged.run();
    }

    /** Puts a program that sends for the first time, or under a new name, into the settings for the Settings page. */
    private void rememberProgram(NotificationSettings s, Notification n) {
        String key = n.programKey();
        if (key.isEmpty() || n.appName().isEmpty() || n.appName().equals(s.names().get(key))) return;
        saveSettings.accept(s.withProgram(key, s.programs().get(key), n.appName()));
    }

    private long nextId() {
        lastId = lastId >= 0xFFFF_FFFFL ? 1 : lastId + 1;
        return lastId;
    }
}
