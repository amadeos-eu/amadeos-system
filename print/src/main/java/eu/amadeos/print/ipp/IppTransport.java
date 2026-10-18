package eu.amadeos.print.ipp;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Sends an encoded IPP request to CUPS with HTTP POST and returns the encoded response. */
public interface IppTransport {
    byte[] post(String resource, byte[] request) throws IppException;

    /** The usual place of the CUPS socket on Debian. */
    Path CUPS_SOCKET = Path.of("/run/cups/cups.sock");

    /**
     * The local CUPS: its socket if there is one, so that CUPS knows the user without a password, otherwise
     * http://localhost:631.
     */
    static IppTransport local(String user) {
        return Files.exists(CUPS_SOCKET) ? new UnixSocketTransport(CUPS_SOCKET, user)
                : new HttpTransport(URI.create("http://localhost:631"));
    }
}
