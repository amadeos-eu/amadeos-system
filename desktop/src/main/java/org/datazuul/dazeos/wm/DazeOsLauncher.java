package org.datazuul.dazeos.wm;

import java.util.Arrays;

public final class DazeOsLauncher {
    public static void main(String[] args) {
        String mode = (args.length > 0) ? args[0] : System.getenv("DAZEOS_MODE");
        String[] subArgs = (args.length > 0) ? Arrays.copyOfRange(args, 1, args.length) : args;

        if ("dm".equalsIgnoreCase(mode)) {
            org.datazuul.dazeos.dm.DazeDisplayManager.main(subArgs);
        } else if ("greeter".equalsIgnoreCase(mode)) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                new org.datazuul.dazeos.dm.ui.DazeLoginGreeter(
                        (u, p, s) -> System.out.println("Greeter test login: user=" + u + ", session=" + s),
                        a -> System.out.println("Greeter power action: " + a)
                ).setVisible(true);
            });
        } else if ("wm".equalsIgnoreCase(mode)) {
            DazeWindowManagerMain.main(subArgs);
        } else if ("settings".equalsIgnoreCase(mode) || "config".equalsIgnoreCase(mode)) {
            org.datazuul.dazeos.wm.settings.DazeSettingsApplication.main(subArgs);
        } else {
            // Default desktop shell: CDE Front Panel dock ("panel", "desktop", "cde", "demo", or default)
            org.datazuul.dazeos.wm.panel.DazeDesktopPanel.main(subArgs);
        }
    }
}

