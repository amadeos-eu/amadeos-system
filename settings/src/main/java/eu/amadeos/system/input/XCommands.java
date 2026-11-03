package eu.amadeos.system.input;

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

    // --8<-- [start:keyboard]
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
    // --8<-- [end:keyboard]

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

    // --8<-- [start:accessibility]
    /**
     * The keyboard aids through {@code xkbset} (XKB's AccessX controls), kept from expiring after two idle minutes,
     * and the mouse pointer through the Xcursor resources, which every program reads when it connects to the X
     * server. {@code xsetroot} puts the new pointer on the desktop at once; windows that are already open keep
     * theirs. The classic pointer names a theme that does not exist, so libXcursor falls back to X's own cursors.
     */
    public static List<Command> accessibility(AccessibilitySettings a) {
        List<String> keys = new ArrayList<>(List.of("xkbset"));
        if (a.stickyKeys()) {
            keys.addAll(List.of("sticky", a.stickyTwoKeys() ? "twokey" : "-twokey", a.stickyLock() ? "latchlock" : "-latchlock"));
        } else {
            keys.add("-sticky");
        }
        keys.addAll(a.slowKeys() ? List.of("slowkeys", Integer.toString(a.slowKeysMs())) : List.of("-slowkeys"));
        keys.addAll(a.bounceKeys() ? List.of("bouncekeys", Integer.toString(a.bounceKeysMs())) : List.of("-bouncekeys"));
        keys.add(a.mouseKeys() ? "mousekeys" : "-mousekeys");
        keys.add(a.gestures() ? "accessx" : "-accessx");
        keys.add(a.feedback() ? "feedback" : "-feedback");
        String theme = a.pointer() == AccessibilitySettings.Pointer.CLASSIC ? "amadeos-classic" : a.pointer().theme();
        return List.of(
                new Command(keys, null),
                Command.of("xkbset", "exp", "=sticky", "=slowkeys", "=bouncekeys", "=mousekeys", "=accessx"),
                new Command(List.of("xrdb", "-nocpp", "-merge"),
                        "Xcursor.theme: " + theme + "\nXcursor.size: " + a.pointerSize() + "\n"),
                Command.of("xsetroot", "-cursor_name", "left_ptr"));
    }
    // --8<-- [end:accessibility]

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
