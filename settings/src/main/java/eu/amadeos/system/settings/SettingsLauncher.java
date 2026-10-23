package eu.amadeos.system.settings;

import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.input.InputApplier;

import java.util.Arrays;

/**
 * Entry point of the amadeos-settings jar: {@code amadeos-settings} opens the Settings, {@code amadeos-settings
 * backdrop} (or another control) opens them at that control, {@code amadeos-settings input} applies the
 * keyboard, mouse and bell settings (started at login by {@code /etc/amadeos/autostart.d/10-amadeos-input} and
 * on Apply in the Settings), {@code amadeos-settings display-apply} sets the saved monitor layout (started at
 * login by {@code /etc/amadeos/autostart.d/15-amadeos-display}). {@code amadeos-style}, the name of the Settings,
 * still works.
 */
public final class SettingsLauncher {
    private SettingsLauncher() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";
        String[] rest = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : args;
        switch (mode) {
            case "input" -> InputApplier.main(rest);
            // "display" opens the Display control; this sets the saved monitor layout at login.
            case "display-apply" -> DisplayApplier.main(rest);
            // "style", "settings" and "config" are names used before.
            case "", "style", "settings", "config" -> SettingsWindow.main(rest);
            default -> {
                if (isControl(mode)) {
                    SettingsWindow.main(args);
                } else {
                    System.err.println("Usage: amadeos-settings [input | color | font | backdrop | workspaces | panel"
                            + " | keyboard | mouse | beep | display | display-apply | screen | window | clipboard | startup | passwords | updates"
                            + " | language]");
                    System.exit(2);
                }
            }
        }
    }

    private static boolean isControl(String name) {
        return Arrays.stream(SettingsCategory.values()).anyMatch(c -> c.name().equalsIgnoreCase(name));
    }
}
