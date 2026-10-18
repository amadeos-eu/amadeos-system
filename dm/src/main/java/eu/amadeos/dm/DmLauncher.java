package eu.amadeos.dm;

import java.util.Arrays;

import eu.amadeos.dm.session.SessionWorker;
import eu.amadeos.dm.ui.AmadeLoginGreeter;

/**
 * Entry point of the amadeos-dm jar: {@code amadeos-dm [display]} runs the display manager (what
 * {@code /usr/bin/amadeos-dm} starts), {@code amadeos-dm session-worker <display>} the root worker the
 * display manager starts for PAM and the session, {@code amadeos-dm greeter} the login greeter alone, for
 * a look at it on the current desktop.
 */
public final class DmLauncher {
    private DmLauncher() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";
        String[] rest = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : args;
        switch (mode) {
            case "session-worker" -> SessionWorker.main(rest);
            case "greeter" -> javax.swing.SwingUtilities.invokeLater(() -> new AmadeLoginGreeter(
                    (u, p, s, l, z) -> System.out.println("Greeter test login: user=" + u + ", session=" + s
                            + ", language=" + l + ", time zone=" + z),
                    a -> System.out.println("Greeter power action: " + a)).setVisible(true));
            case "dm" -> AmadeDisplayManager.main(rest);
            default -> AmadeDisplayManager.main(args);
        }
    }
}
