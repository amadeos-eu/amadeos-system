package eu.amadeos.system.notify;

import eu.amadeos.commons.i18n.Messages;

import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The pop-ups on the screen, on the event dispatch thread. Each is a borderless window of type POPUP, which X11
 * maps override-redirect: the window manager neither frames it nor gives it the focus, so a pop-up never takes
 * the keyboard from the window the user types in. At most {@link #MAX_SHOWN} are shown, stacked from the chosen
 * corner, the newest nearest to it; more wait until there is room. A pop-up's time does not run while the mouse
 * is over it.
 */
final class PopupStack {
    static final int MAX_SHOWN = 4;
    static final int TICK_MS = 250;
    /** The time a pop-up still gets after the mouse has left it. */
    static final long AFTER_HOVER_MS = 2000;
    static final int GAP = 8;
    static final int MARGIN = 12;

    /** Where the pop-ups may go: the screen without the docks (the Front Panel). */
    interface Area {
        Rectangle bounds();
    }

    private final class Shown {
        final PopupCard card;
        final JWindow window;
        long remainingMs;
        boolean hovered;

        Shown(PopupCard card, JWindow window, long timeoutMs) {
            this.card = card;
            this.window = window;
            this.remainingMs = timeoutMs;
        }
    }

    private final Messages texts;
    private final PopupCard.Actions actions;
    private final java.util.function.LongConsumer expired;
    private final Supplier<NotificationSettings> settings;
    private final Supplier<Instant> now;
    private final Area area;
    private final Map<Long, Shown> shown = new LinkedHashMap<>();
    private final Map<Long, Notification> waiting = new LinkedHashMap<>();
    private final Map<Long, Long> waitingTimeouts = new LinkedHashMap<>();
    private final Timer timer;
    private int ticks;

    PopupStack(Messages texts, PopupCard.Actions actions, java.util.function.LongConsumer expired,
               Supplier<NotificationSettings> settings, Supplier<Instant> now, Area area) {
        this.texts = texts;
        this.actions = actions;
        this.expired = expired;
        this.settings = settings;
        this.now = now;
        this.area = area;
        this.timer = new Timer(TICK_MS, e -> tick());
    }

    /** Shows the notification, or puts the new content into its pop-up when it is shown already. */
    void show(Notification n, long timeoutMs) {
        Shown old = shown.get(n.id());
        if (old != null) {
            old.window.dispose();
            shown.put(n.id(), open(n, timeoutMs));
            layout();
            return;
        }
        if (shown.size() >= MAX_SHOWN) {
            waiting.put(n.id(), n);
            waitingTimeouts.put(n.id(), timeoutMs);
            return;
        }
        shown.put(n.id(), open(n, timeoutMs));
        layout();
        if (!timer.isRunning()) timer.start();
    }

    void hide(long id) {
        waiting.remove(id);
        waitingTimeouts.remove(id);
        Shown s = shown.remove(id);
        if (s == null) return;
        s.window.dispose();
        fill();
        layout();
    }

    /** Takes all pop-ups away, e.g. while the history window shows them anyway; they stay open in the history. */
    void hideAll() {
        waiting.clear();
        waitingTimeouts.clear();
        for (Shown s : shown.values()) s.window.dispose();
        shown.clear();
    }

    List<Long> shownIds() {
        return new ArrayList<>(shown.keySet());
    }

    List<Window> windows() {
        return shown.values().stream().map(s -> (Window) s.window).toList();
    }

    /** Places the pop-ups again, e.g. after the corner was changed in the Settings. */
    void layout() {
        if (shown.isEmpty()) return;
        NotificationSettings.Corner corner = settings.get().corner();
        Rectangle r = area.bounds();
        int y = corner.bottom() ? r.y + r.height - MARGIN : r.y + MARGIN;
        List<Shown> newestFirst = new ArrayList<>(shown.values());
        java.util.Collections.reverse(newestFirst);
        for (Shown s : newestFirst) {
            int w = s.window.getWidth();
            int h = s.window.getHeight();
            int x = corner.right() ? r.x + r.width - w - MARGIN : r.x + MARGIN;
            if (corner.bottom()) {
                y -= h;
                s.window.setLocation(x, y);
                y -= GAP;
            } else {
                s.window.setLocation(x, y);
                y += h + GAP;
            }
        }
    }

    private Shown open(Notification n, long timeoutMs) {
        PopupCard card = new PopupCard(n, texts, actions, now);
        JWindow w = new JWindow();
        w.setType(Window.Type.POPUP);
        w.setName("notification." + n.id());
        w.setFocusableWindowState(false);
        w.setAlwaysOnTop(true);
        w.setContentPane(card);
        w.pack();
        Shown s = new Shown(card, w, timeoutMs);
        // Place before showing, so that the pop-up does not flash in the top left corner.
        shown.put(n.id(), s);
        layout();
        if (!GraphicsEnvironment.isHeadless()) w.setVisible(true);
        return s;
    }

    private void fill() {
        while (shown.size() < MAX_SHOWN && !waiting.isEmpty()) {
            long id = waiting.keySet().iterator().next();
            Notification n = waiting.remove(id);
            long timeout = waitingTimeouts.remove(id);
            shown.put(id, open(n, timeout));
        }
    }

    private void tick() {
        if (shown.isEmpty()) {
            timer.stop();
            return;
        }
        Point pointer = pointer();
        List<Long> done = new ArrayList<>();
        for (Map.Entry<Long, Shown> e : shown.entrySet()) {
            Shown s = e.getValue();
            boolean over = pointer != null && s.window.isShowing() && s.window.getBounds().contains(pointer);
            if (over) {
                s.hovered = true;
                continue;
            }
            if (s.hovered) {
                s.hovered = false;
                if (s.remainingMs > 0) s.remainingMs = Math.max(s.remainingMs, AFTER_HOVER_MS);
            }
            if (s.remainingMs <= 0) continue; // until closed
            s.remainingMs -= TICK_MS;
            if (s.remainingMs <= 0) done.add(e.getKey());
        }
        for (long id : done) expired.accept(id);
        ticks++;
        // Every two seconds: above windows raised since (the Front Panel at login), and placed again should the
        // panel have appeared or changed its height.
        if (ticks % (2_000 / TICK_MS) == 0 && !shown.isEmpty()) {
            layout();
            for (Shown s : shown.values()) s.window.toFront();
        }
        // The ages ("2 min") once every half minute.
        if (ticks % (30_000 / TICK_MS) == 0) {
            for (Shown s : shown.values()) s.card.refreshAge(ZoneId.systemDefault());
        }
    }

    private static Point pointer() {
        try {
            PointerInfo info = MouseInfo.getPointerInfo();
            return info == null ? null : info.getLocation();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
