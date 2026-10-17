package org.datazuul.amadeos.input;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Translates {@link InputSettings} into the standard X11 tools from {@code x11-xserver-utils}:
 * {@code xset} (repeat, click, acceleration, bell), {@code xmodmap} (button order for left-handed use) and
 * {@code xrdb} (double-click time, read by X toolkits including AWT at start-up). Pure, so it can be tested.
 */
public final class XCommands {

    /** A command line and optional text for its standard input. */
    public record Command(List<String> args, String stdin) {
        public Command {
            args = List.copyOf(args);
        }

        static Command of(String... args) {
            return new Command(List.of(args), null);
        }

        @Override
        public String toString() {
            return String.join(" ", args) + (stdin == null ? "" : " <<< " + stdin.strip());
        }
    }

    private XCommands() {
    }

    public static List<Command> keyboard(InputSettings.Keyboard k) {
        List<Command> commands = new ArrayList<>();
        if (k.autoRepeat()) {
            commands.add(Command.of("xset", "r", "on"));
            commands.add(Command.of("xset", "r", "rate", Integer.toString(k.repeatDelayMs()),
                    Integer.toString(k.repeatRate())));
        } else {
            commands.add(Command.of("xset", "r", "off"));
        }
        commands.add(k.clickVolume() == 0 ? Command.of("xset", "c", "off")
                : Command.of("xset", "c", Integer.toString(k.clickVolume())));
        return commands;
    }

    /**
     * @param pointerButtons number of buttons the pointer reports ({@code xmodmap -pp}); at least 3 are assumed
     */
    public static List<Command> mouse(InputSettings.Mouse m, int pointerButtons) {
        List<Command> commands = new ArrayList<>();
        commands.add(Command.of("xset", "m", m.acceleration() + "/1", Integer.toString(m.threshold())));
        commands.add(Command.of("xmodmap", "-e", "pointer = " + buttonMap(m.leftHanded(), pointerButtons)));
        commands.add(new Command(List.of("xrdb", "-merge"), "*multiClickTime: " + m.doubleClickMs() + "\n"));
        return commands;
    }

    /** "1 2 3 4 5 ..." or, left-handed, "3 2 1 4 5 ...": only the primary and secondary button swap. */
    static String buttonMap(boolean leftHanded, int pointerButtons) {
        int buttons = Math.max(3, pointerButtons);
        StringJoiner map = new StringJoiner(" ");
        for (int b = 1; b <= buttons; b++) {
            int mapped = !leftHanded ? b : b == 1 ? 3 : b == 3 ? 1 : b;
            map.add(Integer.toString(mapped));
        }
        return map.toString();
    }

    public static List<Command> beep(InputSettings.Beep b) {
        return List.of(Command.of("xset", "b", Integer.toString(b.volume()), Integer.toString(b.pitchHz()),
                Integer.toString(b.durationMs())));
    }

    public static List<Command> all(InputSettings s, int pointerButtons) {
        List<Command> commands = new ArrayList<>(keyboard(s.keyboard()));
        commands.addAll(mouse(s.mouse(), pointerButtons));
        commands.addAll(beep(s.beep()));
        return commands;
    }

    /** Parses "There are 9 pointer buttons defined." from {@code xmodmap -pp}; 3 if not found. */
    static int pointerButtons(String xmodmapOutput) {
        if (xmodmapOutput != null) {
            var matcher = java.util.regex.Pattern.compile("There are (\\d+) pointer buttons").matcher(xmodmapOutput);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        }
        return 3;
    }
}
