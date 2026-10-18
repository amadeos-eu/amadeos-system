package eu.amadeos.dm.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The time zone of the system: {@code /etc/localtime}, a link to a file under {@code /usr/share/zoneinfo},
 * and its name in {@code /etc/timezone}, which the JDK reads first.
 *
 * <p>The greeter offers the zones of {@code zone1970.tab} and the session worker, which runs as root, sets
 * the one chosen at login before the session starts, so that the clocks of the desktop show it. The live
 * image boots with the zone from the kernel command line ({@code timezone=Europe/Berlin}) and forgets a
 * change at shutdown; an installed system keeps it.
 */
public final class SystemTimeZone {
    public static final SystemTimeZone SYSTEM = new SystemTimeZone(Path.of("/"));
    public static final String UTC = "UTC";
    static final String ZONEINFO = "usr/share/zoneinfo";
    static final String TABLE = "zone1970.tab";
    /** Region/City names only: nothing that could leave the zoneinfo directory. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_+-]*(/[A-Za-z0-9_+-]+){0,2}");

    private final Path root;

    /** @param root the directory that stands for {@code /}; tests use a temporary one */
    SystemTimeZone(Path root) {
        this.root = root;
    }

    /**
     * The zones to choose from: UTC, then the zones of {@code zone1970.tab} (one per distinct zone, without
     * the old aliases) in alphabetical order. Without the table, the JDK's list of Region/City zones.
     */
    public List<String> available() {
        List<String> zones = new ArrayList<>();
        zones.add(UTC);
        Path table = root.resolve(ZONEINFO).resolve(TABLE);
        if (Files.isReadable(table)) {
            try {
                zones.addAll(parseTable(Files.readString(table, StandardCharsets.UTF_8)));
                return zones;
            } catch (IOException e) {
                // fall through to the JDK's list
            }
        }
        TreeSet<String> ids = new TreeSet<>();
        for (String id : ZoneId.getAvailableZoneIds()) {
            if (id.indexOf('/') > 0 && !id.startsWith("Etc/") && !id.startsWith("SystemV/") && NAME.matcher(id).matches()) {
                ids.add(id);
            }
        }
        zones.addAll(ids);
        return zones;
    }

    /** The zone names (third column) of {@code zone1970.tab}, sorted. */
    static List<String> parseTable(String text) {
        TreeSet<String> zones = new TreeSet<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("#") || line.isBlank()) continue;
            String[] columns = line.split("\t");
            if (columns.length >= 3 && NAME.matcher(columns[2]).matches()) {
                zones.add(columns[2]);
            }
        }
        return new ArrayList<>(zones);
    }

    /**
     * The zone {@code /etc/localtime} points to, e.g. {@code Europe/Berlin}; empty when it is not a link
     * into the zoneinfo directory (then the JVM's default zone is the best guess).
     */
    public Optional<String> current() {
        Path localtime = root.resolve("etc").resolve("localtime");
        try {
            if (!Files.isSymbolicLink(localtime)) {
                return Optional.empty();
            }
            // Only the text of the link matters, which names a path on the real system whatever the root:
            // Debian's tzdata links to /usr/share/zoneinfo/..., systemd to ../usr/share/zoneinfo/...
            Path target = Files.readSymbolicLink(localtime);
            Path resolved = (target.isAbsolute() ? target : Path.of("/etc").resolve(target)).normalize();
            Path zoneinfo = Path.of("/").resolve(ZONEINFO);
            if (!resolved.startsWith(zoneinfo) || resolved.equals(zoneinfo)) {
                return Optional.empty();
            }
            String zone = zoneinfo.relativize(resolved).toString();
            return NAME.matcher(zone).matches() ? Optional.of(zone) : Optional.empty();
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    /** Whether the name is a zone this system has a zoneinfo file for. */
    public boolean isKnown(String zone) {
        return zone != null && NAME.matcher(zone).matches()
                && Files.isRegularFile(root.resolve(ZONEINFO).resolve(zone));
    }

    /**
     * Points {@code /etc/localtime} at the zone and writes {@code /etc/timezone}. The link is replaced in
     * one step, so there is never a moment without a time zone. Needs root on a real system.
     *
     * @throws IOException for an unknown zone, or when the files cannot be written
     */
    public void set(String zone) throws IOException {
        if (!isKnown(zone)) {
            throw new IOException("Unknown time zone: " + zone);
        }
        Path etc = root.resolve("etc");
        Path localtime = etc.resolve("localtime");
        Path temporary = etc.resolve("localtime.amadeos-dm");
        // The link target as Debian writes it: /usr/share/zoneinfo/<zone>.
        Path target = Path.of("/").resolve(ZONEINFO).resolve(zone);
        Files.deleteIfExists(temporary);
        Files.createSymbolicLink(temporary, target);
        try {
            Files.move(temporary, localtime, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        Files.writeString(etc.resolve("timezone"), zone + "\n", StandardCharsets.UTF_8);
    }
}
