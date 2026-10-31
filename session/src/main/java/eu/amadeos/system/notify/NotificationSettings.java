package eu.amadeos.system.notify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The "Notifications" settings of the Settings, in {@code ~/.config/amadeos/notifications.conf}.
 *
 * @param corner        where the pop-ups appear
 * @param seconds       how long a pop-up stays when the program does not say
 * @param beep          play the beep for each new pop-up
 * @param dndUntil      Do not disturb by hand: {@link #DND_OFF}, {@link #DND_ON} or the end in epoch seconds
 * @param schedule      Do not disturb every day from {@code from} to {@code to}
 * @param fullscreen    Do not disturb while the active window is full screen
 * @param programs      what each program may do, by its key ({@link #programKey}); programs not listed: pop-ups
 * @param names         the name to show for each program key in the Settings, as the program last called itself
 */
public record NotificationSettings(Corner corner, int seconds, boolean beep, long dndUntil, boolean schedule,
                                   LocalTime from, LocalTime to, boolean fullscreen, Map<String, Mode> programs,
                                   Map<String, String> names) {

    public static final long DND_OFF = 0;
    public static final long DND_ON = -1;
    public static final int MIN_SECONDS = 2;
    public static final int MAX_SECONDS = 60;

    public static final NotificationSettings DEFAULTS = new NotificationSettings(Corner.BOTTOM_RIGHT, 6, false, DND_OFF,
            false, LocalTime.of(22, 0), LocalTime.of(7, 0), true, Map.of(), Map.of());

    /** The four corners of the screen; at the bottom the pop-ups stay above the Front Panel. */
    public enum Corner {
        BOTTOM_RIGHT, TOP_RIGHT, BOTTOM_LEFT, TOP_LEFT;

        public boolean bottom() {
            return this == BOTTOM_RIGHT || this == BOTTOM_LEFT;
        }

        public boolean right() {
            return this == BOTTOM_RIGHT || this == TOP_RIGHT;
        }
    }

    /** What a program's notifications do. */
    public enum Mode {
        /** Pop-up and history. */
        POPUP,
        /** Only into the history, the bell counts them. */
        HISTORY,
        /** Dropped. */
        OFF
    }

    public NotificationSettings {
        if (corner == null) corner = Corner.BOTTOM_RIGHT;
        seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
        if (from == null) from = LocalTime.of(22, 0);
        if (to == null) to = LocalTime.of(7, 0);
        programs = programs == null ? Map.of() : Map.copyOf(programs);
        names = names == null ? Map.of() : Map.copyOf(names);
    }

    /** The key a program is known by: its desktop entry without {@code .desktop}, else its name, in lower case. */
    public static String programKey(String desktopEntry, String appName) {
        String key = desktopEntry != null && !desktopEntry.isBlank() ? desktopEntry : appName;
        if (key == null) return "";
        key = key.strip().toLowerCase(Locale.ROOT);
        return key.endsWith(".desktop") ? key.substring(0, key.length() - ".desktop".length()) : key;
    }

    public Mode mode(String programKey) {
        return programs.getOrDefault(programKey, Mode.POPUP);
    }

    /** Whether Do not disturb is on by hand at {@code now}. */
    public boolean manualDnd(Instant now) {
        return dndUntil == DND_ON || (dndUntil > 0 && now.getEpochSecond() < dndUntil);
    }

    /** Whether the schedule holds back pop-ups at {@code time}; the span may go past midnight. */
    public boolean scheduled(LocalTime time) {
        if (!schedule || from.equals(to)) return false;
        return from.isBefore(to) ? !time.isBefore(from) && time.isBefore(to)
                : !time.isBefore(from) || time.isBefore(to);
    }

    /** Whether Do not disturb is on at {@code now}, by hand, by the schedule or for a full screen window. */
    public boolean doNotDisturb(Instant now, ZoneId zone, boolean fullscreenWindow) {
        return manualDnd(now) || scheduled(LocalTime.ofInstant(now, zone)) || (fullscreen && fullscreenWindow);
    }

    /** "Until tomorrow morning": the next end of the schedule, or the next 07:00 without a schedule. */
    public long tomorrowMorning(Instant now, ZoneId zone) {
        LocalTime morning = schedule ? to : LocalTime.of(7, 0);
        LocalDateTime here = LocalDateTime.ofInstant(now, zone);
        LocalDate day = here.toLocalTime().isBefore(morning) ? here.toLocalDate() : here.toLocalDate().plusDays(1);
        return day.atTime(morning).atZone(zone).toEpochSecond();
    }

    public NotificationSettings withDndUntil(long until) {
        return new NotificationSettings(corner, seconds, beep, until, schedule, from, to, fullscreen, programs, names);
    }

    public NotificationSettings withProgram(String key, Mode mode, String name) {
        Map<String, Mode> p = new TreeMap<>(programs);
        if (mode == null) p.remove(key);
        else p.put(key, mode);
        Map<String, String> n = new TreeMap<>(names);
        if (name != null && !name.isBlank()) n.put(key, name);
        return new NotificationSettings(corner, seconds, beep, dndUntil, schedule, from, to, fullscreen, p, n);
    }
}
