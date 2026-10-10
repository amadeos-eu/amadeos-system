package org.datazuul.dazeos.wm;

import java.util.Arrays;

public final class DazeOsLauncher {
    public static void main(String[] args) {
        if (args.length > 0 && "wm".equalsIgnoreCase(args[0])) {
            DazeWindowManagerMain.main(Arrays.copyOfRange(args, 1, args.length));
        } else if (args.length > 0 && "demo".equalsIgnoreCase(args[0])) {
            SwingDemoApplication.main(Arrays.copyOfRange(args, 1, args.length));
        } else {
            String mode = System.getenv("DAZEOS_MODE");
            if ("wm".equalsIgnoreCase(mode)) {
                DazeWindowManagerMain.main(args);
            } else {
                SwingDemoApplication.main(args);
            }
        }
    }
}

