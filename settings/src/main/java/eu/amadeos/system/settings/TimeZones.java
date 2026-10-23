package eu.amadeos.system.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The computer's time zone, a setting of the whole system: read from {@code /etc/timezone} or Java's default,
 * changed with {@code timedatectl set-timezone}, for which polkit asks for the administrator's password.
 */
interface TimeZones {
    /** The zone the computer uses now, e.g. {@code Europe/Berlin}. */
    String current();

    /** Sets the zone of the computer; called off the event dispatch thread, it may wait for a password. */
    void set(String zone) throws IOException;

    /** The zones to choose from: continent/city names, sorted, without the old aliases. */
    static List<String> choices() {
        Set<String> regions = Set.of("Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia",
                "Europe", "Indian", "Pacific");
        return ZoneId.getAvailableZoneIds().stream()
                .filter(id -> id.contains("/") && regions.contains(id.substring(0, id.indexOf('/'))))
                .sorted().toList();
    }

    /** The real computer: {@code timedatectl}. */
    static TimeZones system() {
        return new TimeZones() {
            @Override
            public String current() {
                try {
                    Path file = Path.of("/etc/timezone");
                    if (Files.isRegularFile(file)) {
                        String zone = Files.readString(file, StandardCharsets.UTF_8).strip();
                        if (!zone.isEmpty()) return zone;
                    }
                } catch (IOException e) {
                    // fall back to Java's view
                }
                return ZoneId.systemDefault().getId();
            }

            @Override
            public void set(String zone) throws IOException {
                ZoneId.of(zone);
                Process process = new ProcessBuilder("timedatectl", "set-timezone", zone).redirectErrorStream(true).start();
                try {
                    String output;
                    try (var in = process.getInputStream()) {
                        output = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
                    }
                    if (!process.waitFor(5, TimeUnit.MINUTES)) {
                        process.destroy();
                        throw new IOException("timedatectl did not finish");
                    }
                    if (process.exitValue() != 0) {
                        throw new IOException(output.isEmpty() ? "timedatectl failed" : output.lines().findFirst().orElse(output));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
        };
    }

    /** A zone for display: {@code America/New_York} as {@code America / New York}. */
    static String label(String zone) {
        return zone.replace('_', ' ').replace("/", " / ");
    }
}
