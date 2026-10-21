package eu.amadeos.system.software.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Screenshots from the distribution's media server, kept in {@code ~/.cache/amadeos-software/screenshots} so
 * each is downloaded once. Only images, at most 8 MB.
 */
public final class ScreenshotCache {
    private static final long MAX_BYTES = 8L << 20;

    private final Path dir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    ScreenshotCache(Path dir) {
        this.dir = dir;
    }

    public static ScreenshotCache inUserCache() {
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".cache");
        return new ScreenshotCache(base.resolve("amadeos-software").resolve("screenshots"));
    }

    /** The file a screenshot is kept in. */
    Path file(URI image) {
        String path = image.getPath() == null ? "" : image.getPath().toLowerCase(Locale.ROOT);
        String extension = path.endsWith(".jpg") || path.endsWith(".jpeg") ? ".jpg" : ".png";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(image.toString().getBytes(StandardCharsets.UTF_8));
            return dir.resolve(HexFormat.of().formatHex(hash, 0, 16) + extension);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    Optional<Path> cached(URI image) {
        Path file = file(image);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** The screenshot, downloaded first unless it is in the cache. */
    Path get(URI image) throws IOException {
        Optional<Path> cached = cached(image);
        if (cached.isPresent()) return cached.get();
        Files.createDirectories(dir);
        HttpRequest request = HttpRequest.newBuilder(image).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "amadeos-software").GET().build();
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        try (InputStream in = response.body()) {
            String type = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + image);
            if (!type.isEmpty() && !type.startsWith("image/")) throw new IOException("not an image: " + type);
            Path part = Files.createTempFile(dir, "download", ".part");
            try {
                long copied = 0;
                try (var out = Files.newOutputStream(part)) {
                    byte[] buffer = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        copied += n;
                        if (copied > MAX_BYTES) throw new IOException("screenshot too large: " + image);
                        out.write(buffer, 0, n);
                    }
                }
                Path file = file(image);
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return file;
            } finally {
                Files.deleteIfExists(part);
            }
        }
    }
}
