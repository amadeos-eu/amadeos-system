package eu.amadeos.system.base.theme.backdrop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * CDE-style backdrops: the loader for {@code .pm/.xpm} and {@code .bm/.xbm} pattern files and the lookup
 * of the system backdrops. The original CDE backdrops come as the separate Debian package
 * {@code amadeos-backdrops-cde} (LGPL, built by {@code scripts/package-cde-backdrops.sh}) and are
 * installed to {@code /usr/share/amadeos/backdrops/cde}; without that package the list is simply empty.
 *
 * <p>Patterns are coloured the way dtwm colours them from the workspace colour set: symbolic XPM colours
 * ({@code background}, {@code topShadowColor}, ...) come from {@link MotifColors} of the backdrop colour,
 * and bitmaps are drawn with the set's background (set bits) on its bottom shadow (clear bits).
 */
public final class Backdrops {
    /** Path prefix that {@code WallpaperManager} uses for classpath resources. */
    public static final String CLASSPATH = "classpath:";
    /** System property overriding {@link #SYSTEM_DIR}, for tests and development runs. */
    public static final String DIR_PROPERTY = "amadeos.backdrops.dir";
    /** Where backdrop packages install their patterns, one sub-folder per set (e.g. {@code cde/}). */
    public static final Path SYSTEM_DIR = Path.of("/usr/share/amadeos/backdrops");

    private Backdrops() {
    }

    /** The system backdrop folder, {@link #SYSTEM_DIR} unless {@value #DIR_PROPERTY} is set. */
    public static Path systemDir() {
        String override = System.getProperty(DIR_PROPERTY);
        return override == null || override.isBlank() ? SYSTEM_DIR : Path.of(override);
    }

    /** The installed system backdrops, sorted by name. */
    public static List<Path> systemBackdrops() {
        return patterns(systemDir());
    }

    /** Pattern files in {@code dir} and its direct sub-folders, sorted by display name. */
    public static List<Path> patterns(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.walk(dir, 2)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> isPattern(p.getFileName().toString()))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(out::add);
        } catch (IOException | java.io.UncheckedIOException e) {
            return out;
        }
        out.sort(Comparator.comparing((Path p) -> displayName(p.toString()).toLowerCase(Locale.ROOT))
                .thenComparing(Path::toString));
        return out;
    }

    /** The name dtstyle shows: the file name without extension. */
    public static String displayName(String file) {
        String name = Path.of(file).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Whether the path is an X pixmap or bitmap, i.e. a pattern that is coloured and tiled. */
    public static boolean isPattern(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".pm") || p.endsWith(".xpm") || p.endsWith(".bm") || p.endsWith(".xbm");
    }

    /** Loads a pattern from a file or {@code classpath:} path and colours it with the backdrop colour. */
    public static BufferedImage load(String path, Color color) throws IOException {
        return decode(path, read(path), color);
    }

    static BufferedImage decode(String name, String source, Color color) throws IOException {
        MotifColors colors = MotifColors.of(color);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".bm") || lower.endsWith(".xbm") || source.contains("_bits[]")) {
            return XbmDecoder.decode(source, colors.background(), colors.bottomShadow());
        }
        return flatten(XpmDecoder.decode(source, colors::symbol), colors.bottomShadow());
    }

    private static String read(String path) throws IOException {
        if (path.startsWith(CLASSPATH)) {
            String resource = path.substring(CLASSPATH.length());
            try (InputStream in = Backdrops.class.getResourceAsStream(resource)) {
                if (in == null) throw new IOException("no such backdrop: " + path);
                return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
        }
        return Files.readString(Path.of(path), StandardCharsets.ISO_8859_1);
    }

    /** Transparent pixels ("None") show the backdrop's base colour, as on the root window. */
    private static BufferedImage flatten(BufferedImage image, Color under) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(under);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }
}
