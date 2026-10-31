package eu.amadeos.system.base.power;

import java.util.Locale;

/**
 * What the computer does when the lid closes, the power button is pressed, the desktop has been idle long
 * enough or the battery runs low. Not every action fits every event: {@link #ASK} is only for the power button,
 * {@link #LOCK} and {@link #SCREEN_OFF} only for the lid.
 */
public enum PowerAction {
    /** Show the Exit dialog. */
    ASK,
    SUSPEND,
    HIBERNATE,
    LOCK,
    SCREEN_OFF,
    SHUTDOWN,
    NOTHING;

    /** The actions offered for closing the lid. */
    public static final PowerAction[] LID = {SUSPEND, HIBERNATE, LOCK, SCREEN_OFF, NOTHING};
    /** The actions offered for the power button. */
    public static final PowerAction[] POWER_KEY = {ASK, SUSPEND, HIBERNATE, SHUTDOWN, NOTHING};
    /** The actions offered for a nearly empty battery. */
    public static final PowerAction[] LOW_BATTERY = {HIBERNATE, SUSPEND, SHUTDOWN, NOTHING};

    /** The name in power.conf, e.g. {@code screen-off}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The action of a power.conf value, or the fallback for a missing or unknown one. */
    public static PowerAction parse(String value, PowerAction fallback) {
        if (value == null) return fallback;
        String name = value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (PowerAction action : values()) {
            if (action.name().equals(name)) return action;
        }
        return fallback;
    }
}
