package org.datazuul.dazeos.wm;

import java.util.Arrays;

public final class DazeOsLauncher {
    public static void main(String[] args) {
        String mode = (args.length > 0) ? args[0] : System.getenv("DAZEOS_MODE");
        String[] subArgs = (args.length > 0) ? Arrays.copyOfRange(args, 1, args.length) : args;

        if ("wm".equalsIgnoreCase(mode)) {
            DazeWindowManagerMain.main(subArgs);
        } else if ("settings".equalsIgnoreCase(mode) || "config".equalsIgnoreCase(mode)) {
            org.datazuul.dazeos.wm.settings.DazeSettingsApplication.main(subArgs);
        } else {
            // Default desktop shell: CDE Front Panel dock ("panel", "desktop", "cde", "demo", or default)
            org.datazuul.dazeos.wm.panel.DazeDesktopPanel.main(subArgs);
        }
    }
}

