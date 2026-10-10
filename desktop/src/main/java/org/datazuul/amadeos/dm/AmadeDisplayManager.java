package org.datazuul.amadeos.dm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.datazuul.amadeos.dm.pam.PamAuthenticator;
import org.datazuul.amadeos.dm.session.SessionLauncher;
import org.datazuul.amadeos.dm.ui.AmadeLoginGreeter;

public final class AmadeDisplayManager {
    private static final Logger LOGGER = Logger.getLogger(AmadeDisplayManager.class.getName());

    private final String display;
    private final PamAuthenticator authenticator;
    private final SessionLauncher sessionLauncher;
    private AmadeLoginGreeter greeter;
    private Process xorgProcess;

    public static void main(String[] args) {
        String display = args.length > 0 ? args[0] : System.getenv().getOrDefault("DISPLAY", ":0");
        AmadeDisplayManager dm = new AmadeDisplayManager(display);
        dm.start();
    }

    public AmadeDisplayManager(String display) {
        this(display, new PamAuthenticator(), new SessionLauncher());
    }

    public AmadeDisplayManager(String display, PamAuthenticator authenticator, SessionLauncher sessionLauncher) {
        this.display = display;
        this.authenticator = authenticator;
        this.sessionLauncher = sessionLauncher;
    }

    public void start() {
        LOGGER.info("Starting amadeOS Display Manager on display " + display);

        // Ensure Xorg is running if display :0 and not running
        ensureXServer();

        SwingUtilities.invokeLater(() -> {
            greeter = new AmadeLoginGreeter(this::handleLogin, this::handlePowerAction);
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
                pb.redirectOutput(new File("/var/log/amadeos-xorg.log"));
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
            new Thread(() -> superviseSession(username, sessionProc, result.session()), "amadeOS-Session-Supervisor").start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to launch session: " + e.getMessage(), e);
            greeter.setStatus("Failed to start session: " + e.getMessage(), true);
            SwingUtilities.invokeLater(() -> greeter.setVisible(true));
            result.session().close();
        }
    }

    private void superviseSession(String username, Process sessionProc, PamAuthenticator.PamSession pamSession) {
        long startTime = System.currentTimeMillis();
        int exitCode = -1;
        try {
            exitCode = sessionProc.waitFor();
            LOGGER.info("User session for " + username + " exited with code " + exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Clean up PAM session credentials
            pamSession.close();

            // Check if user requested system power actions via session control dialog
            checkSessionActionFile(username);

            long duration = System.currentTimeMillis() - startTime;
            if (duration < 2000 || exitCode != 0) {
                LOGGER.warning("Session ended quickly (" + duration + "ms) with exit code " + exitCode);
                File runnerLog = new File("/var/log/amadeos-session-runner.log");
                if (runnerLog.exists()) {
                    try {
                        String logTail = Files.readString(runnerLog.toPath());
                        if (!logTail.isBlank()) {
                            LOGGER.warning("Session runner log:\n" + logTail);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            // Re-arm the login greeter
            SwingUtilities.invokeLater(() -> {
                greeter.resetFields();
                greeter.setVisible(true);
            });
        }
    }

    private void checkSessionActionFile(String username) {
        String home = "/home/" + username;
        File actionFile = new File(home, ".amadeos-session-action");
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

