package org.datazuul.amadeos.dm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.datazuul.amadeos.commons.i18n.Language;
import org.datazuul.amadeos.dm.session.SessionType;
import org.datazuul.amadeos.dm.session.SessionWorkerClient;
import org.datazuul.amadeos.dm.session.LastUser;
import org.datazuul.amadeos.dm.session.UserAccount;
import org.datazuul.amadeos.dm.ui.AmadeLoginGreeter;

public final class AmadeDisplayManager {
    private static final Logger LOGGER = Logger.getLogger(AmadeDisplayManager.class.getName());

    private final String display;
    private final SessionWorkerClient sessionWorkers;
    private AmadeLoginGreeter greeter;
    private Process xorgProcess;

    public static void main(String[] args) {
        String display = args.length > 0 ? args[0] : System.getenv().getOrDefault("DISPLAY", ":0");
        AmadeDisplayManager dm = new AmadeDisplayManager(display);
        dm.start();
    }

    public AmadeDisplayManager(String display) {
        this(display, new SessionWorkerClient());
    }

    public AmadeDisplayManager(String display, SessionWorkerClient sessionWorkers) {
        this.display = display;
        this.sessionWorkers = sessionWorkers;
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
                List<String> command = new ArrayList<>(List.of(
                        "Xorg", ":0", "vt7", "-novtswitch", "-nolisten", "tcp", "-background", "none"));
                String authority = System.getenv("XAUTHORITY");
                if (authority != null && !authority.isBlank()) {
                    // amadeos-dm creates the cookie; without -auth the server would accept anyone.
                    command.add("-auth");
                    command.add(authority);
                } else {
                    LOGGER.warning("XAUTHORITY is not set; starting Xorg without access control");
                }
                ProcessBuilder pb = new ProcessBuilder(command);
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

    /** Runs on the greeter's authentication thread. */
    private void handleLogin(String username, String password, String sessionLabel, Language language,
                             String timeZone) {
        SessionType type = SessionType.from(sessionLabel);
        LOGGER.info(() -> "Login attempt (session: " + type.id() + ", language: " + language
                + ", time zone: " + timeZone + ")");

        // PAM and the session run in a separate root worker process (see SessionWorker).
        SessionWorkerClient.Result result = sessionWorkers.login(display, username, password, type,
                Optional.ofNullable(language), Optional.ofNullable(timeZone));
        if (result instanceof SessionWorkerClient.Failed failed) {
            greeter.setStatus(failed.message(), true);
            return;
        }
        if (timeZone != null) {
            // The worker has set the system's zone by now; this JVM read it at start, so follow by hand
            // for the clock of the greeter, which comes back when the session ends.
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(timeZone)));
            } catch (DateTimeException e) {
                LOGGER.fine(() -> "Unknown time zone for the greeter clock: " + timeZone);
            }
        }
        Process worker = ((SessionWorkerClient.Started) result).worker();
        LastUser.SYSTEM.remember(username);
        greeter.setStatus("Starting session...", false);
        SwingUtilities.invokeLater(() -> greeter.setVisible(false));
        new Thread(() -> superviseSession(username, worker), "amadeOS-Session-Supervisor").start();
    }

    private void superviseSession(String username, Process worker) {
        long startTime = System.currentTimeMillis();
        int exitCode = -1;
        try {
            exitCode = worker.waitFor();
            LOGGER.info("User session for " + username + " exited with code " + exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Check if user requested system power actions via session control dialog
            checkSessionActionFile(username);

            long duration = System.currentTimeMillis() - startTime;
            if (duration < 2000 || exitCode != 0) {
                LOGGER.warning("Session ended quickly (" + duration + "ms) with exit code " + exitCode
                        + "; see /var/log/amadeos-session-runner.log");
            }

            // Re-arm the login greeter
            SwingUtilities.invokeLater(() -> {
                greeter.resetFields();
                greeter.setVisible(true);
            });
        }
    }

    private void checkSessionActionFile(String username) {
        String home = UserAccount.lookup(username).map(UserAccount::home).orElse("/home/" + username);
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

