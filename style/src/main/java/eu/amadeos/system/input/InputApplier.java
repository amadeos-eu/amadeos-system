package eu.amadeos.system.input;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs the {@link XCommands} against the current display. Used by the Style Manager (Apply) and at login
 * ({@code amadeos.jar input}, from {@code /etc/amadeos/autostart.d/10-amadeos-input}).
 */
public class InputApplier {
    private static final Logger LOGGER = Logger.getLogger(InputApplier.class.getName());

    /** Applies the commands; returns the ones that failed (empty if all worked). */
    public List<XCommands.Command> apply(List<XCommands.Command> commands) {
        List<XCommands.Command> failed = new ArrayList<>();
        for (XCommands.Command command : commands) {
            if (run(command, null) != 0) failed.add(command);
        }
        if (!failed.isEmpty()) LOGGER.warning("Input settings not applied: " + failed);
        return failed;
    }

    public List<XCommands.Command> applyAll(InputSettings settings) {
        return apply(XCommands.all(settings, pointerButtons()));
    }

    public int pointerButtons() {
        StringBuilder out = new StringBuilder();
        run(XCommands.Command.of("xmodmap", "-pp"), out);
        return XCommands.pointerButtons(out.toString());
    }

    /** Exit code of the command, -1 if it could not run. */
    protected int run(XCommands.Command command, StringBuilder output) {
        try {
            Process process = new ProcessBuilder(command.args()).redirectErrorStream(true).start();
            try (OutputStream stdin = process.getOutputStream()) {
                if (command.stdin() != null) stdin.write(command.stdin().getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            try (InputStream out = process.getInputStream()) {
                String text = new String(out.readAllBytes(), StandardCharsets.UTF_8);
                if (output != null) output.append(text);
            }
            return process.exitValue();
        } catch (IOException e) {
            LOGGER.fine("Cannot run " + command + ": " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** {@code amadeos.jar input}: applies the stored settings at login. */
    public static void main(String[] args) {
        List<XCommands.Command> failed = new InputApplier().applyAll(InputSettingsStore.user().load());
        System.exit(failed.isEmpty() ? 0 : 1);
    }
}
