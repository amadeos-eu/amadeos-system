package eu.amadeos.system.print.ipp;

import java.io.IOException;

/** CUPS could not be reached, refused the request or answered something unreadable. */
public class IppException extends IOException {
    /** The status when nothing answered. */
    public static final int UNREACHABLE = -1;

    /** The IPP status code, the HTTP status for HTTP errors, {@link #UNREACHABLE}, or 0 for unreadable answers. */
    private final int status;

    public IppException(String message) {
        this(message, 0, null);
    }

    public IppException(String message, int status) {
        this(message, status, null);
    }

    public IppException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** IPP forbidden / not-authenticated / not-authorized, or HTTP 401 / 403: the user may not do this. */
    public boolean notAllowed() {
        return status == 0x0401 || status == 0x0402 || status == 0x0403 || status == 401 || status == 403;
    }

    /** Nothing answered: CUPS is not installed or not running. */
    public boolean unreachable() {
        return status == UNREACHABLE;
    }
}
