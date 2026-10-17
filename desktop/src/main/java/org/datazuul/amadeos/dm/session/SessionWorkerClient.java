package org.datazuul.amadeos.dm.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.datazuul.amadeos.commons.i18n.Language;
import org.datazuul.amadeos.base.x11.LibC;

/**
 * Used by the display manager: starts a {@link SessionWorker} process, hands it the credentials
 * and waits for its verdict.
 */
public final class SessionWorkerClient {
    public sealed interface Result permits Started, Failed {
    }

    /** The session is running; the process ends when the session ends. */
    public record Started(Process worker) implements Result {
    }

    public record Failed(String message) implements Result {
    }

    public Result login(String display, String username, String password, SessionType type) {
        return login(display, username, password, type, Optional.empty());
    }

    /** @param language the language chosen in the greeter; empty keeps the system default */
    public Result login(String display, String username, String password, SessionType type,
                        Optional<Language> language) {
        return login(display, username, password, type, language, Optional.empty());
    }

    /**
     * @param language the language chosen in the greeter; empty keeps the system default
     * @param timeZone the time zone chosen in the greeter; the worker sets it on the system once the
     *                 password is accepted, before the session starts; empty keeps the system's
     */
    public Result login(String display, String username, String password, SessionType type,
                        Optional<Language> language, Optional<String> timeZone) {
        Process worker;
        try {
            worker = new ProcessBuilder(workerCommand(display, loginUid(username)))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException e) {
            return new Failed("Cannot start session worker: " + e.getMessage());
        }
        try {
            try (OutputStream stdin = worker.getOutputStream()) {
                WorkerProtocol.writeRequest(stdin,
                        new WorkerProtocol.Request(username, password, type, language, timeZone));
            }
            String error;
            try (InputStream stdout = worker.getInputStream()) {
                error = WorkerProtocol.parseResult(WorkerProtocol.readLine(stdout));
            }
            if (error == null) {
                return new Started(worker);
            }
            worker.waitFor(5, TimeUnit.SECONDS);
            return new Failed(error);
        } catch (IOException e) {
            worker.destroy();
            return new Failed(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.destroy();
            return new Failed("Interrupted");
        }
    }

    /** The uid to record as the worker's audit login uid; empty when not root or the user is unknown. */
    private static OptionalInt loginUid(String username) {
        if (LibC.INSTANCE.getuid() != 0) {
            return OptionalInt.empty();
        }
        return UserAccount.lookup(username).map(account -> OptionalInt.of(account.uid())).orElse(OptionalInt.empty());
    }

    /**
     * Same JVM and class path as the display manager, entering via the session-worker mode.
     *
     * <p>With a login uid, the JVM is started through a shell that first writes the uid to its own
     * /proc/self/loginuid. The kernel only accepts that write from the thread-group leader, and the
     * Java launcher always runs main() on a second thread, so pam_loginuid fails with EPERM when
     * called from the worker. pam_loginuid accepts a loginuid that is already correct, and exec keeps it.
     */
    static List<String> workerCommand(String display, OptionalInt loginUid) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        if (loginUid.isPresent()) {
            command.addAll(List.of("/bin/sh", "-c", "printf %s \"$1\" > /proc/self/loginuid; shift; exec \"$@\"",
                    "amadeos-session-worker", Integer.toString(loginUid.getAsInt())));
        }
        command.addAll(List.of(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"),
                "org.datazuul.amadeos.wm.AmadeOsLauncher", "session-worker", display));
        return command;
    }
}
