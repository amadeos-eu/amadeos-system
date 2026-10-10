package org.datazuul.dazeos.wm;

import java.util.Arrays;

public final class DazeOsLauncher {
    public static void main(String[] args) {
        String mode = (args.length > 0) ? args[0] : System.getenv("DAZEOS_MODE");
        String[] subArgs = (args.length > 0) ? Arrays.copyOfRange(args, 1, args.length) : args;

        if ("wm".equalsIgnoreCase(mode)) {
            DazeWindowManagerMain.main(subArgs);
        } else if ("panel".equalsIgnoreCase(mode)) {
            org.datazuul.dazeos.wm.panel.DazeDesktopPanel.main(subArgs);
        } else if ("desktop".equalsIgnoreCase(mode)) {
            // Desktop session: start the Desktop Panel dock and initial Control Panel
            org.datazuul.dazeos.wm.panel.DazeDesktopPanel.main(subArgs);
            SwingDemoApplication.main(subArgs);
        } else if ("demo".equalsIgnoreCase(mode)) {
            // Control Panel application only
            SwingDemoApplication.main(subArgs);
        } else {
            SwingDemoApplication.main(subArgs);
        }
    }
}

