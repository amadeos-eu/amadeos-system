package org.datazuul.amadeos.dm.session;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datazuul.amadeos.wm.x11.LibC;

public final class SessionLauncher {
    private static final Logger LOGGER = Logger.getLogger(SessionLauncher.class.getName());
    private static final String DEFAULT_SESSION_SCRIPT = "/usr/local/bin/amadeos-session";

    private final String sessionExecutable;

    public SessionLauncher() {
        this(DEFAULT_SESSION_SCRIPT);
    }

    public SessionLauncher(String sessionExecutable) {
        this.sessionExecutable = Objects.requireNonNull(sessionExecutable);
    }

    public Process launchSession(String username, String display) throws IOException {
        Objects.requireNonNull(username, "username must not be null");
        if (display == null || display.isBlank()) {
            display = ":0";
        }

        // Query user details from system passwd database
        String homeDir = "/home/" + username;
        String shell = "/bin/bash";
        int uid = -1;
        int gid = -1;

        try {
            LibC.Passwd pwd = LibC.INSTANCE.getpwnam(username);
            if (pwd != null) {
                uid = pwd.pw_uid;
                gid = pwd.pw_gid;
                if (pwd.pw_dir != null && !pwd.pw_dir.isBlank()) {
                    homeDir = pwd.pw_dir;
                }
                if (pwd.pw_shell != null && !pwd.pw_shell.isBlank()) {
                    shell = pwd.pw_shell;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to resolve passwd for user " + username + ": " + e.getMessage());
        }

        LOGGER.info(String.format("Launching session for user %s (uid=%d, gid=%d, home=%s, display=%s)",
                username, uid, gid, homeDir, display));

        int currentUid = LibC.INSTANCE.getuid();
        List<String> cmd = new ArrayList<>();

        String sessionCmd = sessionExecutable;
        File execFile = new File(sessionCmd);
        if (!execFile.canExecute()) {
            File localDev = new File("config/includes.chroot/usr/local/bin/amadeos-session");
            if (localDev.canExecute()) {
                sessionCmd = localDev.getAbsolutePath();
            }
        }

        if (currentUid == 0) {
            // Running as root: drop privileges to user using runuser or su, preserving environment
            File runuserBin = new File("/usr/sbin/runuser");
            if (runuserBin.canExecute()) {
                cmd.add("/usr/sbin/runuser");
                cmd.add("-m"); // Preserve environment variables (DISPLAY, HOME, USER, etc.)
                cmd.add("-u");
                cmd.add(username);
                cmd.add("--");
                cmd.add(sessionCmd);
                cmd.add(display);
            } else {
                cmd.add("su");
                cmd.add("-p"); // Preserve environment
                cmd.add(username);
                cmd.add("-c");
                cmd.add(sessionCmd + " " + display);
            }
        } else {
            // Running as non-root (local development / testing)
            cmd.add(sessionCmd);
            cmd.add(display);
        }

        // Clean up any stale root-owned log or lock files in user's home directory
        try {
            new File(homeDir, ".amadeos-session.log").delete();
            new File(homeDir, ".amadeos-wm.log").delete();
            new File(homeDir, ".amadeos-panel.lock").delete();
            new File(homeDir, ".amadeos-wm.pid").delete();
        } catch (Exception ignored) {
        }

        // Ensure user home directory exists and is owned by the user
        if (currentUid == 0 && new File(homeDir).isDirectory()) {
            try {
                new ProcessBuilder("chown", "-R", username + ":" + username, homeDir)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        env.put("USER", username);
        env.put("LOGNAME", username);
        env.put("HOME", homeDir);
        env.put("SHELL", shell);
        env.put("DISPLAY", display);
        env.put("XDG_SESSION_TYPE", "x11");
        env.put("XDG_CURRENT_DESKTOP", "amadeOS");
        env.put("XDG_SESSION_DESKTOP", "amadeOS");

        File workingDir = new File(homeDir);
        if (workingDir.isDirectory()) {
            pb.directory(workingDir);
        } else {
            pb.directory(new File("/tmp"));
        }

        String userXauthority = grantDisplayAccess(username, homeDir, display, currentUid == 0);
        if (userXauthority != null) {
            env.put("XAUTHORITY", userXauthority);
        }

        pb.redirectErrorStream(true);
        File runnerLog = new File("/var/log/amadeos-session-runner.log");
        try {
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(runnerLog));
        } catch (Exception e) {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }

        return pb.start();
    }

    /**
     * Copies the X server cookie of the display manager into the user's ~/.Xauthority so the
     * session can connect without opening the server to every local process via xhost.
     * The file is written by xauth running as the user, never by root, so a planted symlink
     * cannot redirect the write.
     *
     * @return the XAUTHORITY path for the session, or the inherited one when not running as root
     */
    String grantDisplayAccess(String username, String homeDir, String display, boolean asRoot) {
        String serverAuthority = System.getenv("XAUTHORITY");
        if (serverAuthority == null || serverAuthority.isBlank()) {
            LOGGER.warning("XAUTHORITY is not set; the session may not be allowed to connect to " + display);
            return null;
        }
        if (!asRoot) {
            return serverAuthority;
        }
        String userAuthority = new File(homeDir, ".Xauthority").getPath();
        try {
            Process list = new ProcessBuilder("xauth", "-f", serverAuthority, "nlist", display)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] entries = list.getInputStream().readAllBytes();
            if (!list.waitFor(5, TimeUnit.SECONDS) || list.exitValue() != 0 || entries.length == 0) {
                LOGGER.warning("No X authorization entry found for " + display + " in " + serverAuthority);
                return null;
            }
            Process merge = new ProcessBuilder("/usr/sbin/runuser", "-u", username, "--",
                    "xauth", "-q", "-f", userAuthority, "nmerge", "-")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (var stdin = merge.getOutputStream()) {
                stdin.write(entries);
            }
            if (!merge.waitFor(5, TimeUnit.SECONDS) || merge.exitValue() != 0) {
                LOGGER.warning("Failed to store X authorization in " + userAuthority);
                return null;
            }
            return userAuthority;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to grant X display access to " + username + ": " + e.getMessage(), e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
