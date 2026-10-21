package eu.amadeos.system.dm.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The keyboard layouts the greeter offers: the XKB layouts of {@code evdev.lst}, and the layout of the
 * computer from {@code /etc/default/keyboard} (written by the installer, {@code de} on the live image),
 * which the greeter starts with.
 */
public final class KeyboardLayouts {
    public static final KeyboardLayouts SYSTEM = new KeyboardLayouts(Path.of("/"));
    static final String EVDEV_LIST = "usr/share/X11/xkb/rules/evdev.lst";
    static final String DEFAULT_KEYBOARD = "etc/default/keyboard";
    private static final Logger LOGGER = Logger.getLogger(KeyboardLayouts.class.getName());
    private static final Pattern LIST_LINE = Pattern.compile("^\\s+([a-z]{2,8})\\s+(.+?)\\s*$");
    private static final Pattern SETTING = Pattern.compile("^\\s*(XKBLAYOUT|XKBVARIANT)\\s*=\\s*\"?([^\"#]*)\"?.*$");

    /** A layout: the XKB code and its name, e.g. {@code de} and {@code German}; shown as "German (de)". */
    public record Layout(String code, String name) {
        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    /** The layout and variant of the computer, e.g. {@code de} and {@code nodeadkeys}. */
    public record SystemLayout(String code, String variant) {
    }

    private final Path root;

    /** @param root the directory that stands for {@code /}; tests use a temporary one */
    KeyboardLayouts(Path root) {
        this.root = root;
    }

    /** All layouts, in the order of {@code evdev.lst}; a short built-in list when the file is missing. */
    public List<Layout> list() {
        Path file = root.resolve(EVDEV_LIST);
        try {
            if (Files.isReadable(file)) {
                List<Layout> layouts = parse(Files.readString(file, StandardCharsets.UTF_8));
                if (!layouts.isEmpty()) {
                    return layouts;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Cannot read " + file, e);
        }
        return List.of(new Layout("us", "English (US)"), new Layout("gb", "English (UK)"), new Layout("de", "German"),
                new Layout("at", "German (Austria)"), new Layout("ch", "German (Switzerland)"), new Layout("fr", "French"),
                new Layout("be", "Belgian"), new Layout("es", "Spanish"), new Layout("it", "Italian"),
                new Layout("pt", "Portuguese"), new Layout("nl", "Dutch"), new Layout("dk", "Danish"),
                new Layout("se", "Swedish"), new Layout("no", "Norwegian"), new Layout("fi", "Finnish"),
                new Layout("pl", "Polish"), new Layout("cz", "Czech"));
    }

    /** The {@code ! layout} section of evdev.lst: {@code  de              German}. */
    static List<Layout> parse(String text) {
        List<Layout> layouts = new ArrayList<>();
        boolean inSection = false;
        for (String line : text.split("\n")) {
            if (line.startsWith("!")) {
                inSection = line.strip().equals("! layout");
                continue;
            }
            if (!inSection) {
                continue;
            }
            Matcher matcher = LIST_LINE.matcher(line);
            if (matcher.matches()) {
                layouts.add(new Layout(matcher.group(1), matcher.group(2)));
            }
        }
        return layouts;
    }

    /**
     * The first layout of {@code XKBLAYOUT} in {@code /etc/default/keyboard} with its variant; {@code de}
     * when the file is missing or names none, as on the live image.
     */
    public SystemLayout system() {
        Path file = root.resolve(DEFAULT_KEYBOARD);
        try {
            if (Files.isReadable(file)) {
                return parseDefaults(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Cannot read " + file, e);
        }
        return new SystemLayout("de", "");
    }

    static SystemLayout parseDefaults(String text) {
        String layout = "";
        String variant = "";
        for (String line : text.split("\n")) {
            Matcher matcher = SETTING.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            if (matcher.group(1).equals("XKBLAYOUT")) {
                layout = first(matcher.group(2));
            } else {
                variant = first(matcher.group(2));
            }
        }
        return layout.isEmpty() ? new SystemLayout("de", "") : new SystemLayout(layout, variant);
    }

    private static String first(String list) {
        String value = list.strip();
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).strip() : value;
    }

    /** The entry of the list with this code, if there is one. */
    public static Optional<Layout> find(List<Layout> layouts, String code) {
        return layouts.stream().filter(l -> l.code().equals(code)).findFirst();
    }

    /** Switches the X server to the layout (with the variant, if not blank); failures are logged. */
    public static void apply(String layout, String variant) {
        List<String> command = new ArrayList<>(List.of("setxkbmap", "-layout", layout));
        if (variant != null && !variant.isBlank()) {
            command.addAll(List.of("-variant", variant));
        }
        try {
            new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "setxkbmap failed", e);
        }
    }
}
