package org.datazuul.amadeos.style;

import java.util.Arrays;

import org.datazuul.amadeos.input.InputApplier;

/**
 * Entry point of the amadeos-style jar: {@code amadeos-style} opens the Style Manager, {@code amadeos-style
 * input} applies the keyboard, mouse and bell settings (started at login by
 * {@code /etc/amadeos/autostart.d/10-amadeos-input} and on Apply in the Style Manager).
 */
public final class StyleLauncher {
    private StyleLauncher() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";
        String[] rest = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : args;
        switch (mode) {
            case "input" -> InputApplier.main(rest);
            // "settings" and "config" are the names used before the Style Manager got its CDE name.
            case "", "style", "settings", "config" -> StyleManager.main(rest);
            default -> {
                System.err.println("Usage: amadeos-style [input]");
                System.exit(2);
            }
        }
    }
}
