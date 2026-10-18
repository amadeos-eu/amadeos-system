package eu.amadeos.print.ipp;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** IPP over plain HTTP, for a CUPS without local socket (another host, a container). */
public final class HttpTransport implements IppTransport {
    private final URI server;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HttpTransport(URI server) {
        this.server = server;
    }

    @Override
    public byte[] post(String resource, byte[] request) throws IppException {
        HttpRequest http = HttpRequest.newBuilder(server.resolve(resource))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/ipp")
                .POST(HttpRequest.BodyPublishers.ofByteArray(request))
                .build();
        try {
            HttpResponse<byte[]> response = client.send(http, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IppException("CUPS answered HTTP " + response.statusCode(), response.statusCode());
            }
            return response.body();
        } catch (ConnectException e) {
            throw new IppException("Cannot connect to " + server, IppException.UNREACHABLE, e);
        } catch (IOException e) {
            throw new IppException("CUPS did not answer: " + e.getMessage(), IppException.UNREACHABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IppException("Interrupted", IppException.UNREACHABLE, e);
        }
    }
}
