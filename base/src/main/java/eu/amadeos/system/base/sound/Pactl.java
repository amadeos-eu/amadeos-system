package eu.amadeos.system.base.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code pactl} command lines the desktop uses and parsers for their output. Pure, so it can be tested
 * without a sound server. pactl talks to PipeWire through pipewire-pulse; it runs with {@code LC_ALL=C}, so
 * the field names are English.
 */
public final class Pactl {
    /** Highest volume the panel sets; PipeWire allows more, which distorts. */
    public static final int MAX_VOLUME = 150;
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");
    private static final Pattern EVENT = Pattern.compile("Event '(\\w+)' on ([\\w-]+) #\\d+");

    private Pactl() {
    }

    public static List<String> info() {
        return List.of("pactl", "info");
    }

    public static List<String> sinks() {
        return List.of("pactl", "list", "sinks");
    }

    public static List<String> sources() {
        return List.of("pactl", "list", "sources");
    }

    public static List<String> sinkInputs() {
        return List.of("pactl", "list", "short", "sink-inputs");
    }

    public static List<String> sourceOutputs() {
        return List.of("pactl", "list", "short", "source-outputs");
    }

    public static List<String> subscribe() {
        return List.of("pactl", "subscribe");
    }

    public static List<String> setVolume(int percent) {
        int p = Math.max(0, Math.min(MAX_VOLUME, percent));
        return List.of("pactl", "set-sink-volume", "@DEFAULT_SINK@", p + "%");
    }

    public static List<String> setMuted(boolean muted) {
        return List.of("pactl", "set-sink-mute", "@DEFAULT_SINK@", muted ? "1" : "0");
    }

    public static List<String> setInputMuted(boolean muted) {
        return List.of("pactl", "set-source-mute", "@DEFAULT_SOURCE@", muted ? "1" : "0");
    }

    public static List<String> setDefaultSink(String name) {
        return List.of("pactl", "set-default-sink", name);
    }

    public static List<String> setDefaultSource(String name) {
        return List.of("pactl", "set-default-source", name);
    }

    public static List<String> moveSinkInput(String id, String sink) {
        return List.of("pactl", "move-sink-input", id, sink);
    }

    public static List<String> moveSourceOutput(String id, String source) {
        return List.of("pactl", "move-source-output", id, source);
    }

    /** The value of {@code Default Sink:} or {@code Default Source:} in {@code pactl info}, or "". */
    public static String parseDefault(String info, String field) {
        String prefix = field + ":";
        for (String line : info.split("\n")) {
            String l = line.strip();
            if (l.startsWith(prefix)) return l.substring(prefix.length()).strip();
        }
        return "";
    }

    // --8<-- [start:devices]
    /**
     * Devices of {@code pactl list sinks} or {@code pactl list sources}. The monitors, through which a
     * recording program can hear what an output plays, are left out of the sources.
     */
    public static List<AudioDevice> parseDevices(String list) {
        List<AudioDevice> devices = new ArrayList<>();
        String name = null;
        String description = null;
        int volume = 0;
        boolean muted = false;
        boolean monitor = false;
        for (String line : (list + "\nSink #end").split("\n")) {
            if (line.startsWith("Sink #") || line.startsWith("Source #")) {
                if (name != null && !monitor) devices.add(new AudioDevice(name, description, volume, muted));
                name = null;
                description = null;
                volume = 0;
                muted = false;
                monitor = false;
                continue;
            }
            // Only the fields of the device itself, one tab deep; Properties and Ports are indented further
            if (!line.startsWith("\t") || line.startsWith("\t\t")) continue;
            String l = line.strip();
            if (l.startsWith("Name:")) {
                name = value(l);
            } else if (l.startsWith("Description:")) {
                description = value(l);
            } else if (l.startsWith("Mute:")) {
                muted = value(l).equals("yes");
            } else if (l.startsWith("Volume:")) {
                volume = averagePercent(l);
            } else if (l.startsWith("Monitor of Sink:")) {
                monitor = !value(l).equals("n/a");
            }
        }
        return devices;
    }
    // --8<-- [end:devices]

    /** The mean of the per-channel percentages of a {@code Volume:} line, rounded. */
    static int averagePercent(String volumeLine) {
        Matcher m = PERCENT.matcher(volumeLine);
        int sum = 0;
        int channels = 0;
        while (m.find()) {
            sum += Integer.parseInt(m.group(1));
            channels++;
        }
        return channels == 0 ? 0 : Math.round((float) sum / channels);
    }

    /** The ids (first column) of {@code pactl list short sink-inputs} or {@code source-outputs}. */
    public static List<String> parseIds(String shortList) {
        List<String> ids = new ArrayList<>();
        for (String line : shortList.split("\n")) {
            String id = line.split("\t", 2)[0].strip();
            if (!id.isEmpty() && id.chars().allMatch(Character::isDigit)) ids.add(id);
        }
        return ids;
    }

    /**
     * Whether a line of {@code pactl subscribe} concerns what the panel shows: devices appearing, going or
     * changing volume, and the server's choice of default devices. Playing streams change all the time and
     * are left out.
     */
    public static boolean relevantEvent(String line) {
        Matcher m = EVENT.matcher(line);
        if (!m.find()) return false;
        String facility = m.group(2);
        return facility.equals("sink") || facility.equals("source") || facility.equals("server")
                || facility.equals("card");
    }

    /** The last line of pactl's error output, without the "Failure: " in front. */
    public static String errorMessage(String stderr) {
        String[] lines = stderr.strip().split("\n");
        String last = lines[lines.length - 1].strip();
        return last.startsWith("Failure: ") ? last.substring(9) : last;
    }

    private static String value(String line) {
        return line.substring(line.indexOf(':') + 1).strip();
    }
}
