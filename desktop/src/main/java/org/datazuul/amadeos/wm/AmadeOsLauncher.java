package org.datazuul.amadeos.wm;

import java.util.Arrays;

public final class AmadeOsLauncher {
    public static void main(String[] args) {
        String mode = (args.length > 0) ? args[0] : System.getenv("AMADEOS_MODE");
        String[] subArgs = (args.length > 0) ? Arrays.copyOfRange(args, 1, args.length) : args;

        if ("dm".equalsIgnoreCase(mode)) {
            org.datazuul.amadeos.dm.AmadeDisplayManager.main(subArgs);
        } else if ("session-worker".equalsIgnoreCase(mode)) {
            org.datazuul.amadeos.dm.session.SessionWorker.main(subArgs);
        } else if ("greeter".equalsIgnoreCase(mode)) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                new org.datazuul.amadeos.dm.ui.AmadeLoginGreeter(
                        (u, p, s) -> System.out.println("Greeter test login: user=" + u + ", session=" + s),
                        a -> System.out.println("Greeter power action: " + a)
                ).setVisible(true);
            });
        } else if ("wm".equalsIgnoreCase(mode)) {
            AmadeWindowManagerMain.main(subArgs);
        } else if ("settings".equalsIgnoreCase(mode) || "config".equalsIgnoreCase(mode)) {
            org.datazuul.amadeos.wm.settings.AmadeSettingsApplication.main(subArgs);
        } else {
            // Default desktop shell: CDE Front Panel dock ("panel", "desktop", "cde", "demo", or default)
            org.datazuul.amadeos.wm.panel.AmadeDesktopPanel.main(subArgs);
        }
    }
}

