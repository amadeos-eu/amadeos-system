package eu.amadeos.system.software.ui;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenshotCacheTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    @TempDir
    Path dir;
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/shot.png", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, PNG.length);
            exchange.getResponseBody().write(PNG);
            exchange.close();
        });
        server.createContext("/page.png", exchange -> {
            byte[] html = "<html/>".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length);
            exchange.getResponseBody().write(html);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Test
    void downloadsOnceAndKeepsTheImage() throws Exception {
        ScreenshotCache cache = new ScreenshotCache(dir.resolve("shots"));
        Path file = cache.get(uri("/shot.png"));
        assertArrayEquals(PNG, Files.readAllBytes(file));
        assertEquals(file, cache.get(uri("/shot.png")));
        assertEquals(1, requests.get());
        assertEquals(file, cache.cached(uri("/shot.png")).orElseThrow());
    }

    @Test
    void onlyImages() {
        ScreenshotCache cache = new ScreenshotCache(dir.resolve("shots"));
        assertThrows(IOException.class, () -> cache.get(uri("/page.png")));
        assertThrows(IOException.class, () -> cache.get(uri("/missing.png")));
        assertFalse(cache.cached(uri("/page.png")).isPresent());
    }
}
