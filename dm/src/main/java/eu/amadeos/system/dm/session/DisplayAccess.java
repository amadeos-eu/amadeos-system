package eu.amadeos.system.dm.session;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hands the X server cookie of the display manager to the user session.
 */
public final class DisplayAccess {
    private static final Logger LOGGER = Logger.getLogger(DisplayAccess.class.getName());

    private DisplayAccess() {
    }

    /**
     * Copies the cookie for {@code display} from the server's authority file into the user's
     * ~/.Xauthority. The file is written by xauth running as the user (via setpriv), never by
     * root, so a planted symlink cannot redirect the write.
     *
     * @return the user's XAUTHORITY path, or null if no cookie could be handed over
     */
    public static String grant(UserAccount account, String serverAuthority, String display, boolean asRoot) {
        if (serverAuthority == null || serverAuthority.isBlank()) {
            LOGGER.warning("XAUTHORITY is not set; the session may not be allowed to connect to " + display);
            return null;
        }
        if (!asRoot) {
            return serverAuthority;
        }
        String userAuthority = new File(account.home(), ".Xauthority").getPath();
        try {
            Process list = new ProcessBuilder("xauth", "-f", serverAuthority, "nlist", display)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] entries = list.getInputStream().readAllBytes();
            if (!list.waitFor(5, TimeUnit.SECONDS) || list.exitValue() != 0 || entries.length == 0) {
                LOGGER.warning("No X authorization entry found for " + display + " in " + serverAuthority);
                return null;
            }
            List<String> merge = new ArrayList<>(SessionCommand.dropPrivileges(account));
            merge.addAll(List.of("xauth", "-q", "-f", userAuthority, "nmerge", "-"));
            Process process = new ProcessBuilder(merge)
                    .directory(new File(account.home()).isDirectory() ? new File(account.home()) : new File("/"))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(entries);
            }
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                LOGGER.warning("Failed to store X authorization in " + userAuthority);
                return null;
            }
            return userAuthority;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to grant X display access to " + account.name() + ": " + e.getMessage(), e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
