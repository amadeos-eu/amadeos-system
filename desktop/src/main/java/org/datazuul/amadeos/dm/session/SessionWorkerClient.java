package org.datazuul.amadeos.dm.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        Process worker;
        try {
            worker = new ProcessBuilder(workerCommand(display))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException e) {
            return new Failed("Cannot start session worker: " + e.getMessage());
        }
        try {
            try (OutputStream stdin = worker.getOutputStream()) {
                WorkerProtocol.writeRequest(stdin, new WorkerProtocol.Request(username, password, type));
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

    /** Same JVM and class path as the display manager, entering via the session-worker mode. */
    static List<String> workerCommand(String display) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"),
                "org.datazuul.amadeos.wm.AmadeOsLauncher", "session-worker", display);
    }
}
