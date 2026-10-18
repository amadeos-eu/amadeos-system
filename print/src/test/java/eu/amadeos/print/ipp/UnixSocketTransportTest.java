package eu.amadeos.print.ipp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnixSocketTransportTest {
    @Test
    void readsChunkedAndSizedBodies() throws IOException {
        String chunked = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2;x=y\r\nde\r\n0\r\n\r\n";
        UnixSocketTransport.Response response = UnixSocketTransport.read(stream(chunked));
        assertEquals(200, response.status());
        assertEquals("abcde", new String(response.body(), StandardCharsets.US_ASCII));

        String sized = "HTTP/1.1 401 Unauthorized\r\nContent-Length: 4\r\nWWW-Authenticate: PeerCred\r\n\r\nnopeEXTRA";
        response = UnixSocketTransport.read(stream(sized));
        assertEquals(401, response.status());
        assertEquals("nope", new String(response.body(), StandardCharsets.US_ASCII));
    }

    @Test
    void asksAgainWithPeerCredentialsWhenCupsWantsThem(@TempDir Path dir) throws Exception {
        Path socket = dir.resolve("cups.sock");
        List<String> requests = new ArrayList<>();
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            Thread cups = Thread.ofVirtual().start(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (SocketChannel client = server.accept()) {
                            String head = head(Channels.newInputStream(client));
                            requests.add(head);
                            boolean authorized = head.contains("Authorization: PeerCred alice\r\n");
                            String answer = authorized
                                    ? "HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nipp"
                                    : "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n";
                            Channels.newOutputStream(client).write(answer.getBytes(StandardCharsets.US_ASCII));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            byte[] body = new UnixSocketTransport(socket, "alice").post("/jobs/", new byte[]{1, 2, 3});
            cups.join();
            assertArrayEquals("ipp".getBytes(StandardCharsets.US_ASCII), body);
        }
        assertEquals(2, requests.size());
        assertTrue(requests.get(0).startsWith("POST /jobs/ HTTP/1.1\r\n"));
        assertTrue(requests.get(0).contains("Content-Type: application/ipp\r\n"));
        assertFalse(requests.get(0).contains("Authorization"));
        assertTrue(requests.get(1).contains("Authorization: PeerCred alice\r\n"));
    }

    @Test
    void noSocketMeansCupsIsUnreachable(@TempDir Path dir) {
        IppException e = assertThrows(IppException.class,
                () -> new UnixSocketTransport(dir.resolve("missing.sock"), "alice").post("/", new byte[0]));
        assertTrue(e.unreachable());
    }

    /** The request head and its body (Content-Length bytes), as text. */
    private static String head(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int c = in.read();
            if (c < 0) break;
            head.write(c);
        }
        String text = head.toString(StandardCharsets.US_ASCII);
        int at = text.indexOf("Content-Length: ");
        if (at >= 0) in.readNBytes(Integer.parseInt(text.substring(at + 16, text.indexOf("\r\n", at))));
        return text;
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));
    }
}
