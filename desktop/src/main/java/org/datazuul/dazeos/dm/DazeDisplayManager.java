package org.datazuul.dazeos.dm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.datazuul.dazeos.dm.pam.PamAuthenticator;
import org.datazuul.dazeos.dm.session.SessionLauncher;
import org.datazuul.dazeos.dm.ui.DazeLoginGreeter;

public final class DazeDisplayManager {
    private static final Logger LOGGER = Logger.getLogger(DazeDisplayManager.class.getName());

    private final String display;
    private final PamAuthenticator authenticator;
    private final SessionLauncher sessionLauncher;
    private DazeLoginGreeter greeter;
    private Process xorgProcess;

    public static void main(String[] args) {
        String display = args.length > 0 ? args[0] : System.getenv().getOrDefault("DISPLAY", ":0");
        DazeDisplayManager dm = new DazeDisplayManager(display);
        dm.start();
    }

    public DazeDisplayManager(String display) {
        this(display, new PamAuthenticator(), new SessionLauncher());
    }

    public DazeDisplayManager(String display, PamAuthenticator authenticator, SessionLauncher sessionLauncher) {
        this.display = display;
        this.authenticator = authenticator;
        this.sessionLauncher = sessionLauncher;
    }

    public void start() {
        LOGGER.info("Starting DazeOS Display Manager on display " + display);

        // Ensure Xorg is running if display :0 and not running
        ensureXServer();

        SwingUtilities.invokeLater(() -> {
            greeter = new DazeLoginGreeter(this::handleLogin, this::handlePowerAction);
            greeter.setVisible(true);
        });
    }

    private void ensureXServer() {
        String dispNum = display.startsWith(":") ? display.substring(1) : display;
        File xSocket = new File("/tmp/.X11-unix/X" + dispNum);
        if (!xSocket.exists() && ":0".equals(display)) {
            LOGGER.info("X server not detected on " + display + ". Launching Xorg on vt7...");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "Xorg", ":0", "vt7", "-novtswitch", "-nolisten", "tcp", "-background", "none"
                );
                pb.redirectOutput(new File("/var/log/dazeos-xorg.log"));
                pb.redirectErrorStream(true);
                xorgProcess = pb.start();

                // Wait up to 5 seconds for X server to create socket
                for (int i = 0; i < 50; i++) {
                    if (xSocket.exists()) {
                        LOGGER.info("X server successfully initialized on " + display);
                        break;
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to start Xorg: " + e.getMessage(), e);
            }
        }
    }

    private void handleLogin(String username, String password, String sessionType) {
        LOGGER.info(() -> "Login attempt for user: " + username + " (session: " + sessionType + ")");

        PamAuthenticator.AuthResult result = authenticator.authenticate(username, password);
        if (!result.success()) {
            greeter.setStatus("Login incorrect; please try again.", true);
            return;
        }

        greeter.setStatus("Authentication successful. Starting session...", false);

        // Hide greeter and spawn session
        SwingUtilities.invokeLater(() -> greeter.setVisible(false));

        try {
            Process sessionProc = sessionLauncher.launchSession(username, display);

            // Supervise the session asynchronously
            new Thread(() -> superviseSession(username, sessionProc, result.session()), "DazeOS-Session-Supervisor").start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to launch session: " + e.getMessage(), e);
            greeter.setStatus("Failed to start session: " + e.getMessage(), true);
            SwingUtilities.invokeLater(() -> greeter.setVisible(true));
            result.session().close();
        }
    }

    private void superviseSession(String username, Process sessionProc, PamAuthenticator.PamSession pamSession) {
        try {
            int exitCode = sessionProc.waitFor();
            LOGGER.info("User session for " + username + " exited with code " + exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Clean up PAM session credentials
            pamSession.close();

            // Check if user requested system power actions via session control dialog
            checkSessionActionFile(username);

            // Re-arm the login greeter
            SwingUtilities.invokeLater(() -> {
                greeter.resetFields();
                greeter.setVisible(true);
            });
        }
    }

    private void checkSessionActionFile(String username) {
        String home = "/home/" + username;
        File actionFile = new File(home, ".dazeos-session-action");
        if (actionFile.exists()) {
            try {
                String action = Files.readString(actionFile.toPath(), StandardCharsets.UTF_8).trim();
                actionFile.delete();
                LOGGER.info("Session action requested: " + action);
                if ("poweroff".equalsIgnoreCase(action)) {
                    handlePowerAction("poweroff");
                } else if ("reboot".equalsIgnoreCase(action)) {
                    handlePowerAction("reboot");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing session action file: " + e.getMessage());
            }
        }
    }

    private void handlePowerAction(String action) {
        LOGGER.info("Executing power action: " + action);
        try {
            if ("poweroff".equalsIgnoreCase(action)) {
                new ProcessBuilder("systemctl", "poweroff").start();
            } else if ("reboot".equalsIgnoreCase(action)) {
                new ProcessBuilder("systemctl", "reboot").start();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to execute power action: " + e.getMessage());
        }
    }
}

