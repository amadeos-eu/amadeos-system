package eu.amadeos.system.software.appstream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The AppStream data apt fetched with the package lists (once the {@code appstream} package is installed):
 * the desktop applications of all repositories, by package. The icons are there when apt fetches them too,
 * which {@code /etc/apt/apt.conf.d/60amadeos-software} turns on.
 */
public final class AppStream {
    private static final Logger LOG = LoggerFactory.getLogger(AppStream.class);
    public static final Path LISTS = Path.of("/var/lib/apt/lists");
    public static final List<Path> ICONS = List.of(Path.of("/var/lib/swcatalog/icons"),
            Path.of("/var/lib/app-info/icons"));

    private AppStream() {
    }

    /**
     * Reads every {@code *_dep11_Components-*.yml.gz} in {@code lists}. A package with several applications
     * gets the first; a file that cannot be read is left out. Takes a few seconds for all of Debian.
     */
    public static Map<String, AppData> read(Path lists, List<Path> iconRoots, Locale locale) {
        AppStreamReader reader = new AppStreamReader(locale, iconRoots);
        Map<String, AppData> apps = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(lists, "*_Components-*.yml*")) {
            for (Path file : files) {
                try (InputStream in = open(file);
                     BufferedReader text = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    reader.read(text, app -> apps.putIfAbsent(app.pkg(), app));
                } catch (IOException | RuntimeException e) {
                    LOG.warn("Cannot read {}: {}", file, e.toString());
                }
            }
        } catch (IOException e) {
            LOG.debug("Cannot list {}", lists, e);
        }
        return apps;
    }

    private static InputStream open(Path file) throws IOException {
        InputStream in = Files.newInputStream(file);
        // apt keeps these as .gz (KeepCompressedAs in appstream's apt configuration).
        return file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(in, 1 << 16) : in;
    }
}
