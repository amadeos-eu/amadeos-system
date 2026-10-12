package org.datazuul.amadeos.dm.session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Line protocol between the greeter and the root session worker.
 *
 * <p>The greeter writes three lines to the worker's stdin: user name, password and session
 * type id. The worker answers with one line on stdout, {@code OK} once the user is
 * authenticated and the session is about to start, or {@code FAIL <message>}.
 */
public final class WorkerProtocol {
    static final int MAX_LINE_BYTES = 4096;
    public static final String OK = "OK";
    public static final String FAIL = "FAIL";

    private WorkerProtocol() {
    }

    public record Request(String username, String password, SessionType sessionType) {
    }

    public static void writeRequest(OutputStream out, Request request) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String value : new String[]{request.username(), request.password(), request.sessionType().id()}) {
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
                throw new IOException("Credentials must not contain line breaks or NUL characters");
            }
            text.append(value).append('\n');
        }
        out.write(text.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static Request readRequest(InputStream in) throws IOException {
        String username = readLine(in);
        String password = readLine(in);
        String session = readLine(in);
        if (username == null || password == null) {
            throw new IOException("Incomplete login request");
        }
        return new Request(username, password, SessionType.from(session == null ? "" : session));
    }

    public static void writeResult(OutputStream out, boolean ok, String message) throws IOException {
        String line = ok ? OK : FAIL + " " + (message == null ? "Login failed" : message.replace('\n', ' '));
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** @return null if the worker started the session, otherwise the failure message */
    public static String parseResult(String line) {
        if (line == null) {
            return "The session worker ended without a result";
        }
        if (line.equals(OK)) {
            return null;
        }
        if (line.startsWith(FAIL)) {
            String message = line.substring(FAIL.length()).trim();
            return message.isEmpty() ? "Login failed" : message;
        }
        return "Unexpected answer from the session worker";
    }

    /** Reads one UTF-8 line without buffering past it, so no stdin bytes are consumed early. */
    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }
            if (line.size() >= MAX_LINE_BYTES) {
                throw new IOException("Request line too long");
            }
            line.write(b);
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
    }
}
