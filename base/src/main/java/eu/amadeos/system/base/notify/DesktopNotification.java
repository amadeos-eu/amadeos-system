package eu.amadeos.system.base.notify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A notification the desktop itself sends (the Front Panel's battery and updates cells) through
 * {@link NotificationClient}.
 *
 * @param appName      the sender as the history shows it, e.g. "Battery"
 * @param icon         an icon name of the icon theme or an absolute path; empty for none
 * @param summary      the bold first line
 * @param body         the text under it; may contain {@code <b>}, {@code <i>} and {@code <a href>}
 * @param urgency      {@link Urgency#CRITICAL} stays until closed and shows through Do not disturb
 * @param category     the spec's category, e.g. {@code device.error}; empty for none
 * @param desktopEntry the desktop entry of the program behind it, without {@code .desktop}; empty for none
 * @param actions      action key to button label, in order; the key {@code default} is the click on the body
 * @param onAction     called with the key of the action the user chose, on a D-Bus thread
 * @param replaces     the id of an earlier notification this one replaces, 0 for none
 */
public record DesktopNotification(String appName, String icon, String summary, String body, Urgency urgency,
                                  String category, String desktopEntry, Map<String, String> actions,
                                  Consumer<String> onAction, long replaces) {

    public enum Urgency {
        LOW, NORMAL, CRITICAL
    }

    public DesktopNotification {
        Objects.requireNonNull(summary, "summary");
        appName = appName == null ? "" : appName;
        icon = icon == null ? "" : icon;
        body = body == null ? "" : body;
        urgency = urgency == null ? Urgency.NORMAL : urgency;
        category = category == null ? "" : category;
        desktopEntry = desktopEntry == null ? "" : desktopEntry;
        actions = actions == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(actions));
        onAction = onAction == null ? key -> { } : onAction;
    }

    public static DesktopNotification of(String appName, String icon, String summary, String body) {
        return new DesktopNotification(appName, icon, summary, body, Urgency.NORMAL, "", "", Map.of(), null, 0);
    }

    public DesktopNotification withUrgency(Urgency u) {
        return new DesktopNotification(appName, icon, summary, body, u, category, desktopEntry, actions, onAction, replaces);
    }

    public DesktopNotification withCategory(String c) {
        return new DesktopNotification(appName, icon, summary, body, urgency, c, desktopEntry, actions, onAction, replaces);
    }

    public DesktopNotification withDesktopEntry(String entry) {
        return new DesktopNotification(appName, icon, summary, body, urgency, category, entry, actions, onAction, replaces);
    }

    public DesktopNotification withReplaces(long id) {
        return new DesktopNotification(appName, icon, summary, body, urgency, category, desktopEntry, actions, onAction, id);
    }

    /** Adds a button; {@code default} is the click on the notification itself and has no button. */
    public DesktopNotification withAction(String key, String label) {
        Map<String, String> more = new LinkedHashMap<>(actions);
        more.put(key, label);
        return new DesktopNotification(appName, icon, summary, body, urgency, category, desktopEntry, more, onAction, replaces);
    }

    public DesktopNotification onAction(Consumer<String> handler) {
        return new DesktopNotification(appName, icon, summary, body, urgency, category, desktopEntry, actions, handler, replaces);
    }
}
