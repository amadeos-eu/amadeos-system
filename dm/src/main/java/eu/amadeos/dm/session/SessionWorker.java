package eu.amadeos.dm.session;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.base.pam.PamAuthenticator;
import eu.amadeos.base.x11.LibC;

/**
 * Runs one login session: started by the display manager as a separate root process
 * ({@code amadeos.jar session-worker :0}), it reads the credentials from stdin, does the whole
 * PAM conversation with a single handle, starts the session as the user and closes the PAM
 * session when the session ends.
 *
 * <p>Opening the PAM session here rather than in the display manager matters: pam_systemd moves
 * the calling process into the new login session. Done in the display manager, the whole display
 * manager would end up in the user's session and the user's processes in the display manager's
 * service, so restarting the display manager would kill the session.
 */
public final class SessionWorker {
    private static final Logger LOGGER = Logger.getLogger(SessionWorker.class.getName());
    static final String RUNNER_LOG = "/var/log/amadeos-session-runner.log";
    // Developing without the package installed: the script from the source tree (the module directory is the
    // working directory of gradlew :desktop:run and of run-greeter.sh).
    private static final String DEV_SESSION_EXECUTABLE = "packaging/root/usr/bin/amadeos-session";

    private SessionWorker() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out));
    }

    static int run(String[] args, InputStream in, OutputStream out) {
        String display = args.length > 0 && !args[0].isBlank() ? args[0] : ":0";
        int vt = parseVt(System.getenv("AMADEOS_VT"));
        String pamService = System.getProperty("amadeos.pam.service", "amadeos-dm");

        WorkerProtocol.Request request;
        try {
            request = WorkerProtocol.readRequest(in);
        } catch (IOException e) {
            reply(out, false, e.getMessage());
            return 2;
        }

        boolean asRoot = LibC.INSTANCE.getuid() == 0;
        PamAuthenticator authenticator = new PamAuthenticator(pamService);
        PamAuthenticator.AuthResult result = asRoot
                ? authenticator.openLoginSession(request.username(), request.password(), display,
                        SessionEnvironment.logindVariables(display, vt))
                : authenticator.verify(request.username(), request.password());
        if (!result.success()) {
            reply(out, false, result.errorMessage());
            return 1;
        }
        PamAuthenticator.PamSession pamSession = asRoot ? result.session() : null;

        // Only now, after the password was accepted; before the session starts, so that its clocks show it.
        request.timeZone().ifPresent(zone -> applyTimeZone(zone, asRoot));

        try {
            Optional<UserAccount> account = asRoot
                    ? UserAccount.lookup(request.username())
                    // Without root the session can only run as the current user (development in Xephyr).
                    : UserAccount.lookup(System.getProperty("user.name"));
            if (account.isEmpty()) {
                reply(out, false, "Unknown user");
                return 1;
            }
            Process session;
            try {
                session = startSession(account.get(), request.sessionType(), display, asRoot,
                        pamSession != null ? pamSession.environment() : Map.of(), request.language());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to start session for " + account.get().name(), e);
                reply(out, false, "Failed to start session: " + e.getMessage());
                return 1;
            }
            reply(out, true, null);

            Thread shutdown = new Thread(() -> endSession(session, pamSession), "amadeos-session-worker-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            int exitCode = session.waitFor();
            LOGGER.info(() -> "Session of " + account.get().name() + " ended with exit code " + exitCode);
            return exitCode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        } finally {
            if (pamSession != null) {
                pamSession.close();
            }
        }
    }

    static Process startSession(UserAccount account, SessionType type, String display, boolean asRoot,
                                Map<String, String> pamEnvironment, Optional<Language> language) throws IOException {
        String xauthority = DisplayAccess.grant(account, System.getenv("XAUTHORITY"), display, asRoot);
        Map<String, String> environment = SessionEnvironment.build(pamEnvironment, account, display, xauthority,
                type, language);
        List<String> command = SessionCommand.build(account, type, sessionExecutable(), display, asRoot);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().clear();
        pb.environment().putAll(environment);
        File home = new File(account.home());
        pb.directory(home.isDirectory() ? home : new File("/"));
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        pb.redirectErrorStream(true);
        File runnerLog = new File(RUNNER_LOG);
        boolean logWritable = runnerLog.exists() ? runnerLog.canWrite() : runnerLog.getParentFile().canWrite();
        pb.redirectOutput(logWritable ? ProcessBuilder.Redirect.appendTo(runnerLog) : ProcessBuilder.Redirect.DISCARD);
        LOGGER.info(() -> "Starting " + type.id() + " session for " + account.name() + " on " + display);
        return pb.start();
    }

    /** Sets the system time zone when the one chosen at login differs; a failure is logged, not a login error. */
    static void applyTimeZone(String zone, boolean asRoot) {
        SystemTimeZone system = SystemTimeZone.SYSTEM;
        if (system.current().filter(zone::equals).isPresent()) {
            return;
        }
        if (!asRoot) {
            LOGGER.info(() -> "Not root: the time zone " + zone + " is not applied to the system");
            return;
        }
        try {
            system.set(zone);
            LOGGER.info(() -> "System time zone set to " + zone);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot set the system time zone to " + zone, e);
        }
    }

    private static String sessionExecutable() {
        String configured = System.getProperty("amadeos.session.executable", SessionCommand.DEFAULT_SESSION_EXECUTABLE);
        if (!new File(configured).canExecute() && new File(DEV_SESSION_EXECUTABLE).canExecute()) {
            return new File(DEV_SESSION_EXECUTABLE).getAbsolutePath();
        }
        return configured;
    }

    /** On SIGTERM (shutdown): ask the session to quit, give it a moment, then close PAM. */
    private static void endSession(Process session, PamAuthenticator.PamSession pamSession) {
        if (session.isAlive()) {
            session.destroy();
            try {
                if (!session.waitFor(5, TimeUnit.SECONDS)) {
                    session.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (pamSession != null) {
            pamSession.close();
        }
    }

    private static void reply(OutputStream out, boolean ok, String message) {
        try {
            WorkerProtocol.writeResult(out, ok, message);
        } catch (IOException e) {
            // The display manager may already be gone; the session itself is unaffected.
            LOGGER.fine(() -> "Could not report result to display manager: " + e.getMessage());
        }
    }

    static int parseVt(String value) {
        try {
            int vt = Integer.parseInt(value == null ? "" : value.trim());
            return vt > 0 ? vt : 7;
        } catch (NumberFormatException e) {
            return 7;
        }
    }
}
