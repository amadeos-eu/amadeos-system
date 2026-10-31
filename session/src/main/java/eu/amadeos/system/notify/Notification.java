package eu.amadeos.system.notify;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;

/**
 * A notification as the daemon keeps it.
 *
 * @param id           the id the sender got from {@code Notify}
 * @param appName      the sender's name ({@code app_name}, else the desktop entry's name)
 * @param programKey   the key of the program in the settings ({@link NotificationSettings#programKey})
 * @param desktopEntry the sender's desktop entry without {@code .desktop}, or empty
 * @param icon         an icon name or path: the image-path hint, {@code app_icon} or the desktop entry's icon
 * @param image        the image-data hint, or null; not kept in the history file
 * @param summary      the bold first line
 * @param body         the text, with the markup the specification allows
 * @param actions      the buttons, {@code default} (the click on the body) included
 * @param timeoutMs    {@code expire_timeout}: -1 the user's setting, 0 never
 * @param transientHint not kept in the history
 * @param resident     stays open after an action was chosen
 * @param sender       the unique bus name of the program that sent it, or empty
 */
record Notification(long id, String appName, String programKey, String desktopEntry, String icon, BufferedImage image,
                    String summary, String body, List<Action> actions, Urgency urgency, String category, int timeoutMs,
                    boolean transientHint, boolean resident, boolean suppressSound, Instant time, String sender) {

    static final String DEFAULT_ACTION = "default";

    record Action(String key, String label) {
    }

    enum Urgency {
        LOW, NORMAL, CRITICAL;

        static Urgency of(int value) {
            return value <= 0 ? LOW : value >= 2 ? CRITICAL : NORMAL;
        }
    }

    Notification {
        appName = appName == null ? "" : appName;
        programKey = programKey == null ? "" : programKey;
        desktopEntry = desktopEntry == null ? "" : desktopEntry;
        icon = icon == null ? "" : icon;
        summary = summary == null ? "" : summary;
        body = body == null ? "" : body;
        actions = actions == null ? List.of() : List.copyOf(actions);
        urgency = urgency == null ? Urgency.NORMAL : urgency;
        category = category == null ? "" : category;
        sender = sender == null ? "" : sender;
    }

    boolean critical() {
        return urgency == Urgency.CRITICAL;
    }

    boolean hasDefaultAction() {
        return actions.stream().anyMatch(a -> a.key().equals(DEFAULT_ACTION));
    }

    /** The actions that get a button: all but {@code default}. */
    List<Action> buttons() {
        return actions.stream().filter(a -> !a.key().equals(DEFAULT_ACTION)).toList();
    }

    /** The sender's actions as the spec's list: key, label, key, label, ... */
    static List<Action> actions(List<String> keysAndLabels) {
        java.util.ArrayList<Action> out = new java.util.ArrayList<>();
        if (keysAndLabels == null) return out;
        for (int i = 0; i + 1 < keysAndLabels.size(); i += 2) {
            out.add(new Action(keysAndLabels.get(i), keysAndLabels.get(i + 1)));
        }
        return out;
    }

    /** The same notification with a new id, e.g. one from an earlier session in the history. */
    Notification withId(long newId) {
        return new Notification(newId, appName, programKey, desktopEntry, icon, image, summary, body, actions, urgency,
                category, timeoutMs, transientHint, resident, suppressSound, time, sender);
    }
}
