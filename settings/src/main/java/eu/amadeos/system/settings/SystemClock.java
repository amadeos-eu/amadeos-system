package eu.amadeos.system.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The computer's clock, a setting of the whole system: its time zone, whether systemd-timesyncd sets it from a
 * time server on the internet, and setting it by hand. Read with {@code timedatectl show}, changed with
 * {@code timedatectl set-timezone}, {@code set-ntp} and {@code set-time}, for which polkit asks for the
 * administrator's password (once for a few minutes).
 */
interface SystemClock {
    /**
     * @param zone         the zone the computer uses now, e.g. {@code Europe/Berlin}
     * @param canNtp       false if no time service is installed
     * @param ntp          true if the time service sets the clock
     * @param synchronized true once the time service has reached a time server
     */
    record Status(String zone, boolean canNtp, boolean ntp, boolean synchronized_) {
    }

    Status status();

    /** Sets the zone of the computer; called off the event dispatch thread, it may wait for a password. */
    void setZone(String zone) throws IOException;

    /** Turns network time on or off; off the event dispatch thread. */
    void setNtp(boolean on) throws IOException;

    /** Sets the clock to a local time of the computer's zone; only works with network time off. */
    void setTime(LocalDateTime time) throws IOException;

    /** The zones to choose from: continent/city names, sorted, without the old aliases. */
    static List<String> zones() {
        Set<String> regions = Set.of("Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia",
                "Europe", "Indian", "Pacific");
        return ZoneId.getAvailableZoneIds().stream()
                .filter(id -> id.contains("/") && regions.contains(id.substring(0, id.indexOf('/'))))
                .sorted().toList();
    }

    /** A zone for display: {@code America/New_York} as {@code America / New York}. */
    static String label(String zone) {
        return zone.replace('_', ' ').replace("/", " / ");
    }

    /** The real computer: {@code timedatectl}. */
    static SystemClock system() {
        return new Timedatectl();
    }

    /** {@code timedatectl} of systemd, which talks to systemd-timedated. */
    final class Timedatectl implements SystemClock {
        static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public Status status() {
            try {
                return parse(run(List.of("timedatectl", "show")), fallbackZone());
            } catch (IOException e) {
                return new Status(fallbackZone(), false, false, false);
            }
        }

        /** {@code Timezone=Europe/Berlin}, {@code CanNTP=yes}, {@code NTP=yes}, {@code NTPSynchronized=no}, ... */
        static Status parse(String show, String fallbackZone) {
            Map<String, String> values = new HashMap<>();
            show.lines().forEach(line -> {
                int eq = line.indexOf('=');
                if (eq > 0) values.put(line.substring(0, eq), line.substring(eq + 1).strip());
            });
            String zone = values.getOrDefault("Timezone", "");
            return new Status(zone.isEmpty() ? fallbackZone : zone, "yes".equals(values.get("CanNTP")),
                    "yes".equals(values.get("NTP")), "yes".equals(values.get("NTPSynchronized")));
        }

        /** {@code /etc/timezone} or Java's view, where timedatectl cannot answer. */
        private static String fallbackZone() {
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
        public void setZone(String zone) throws IOException {
            ZoneId.of(zone);
            run(List.of("timedatectl", "set-timezone", zone));
        }

        @Override
        public void setNtp(boolean on) throws IOException {
            run(List.of("timedatectl", "set-ntp", Boolean.toString(on)));
        }

        @Override
        public void setTime(LocalDateTime time) throws IOException {
            run(List.of("timedatectl", "set-time", TIME.format(time)));
        }

        private static String run(List<String> command) throws IOException {
            List<String> full = new ArrayList<>(List.of("env", "LC_ALL=C.UTF-8"));
            full.addAll(command);
            Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
            try {
                String output;
                try (var in = process.getInputStream()) {
                    output = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
                }
                // polkit may wait for the administrator's password.
                if (!process.waitFor(5, TimeUnit.MINUTES)) {
                    process.destroy();
                    throw new IOException("timedatectl did not finish");
                }
                if (process.exitValue() != 0) {
                    throw new IOException(output.isEmpty() ? "timedatectl failed" : output.lines().findFirst().orElse(output));
                }
                return output;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }
    }
}
