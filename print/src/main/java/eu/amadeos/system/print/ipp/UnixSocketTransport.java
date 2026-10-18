package eu.amadeos.system.print.ipp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP/1.1 over the CUPS domain socket. When CUPS asks for authentication (HTTP 401), the request is sent again
 * with {@code Authorization: PeerCred <user>}, as libcups does: CUPS checks the user against the socket's peer
 * credentials, so the owner can cancel own jobs and members of {@code lpadmin} can manage printers.
 */
public final class UnixSocketTransport implements IppTransport {
    private final Path socket;
    private final String user;

    public UnixSocketTransport(Path socket, String user) {
        this.socket = socket;
        this.user = user;
    }

    @Override
    public byte[] post(String resource, byte[] request) throws IppException {
        Response response = exchange(resource, request, false);
        if (response.status() == 401) response = exchange(resource, request, true);
        if (response.status() != 200) {
            throw new IppException("CUPS answered HTTP " + response.status(), response.status());
        }
        return response.body();
    }

    record Response(int status, byte[] body) {
    }

    private Response exchange(String resource, byte[] request, boolean peerCred) throws IppException {
        SocketChannel channel;
        try {
            channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
        } catch (IOException e) {
            throw new IppException("Cannot connect to " + socket + ": " + e.getMessage(), IppException.UNREACHABLE, e);
        }
        try (channel) {
            StringBuilder head = new StringBuilder()
                    .append("POST ").append(resource).append(" HTTP/1.1\r\n")
                    .append("Host: localhost\r\n")
                    .append("Content-Type: application/ipp\r\n")
                    .append("Content-Length: ").append(request.length).append("\r\n")
                    .append("Connection: close\r\n");
            if (peerCred) head.append("Authorization: PeerCred ").append(user).append("\r\n");
            head.append("\r\n");
            var out = Channels.newOutputStream(channel);
            out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
            out.write(request);
            out.flush();
            return read(Channels.newInputStream(channel));
        } catch (IOException e) {
            throw new IppException("CUPS did not answer: " + e.getMessage(), IppException.UNREACHABLE, e);
        }
    }

    /** Reads status line, headers and the body (Content-Length, chunked, or up to the end of the connection). */
    static Response read(InputStream in) throws IOException {
        String statusLine = line(in);
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) throw new IOException("Not HTTP: " + statusLine);
        int status = Integer.parseInt(parts[1]);
        Map<String, String> headers = new LinkedHashMap<>();
        for (String header = line(in); !header.isEmpty(); header = line(in)) {
            int colon = header.indexOf(':');
            if (colon > 0) headers.put(header.substring(0, colon).trim().toLowerCase(Locale.ROOT), header.substring(colon + 1).trim());
        }
        // CUPS sends "100 Continue" before the real answer when it wants the body; skip it.
        if (status == 100) return read(in);
        byte[] body;
        if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            for (int size = Integer.parseInt(line(in).split(";")[0].trim(), 16); size > 0;
                 size = Integer.parseInt(line(in).split(";")[0].trim(), 16)) {
                all.write(in.readNBytes(size));
                line(in);
            }
            body = all.toByteArray();
        } else if (headers.containsKey("content-length")) {
            body = in.readNBytes(Integer.parseInt(headers.get("content-length")));
        } else {
            body = in.readAllBytes();
        }
        return new Response(status, body);
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int c = in.read(); c != '\n'; c = in.read()) {
            if (c < 0) {
                if (line.size() == 0) throw new IOException("Connection closed");
                break;
            }
            if (c != '\r') line.write(c);
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }
}
