package eu.amadeos.system.taskmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the Task Manager to one per user: the first one listens on a Unix socket, a second start hands its
 * arguments over (one line, separated by spaces) and ends. Ctrl+Shift+Esc pressed twice thus brings the open
 * window to the front instead of opening another.
 */
public final class InstanceChannel implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(InstanceChannel.class.getName());

    private final Path path;
    private final ServerSocketChannel server;

    private InstanceChannel(Path path, ServerSocketChannel server) {
        this.path = path;
        this.server = server;
    }

    /** {@code $XDG_RUNTIME_DIR/amadeos-taskmanager.socket}, or a file in the temporary folder. */
    public static Path defaultPath() {
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        String display = System.getenv().getOrDefault("DISPLAY", "").replaceAll("[^A-Za-z0-9.]", "_");
        String name = "amadeos-taskmanager" + (display.isEmpty() ? "" : "-" + display) + ".socket";
        // A Unix socket path may have at most 107 bytes.
        if (runtime != null && !runtime.isBlank() && Files.isDirectory(Path.of(runtime))
                && runtime.length() + name.length() < 100) {
            return Path.of(runtime, name);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), System.getProperty("user.name") + "-" + name);
    }

    /** Sends the arguments to the running Task Manager; false if none runs. */
    public static boolean send(Path path, List<String> arguments) {
        if (!Files.exists(path)) return false;
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(path))) {
            OutputStream out = Channels.newOutputStream(channel);
            out.write((String.join(" ", arguments) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Listens for later starts; each one's arguments go to the handler (on the listening thread). */
    public static InstanceChannel listen(Path path, Consumer<List<String>> handler) throws IOException {
        Files.deleteIfExists(path);
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(path));
        InstanceChannel channel = new InstanceChannel(path, server);
        Thread thread = new Thread(() -> channel.accept(handler), "taskmanager-instance");
        thread.setDaemon(true);
        thread.start();
        return channel;
    }

    private void accept(Consumer<List<String>> handler) {
        while (server.isOpen()) {
            try (SocketChannel client = server.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(client),
                         StandardCharsets.UTF_8))) {
                String line = in.readLine();
                if (line != null) handler.accept(line.isBlank() ? List.of() : List.of(line.trim().split(" +")));
            } catch (IOException e) {
                if (server.isOpen()) LOGGER.log(Level.FINE, "Instance channel", e);
            }
        }
    }

    @Override
    public void close() {
        try {
            server.close();
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // gone already
        }
    }
}
