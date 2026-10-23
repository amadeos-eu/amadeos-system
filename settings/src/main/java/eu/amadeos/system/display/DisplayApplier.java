package eu.amadeos.system.display;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Asks xrandr for the monitors and sets a layout. Used by the Settings (Display page) and at login
 * ({@code amadeos-settings display}, from {@code /etc/amadeos/autostart.d/15-amadeos-display}), which sets the
 * layout saved for the monitors that are connected.
 */
public class DisplayApplier {
    private static final Logger LOGGER = Logger.getLogger(DisplayApplier.class.getName());

    /** The connectors xrandr reports, empty if it cannot be asked. */
    public List<Output> query() {
        StringBuilder out = new StringBuilder();
        if (run(List.of("xrandr", "--query"), out) != 0) return List.of();
        return Xrandr.parse(out.toString());
    }

    /** The connectors that have a monitor on them. */
    public List<Output> connected() {
        return query().stream().filter(Output::connected).toList();
    }

    /** Sets the layout in one xrandr call; false if xrandr refused it. */
    public boolean apply(List<OutputSetting> layout) {
        List<String> command = Xrandr.command(layout);
        StringBuilder out = new StringBuilder();
        int exit = run(command, out);
        if (exit != 0) LOGGER.warning("xrandr failed (" + exit + "): " + command + ": " + out.toString().strip());
        return exit == 0;
    }

    /** Exit code of the command, -1 if it could not run. */
    protected int run(List<String> command, StringBuilder output) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            String text;
            try (InputStream in = process.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            if (output != null) output.append(text);
            return process.exitValue();
        } catch (IOException e) {
            LOGGER.fine("Cannot run " + command + ": " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** Sets the layout saved for the connected monitors; true if there was none or it was set. */
    public boolean applySaved(DisplaySettingsStore store) {
        List<String> names = connected().stream().map(Output::name).toList();
        if (names.isEmpty()) return true;
        return store.load(names).map(this::apply).orElse(true);
    }

    /** {@code amadeos-settings display}: sets the saved layout at login. */
    public static void main(String[] args) {
        System.exit(new DisplayApplier().applySaved(DisplaySettingsStore.user()) ? 0 : 1);
    }
}
